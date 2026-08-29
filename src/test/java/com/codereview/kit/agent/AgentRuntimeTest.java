package com.codereview.kit.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多 Agent 协作：Handoff 链 / 未知目标 / 最大转交守卫。
 */
class AgentRuntimeTest {

    @Test
    void handoffChainReachesFinalAnswer() {
        AgentRuntime runtime = new AgentRuntime(
                new Agent("reviewer", "审查员", ctx -> new AgentOutput.Handoff("reporter", "整理审查结果")),
                new Agent("reporter", "报告员", ctx -> new AgentOutput.FinalAnswer("审查报告完成")));
        AgentRunResult r = runtime.start("reviewer", "审查 PR");
        assertEquals("reporter", r.finalAgent());
        assertEquals("审查报告完成", r.answer());
        assertEquals(2, r.trajectory().size());
        assertTrue(r.trajectory().get(0).startsWith("reviewer"));
    }

    @Test
    void unknownTargetReturnsError() {
        AgentRuntime runtime = new AgentRuntime(
                new Agent("a", "A", ctx -> new AgentOutput.Handoff("ghost", "任务")));
        AgentRunResult r = runtime.start("a", "t");
        assertTrue(r.answer().contains("不存在"));
    }

    @Test
    void maxHopsGuarded() {
        AtomicInteger n = new AtomicInteger();
        AgentRuntime runtime = new AgentRuntime(3,
                new Agent("a", "A", ctx -> new AgentOutput.Handoff("a", "再来一次" + n.incrementAndGet())));
        AgentRunResult r = runtime.start("a", "t");
        assertTrue(r.answer().contains("最大转交次数"));
    }

    @Test
    void supervisorRoutesToWorkers() {
        com.codereview.kit.ChatModel model = prompt -> {
            if (prompt.contains("已执行轨迹")) {
                return "{\"done\":true,\"answer\":\"登录超时已修复\"}";
            }
            return "{\"worker\":\"fixer\",\"task\":\"修复登录超时\",\"done\":false}";
        };
        List<String> ran = new java.util.ArrayList<>();
        SupervisorAgent supervisor = new SupervisorAgent(model);
        String result = supervisor.run("修复登录超时 bug",
                List.of(new SupervisorAgent.Worker("fixer", "修复 bug")),
                worker -> {
                    ran.add(worker);
                    return "已定位并修复";
                }, 5);
        assertEquals("登录超时已修复", result);
        assertEquals(1, ran.size());
        assertTrue(ran.get(0).contains("fixer"));
    }
}
