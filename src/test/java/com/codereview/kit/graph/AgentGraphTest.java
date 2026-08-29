package com.codereview.kit.graph;

import com.codereview.kit.checkpoint.CheckpointStore;
import com.codereview.kit.checkpoint.InMemoryCheckpointStore;
import com.codereview.kit.hitl.ApprovalGate;
import com.codereview.kit.hitl.ApprovalRequest;
import com.codereview.kit.hitl.InMemoryApprovalGate;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 状态化编排图：条件分支 / 循环回边守卫 / 重试 / 检查点恢复 / HITL 中断。
 */
class AgentGraphTest {

    @Test
    void conditionalBranchTaken() {
        AgentGraph graph = AgentGraph.builder()
                .addNode("start", s -> s.put("done", true))
                .addNode("fast", s -> s.put("branch", "fast"))
                .addNode("slow", s -> s.put("branch", "slow"))
                .addConditionalEdge("start", "fast", s -> s.getBoolean("done"))
                .addConditionalEdge("start", "slow", s -> !s.getBoolean("done"))
                .start("start")
                .build();
        GraphRunResult r = graph.execute(GraphState.of(), GraphExecutorOptions.defaults());
        assertTrue(r.executed().contains("start"));
        assertTrue(r.executed().contains("fast"));
        assertFalse(r.executed().contains("slow"));
        assertEquals("fast", r.finalState().getString("branch"));
    }

    @Test
    void loopBackEdgeConvergesWithBudget() {
        AtomicInteger count = new AtomicInteger();
        AgentGraph graph = AgentGraph.builder()
                .addNode("work", s -> {
                    int n = count.incrementAndGet();
                    return n >= 3 ? s.put("converged", true) : s.put("converged", false);
                })
                .addConditionalEdge("work", "work", s -> !s.getBoolean("converged"))
                .start("work")
                .build();
        GraphRunResult r = graph.execute(GraphState.of(),
                new GraphExecutorOptions(100, 3, null, null, null, 1000));
        assertEquals(3, count.get(), "回边循环应到收敛为止");
        assertTrue(r.finalState().getBoolean("converged"));
    }

    @Test
    void nodeRetrySucceedsOnSecondAttempt() {
        AtomicInteger attempts = new AtomicInteger();
        AgentGraph graph = AgentGraph.builder()
                .addNode(GraphNode.withRetry("flaky", s -> {
                    if (attempts.incrementAndGet() < 2) {
                        throw new IllegalStateException("第一次失败");
                    }
                    return s.put("ok", true);
                }, 3))
                .start("flaky")
                .build();
        GraphRunResult r = graph.execute(GraphState.of(), GraphExecutorOptions.defaults());
        assertEquals("ok", r.nodeResult("flaky"));
        assertEquals(2, attempts.get());
    }

    @Test
    void checkpointResumeSkipsCompletedNodes() {
        CheckpointStore store = new InMemoryCheckpointStore();
        GraphExecutorOptions opts = new GraphExecutorOptions(100, 3, store, "run-1", null, 1000);

        // 第一次跑：b 崩溃
        AtomicInteger bRuns = new AtomicInteger();
        AgentGraph graph1 = AgentGraph.builder()
                .addNode("a", s -> s.put("a", 1))
                .addNode("b", s -> {
                    bRuns.incrementAndGet();
                    throw new IllegalStateException("模拟崩溃");
                })
                .addEdge("a", "b")
                .start("a")
                .build();
        GraphRunResult r1 = graph1.execute(GraphState.of(), opts);
        assertTrue(r1.nodeResult("b").startsWith("fail"));

        // 恢复：a 已完成跳过，只重跑 b
        AgentGraph graph2 = AgentGraph.builder()
                .addNode("a", s -> { throw new IllegalStateException("不应重跑 a"); })
                .addNode("b", s -> s.put("b", 2))
                .addEdge("a", "b")
                .start("a")
                .build();
        GraphRunResult r2 = graph2.execute(GraphState.of(), opts);
        assertEquals("ok", r2.nodeResult("b"));
        assertFalse(r2.executed().contains("a"), "恢复后 a 不应重跑");
    }

    @Test
    void hitlRejectionInterruptsExecution() throws Exception {
        ApprovalGate gate = new InMemoryApprovalGate();
        AtomicInteger after = new AtomicInteger();
        AgentGraph graph = AgentGraph.builder()
                .addNode(GraphNode.withApproval("risky", s -> {
                    after.incrementAndGet();
                    return s.put("done", true);
                }, gate, "是否继续危险操作？"))
                .start("risky")
                .build();
        new Thread(() -> {
            try {
                Thread.sleep(100);
                var pending = gate.list(ApprovalRequest.Status.PENDING);
                if (!pending.isEmpty()) {
                    gate.decide(pending.get(0).requestId(), false);
                }
            } catch (Exception ignored) {
            }
        }).start();

        GraphRunResult r = graph.execute(GraphState.of(), new GraphExecutorOptions(100, 3, null, null, null, 5000));
        assertTrue(r.interrupted());
        assertEquals("interrupted", r.nodeResult("risky"));
        assertEquals(0, after.get(), "审批拒绝后节点动作不应执行");
    }
}
