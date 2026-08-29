package com.codereview.kit.toolcalling;
import com.codereview.kit.model.ToolSchema;

import com.codereview.kit.model.NativeChatModel;
import com.codereview.kit.model.NativeMessage;
import com.codereview.kit.model.NativeOptions;
import com.codereview.kit.model.NativeResult;
import com.codereview.kit.model.NativeToolCall;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 原生函数调用决策循环（并行工具调用 + 工具结果 role 消息）。
 *
 * <p>与 {@link ToolCallingLoop}（prompt-JSON 模式）互补：本类走供应商原生
 * {@code tools} 参数协议，一次可返回多个工具调用并**并行执行**，工具结果以
 * role=tool 消息回填，可靠性远高于文本决策。模型不支持原生时用
 * {@link #runWithFallback} 自动降级。
 */
public class NativeToolCallingLoop {

    private static final Logger log = LoggerFactory.getLogger(NativeToolCallingLoop.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final NativeChatModel model;
    private final ToolRegistry registry;
    private final int maxIterations;
    private final ExecutorService executor;

    public NativeToolCallingLoop(NativeChatModel model, ToolRegistry registry, int maxIterations) {
        this(model, registry, maxIterations,
                Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors())));
    }

    public NativeToolCallingLoop(NativeChatModel model, ToolRegistry registry, int maxIterations,
                                 ExecutorService executor) {
        this.model = model;
        this.registry = registry;
        this.maxIterations = Math.max(1, maxIterations);
        this.executor = executor;
    }

    /** 一次原生工具循环结果。 */
    public record NativeLoopResult(String answer, List<String> toolCalls, int iterations,
                                   Integer inputTokens, Integer outputTokens, Double cost) {
    }

    public NativeLoopResult run(String goal, String context) {
        List<NativeMessage> messages = new ArrayList<>();
        messages.add(NativeMessage.system(buildSystemPrompt(goal, context)));
        messages.add(NativeMessage.user(goal));

        List<ToolSchema> tools = registry.list().stream().map(ToolSchema::from).toList();
        List<String> executed = new ArrayList<>();
        long totalIn = 0, totalOut = 0;
        double totalCost = 0;

        for (int i = 1; i <= maxIterations; i++) {
            NativeResult r = model.chat(messages, tools, NativeOptions.defaults());
            totalIn += r.inputTokens() == null ? 0 : r.inputTokens();
            totalOut += r.outputTokens() == null ? 0 : r.outputTokens();
            totalCost += r.cost() == null ? 0 : r.cost();

            if (!r.wantsToolCall()) {
                String answer = r.content() == null ? "" : r.content();
                if (answer.isBlank()) {
                    answer = "(模型未给出文本结论)";
                }
                log.info("[NativeLoop] 完成，共 {} 轮，工具调用 {} 次", i, executed.size());
                return new NativeLoopResult(answer, List.copyOf(executed), i,
                        (int) totalIn, (int) totalOut, totalCost);
            }

            // 并行执行本轮的多个工具调用
            List<Map<String, Object>> executedCalls = executeInParallel(r.toolCalls(), executed);
            messages.add(NativeMessage.assistant(r.content(), r.toolCalls()));
            for (Map<String, Object> ec : executedCalls) {
                messages.add(NativeMessage.tool((String) ec.get("toolCallId"), (String) ec.get("output")));
            }
        }
        log.warn("[NativeLoop] 达到最大迭代 {}，返回兜底结论", maxIterations);
        return new NativeLoopResult("已达最大迭代次数（" + maxIterations + "）",
                List.copyOf(executed), maxIterations, (int) totalIn, (int) totalOut, totalCost);
    }

    /** 并行执行一批工具调用（异常隔离：单工具失败不影响其余）。 */
    private List<Map<String, Object>> executeInParallel(List<NativeToolCall> calls, List<String> executed) {
        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        for (NativeToolCall call : calls) {
            futures.add(executor.submit(() -> runTool(call, executed)));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Future<Map<String, Object>> f : futures) {
            try {
                out.add(f.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                out.add(Map.of("toolCallId", "", "output", "工具执行被中断"));
            } catch (ExecutionException e) {
                out.add(Map.of("toolCallId", "", "output", "工具执行异常: " + e.getCause()));
            }
        }
        return out;
    }

    private Map<String, Object> runTool(NativeToolCall call, List<String> executed) {
        AgentTool tool = registry.get(call.name());
        String output;
        if (tool == null) {
            output = "错误：工具 " + call.name() + " 不存在，可用工具见清单";
        } else {
            executed.add(call.name());
            try {
                AgentTool.ToolResult tr = tool.execute(parseArgs(call.argumentsJson()));
                output = (tr.success() ? "[观察] " : "[工具错误] ") + truncate(tr.output());
            } catch (Exception e) {
                log.warn("[NativeLoop] 工具 {} 执行异常：{}", call.name(), e.getMessage());
                output = "工具执行异常: " + e.getMessage();
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("toolCallId", call.id());
        m.put("output", output);
        return m;
    }

    /** 回退模式：模型不支持原生调用时退化为 prompt-JSON 决策。 */
    public static ToolCallingLoop.LoopResult runWithFallback(NativeChatModel nativeModel, ToolRegistry registry,
                                                             String goal, String context, int maxIterations) {
        return new ToolCallingLoop(new com.codereview.kit.ChatModel() {
            @Override
            public String chat(String prompt) {
                return nativeModel.chat(List.of(NativeMessage.system(
                                "你是具备工具调用能力的审查助手。按给定 JSON 格式决策。")),
                        List.of(), NativeOptions.defaults()).content();
            }
        }, registry, maxIterations).run(goal, context);
    }

    private static Map<String, Object> parseArgs(String json) {
        try {
            JsonNode node = MAPPER.readTree(json);
            Map<String, Object> args = new LinkedHashMap<>();
            if (node.isObject()) {
                node.properties().forEach(e -> args.put(e.getKey(),
                        e.getValue().isTextual() ? e.getValue().asText() : e.getValue().toString()));
            }
            return args;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String buildSystemPrompt(String goal, String context) {
        StringBuilder sb = new StringBuilder("你是有工具调用能力的 Agent。");
        if (context != null && !context.isBlank()) {
            sb.append("\n背景：").append(context);
        }
        return sb.toString();
    }

    private static String truncate(String s) {
        return s == null ? "" : s.length() > 2000 ? s.substring(0, 2000) + "…(截断)" : s;
    }
}
