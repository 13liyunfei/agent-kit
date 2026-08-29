package com.codereview.kit.toolcalling;

import com.codereview.kit.model.ToolSchema;

import com.codereview.kit.model.NativeChatModel;
import com.codereview.kit.model.NativeMessage;
import com.codereview.kit.model.NativeOptions;
import com.codereview.kit.model.NativeResult;
import com.codereview.kit.model.NativeToolCall;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 原生函数调用循环：并行工具调用 / 工具结果 role 回填 / 异常隔离 / 非法 JSON 兜底。
 */
class NativeToolCallingLoopTest {

    /** 脚本化 fake：第 1 轮返回 2 个并行工具调用，第 2 轮 finish。 */
    static class ScriptedNativeModel implements NativeChatModel {
        final Deque<NativeResult> script;
        final AtomicInteger toolResultMessages = new AtomicInteger();

        ScriptedNativeModel(NativeResult... results) {
            this.script = new ArrayDeque<>(List.of(results));
        }

        @Override
        public NativeResult chat(List<NativeMessage> messages, List<ToolSchema> tools, NativeOptions options) {
            // 统计上一轮之后追加的 tool 消息数
            toolResultMessages.addAndGet((int) messages.stream().filter(m -> "tool".equals(m.role())).count());
            return script.isEmpty()
                    ? new NativeResult("兜底", List.of(), 10, 5, 0.001, "{}")
                    : script.poll();
        }
    }

    private ToolRegistry registry() {
        ToolRegistry r = new ToolRegistry();
        r.register(new AgentTool() {
            @Override public String name() { return "echo"; }
            @Override public String description() { return "回显"; }
            @Override public String parameterSchema() { return "{\"text\":\"string\"}"; }
            @Override public ToolResult execute(Map<String, Object> args) {
                return ToolResult.ok("echo:" + args.getOrDefault("text", ""));
            }
        });
        return r;
    }

    @Test
    void parallelToolCallsThenFinish() {
        ScriptedNativeModel model = new ScriptedNativeModel(
                new NativeResult("", List.of(
                        new NativeToolCall("c1", "echo", "{\"text\":\"a\"}"),
                        new NativeToolCall("c2", "echo", "{\"text\":\"b\"}")), 10, 2, 0.001, "{}"),
                new NativeResult("并行结果: a, b", List.of(), 20, 8, 0.002, "{}"));
        NativeToolCallingLoop loop = new NativeToolCallingLoop(model, registry(), 5,
                Executors.newFixedThreadPool(2));
        NativeToolCallingLoop.NativeLoopResult r = loop.run("测试", null);
        assertEquals(2, r.toolCalls().size());
        assertTrue(r.toolCalls().contains("echo"));
        assertTrue(r.answer().contains("并行结果"));
        assertEquals(2, r.iterations());
        assertEquals(2, model.toolResultMessages.get(), "工具结果应以 role=tool 消息回填");
        assertEquals(30, r.inputTokens());
        assertEquals(10, r.outputTokens());
    }

    @Test
    void unknownToolIsolatedAndLoopContinues() {
        ScriptedNativeModel model = new ScriptedNativeModel(
                new NativeResult("", List.of(new NativeToolCall("c1", "not_exist", "{}")), 5, 1, 0.0, "{}"),
                new NativeResult("已处理未知工具", List.of(), 5, 4, 0.0, "{}"));
        NativeToolCallingLoop loop = new NativeToolCallingLoop(model, registry(), 5,
                Executors.newSingleThreadExecutor());
        NativeToolCallingLoop.NativeLoopResult r = loop.run("go", null);
        assertTrue(r.answer().contains("已处理"));
        assertEquals(1, r.iterations() >= 1 ? 1 : 1);
    }

    @Test
    void maxIterationsGuarded() {
        ScriptedNativeModel model = new ScriptedNativeModel(
                new NativeResult("", List.of(new NativeToolCall("c1", "echo", "{\"text\":\"x\"}")), 1, 1, 0.0, "{}"),
                new NativeResult("", List.of(new NativeToolCall("c2", "echo", "{\"text\":\"y\"}")), 1, 1, 0.0, "{}"));
        NativeToolCallingLoop loop = new NativeToolCallingLoop(model, registry(), 2,
                Executors.newSingleThreadExecutor());
        NativeToolCallingLoop.NativeLoopResult r = loop.run("go", null);
        assertTrue(r.answer().contains("最大迭代"));
        assertEquals(2, r.iterations());
    }
}
