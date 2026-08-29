package com.codereview.kit.graph;

import com.codereview.kit.checkpoint.Checkpoint;
import com.codereview.kit.checkpoint.CheckpointStore;
import com.codereview.kit.hitl.ApprovalRequest;
import com.codereview.kit.obs.GenAiSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 状态化 Agent 编排图。
 *
 * <p>能力：多起点 / 有向边 / 条件边（分支）/ 循环回边（带步数与单节点次数上限）/
 * 节点级重试 / HITL 审批中断 / 检查点断点续跑 / span 记录。
 *
 * <p>执行语义：从起点 BFS 展开；节点完成后评估其全部出边，条件命中的目标入队；
 * 无出边节点视为分支终点。全部队列清空即运行完成。
 */
public class AgentGraph {

    private static final Logger log = LoggerFactory.getLogger(AgentGraph.class);

    private final Map<String, GraphNode> nodes;
    private final Map<String, List<Edge>> edgesByFrom;
    private final List<String> startNodes;

    AgentGraph(Map<String, GraphNode> nodes, Map<String, List<Edge>> edgesByFrom, List<String> startNodes) {
        this.nodes = nodes;
        this.edgesByFrom = edgesByFrom;
        this.startNodes = startNodes;
    }

    public static AgentGraphBuilder builder() {
        return new AgentGraphBuilder();
    }

    public GraphRunResult execute(GraphState initialState, GraphExecutorOptions options) {
        GraphState state = new GraphState(initialState.toMap());
        CheckpointStore store = options.checkpointStore();
        String runId = options.runId() == null ? "run-" + System.nanoTime() : options.runId();

        // 检查点恢复：本次执行前已完成的节点不再执行（状态保留在 state 中）
        if (store != null) {
            store.load(runId).ifPresent(cp -> cp.state().forEach(state::put));
        }
        Set<String> preCompleted = new java.util.HashSet<>(state.completedNodes());
        if (!preCompleted.isEmpty()) {
            log.info("[Graph] 从检查点恢复，跳过已完成节点 {}", preCompleted);
        }

        Map<String, Integer> runCount = new HashMap<>();
        Map<String, String> nodeResults = new LinkedHashMap<>();
        List<String> executed = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>(startNodes);
        boolean interrupted = false;
        String reason = "完成";

        int steps = 0;
        while (!queue.isEmpty() && steps < options.maxSteps()) {
            String name = queue.poll();
            GraphNode node = nodes.get(name);
            if (node == null) {
                nodeResults.put(name, "fail:节点不存在");
                continue;
            }
            boolean alreadyDone = preCompleted.contains(name); // 检查点恢复节点：跳过动作但继续展开出边
            if (!alreadyDone) {
                int runs = runCount.merge(name, 1, Integer::sum);
                if (runs > options.maxNodeRuns()) {
                    nodeResults.put(name, "fail:超过单节点最大执行次数");
                    continue;
                }
                steps++;

                // HITL 审批中断
                if (node.approvalGate() != null) {
                    String reqId = node.approvalGate().submit(node.approvalTask(), String.valueOf(state.toMap()));
                    try {
                        ApprovalRequest decided = node.approvalGate().await(reqId, options.approvalTimeoutMs());
                        if (decided == null || decided.status() != ApprovalRequest.Status.APPROVED) {
                            interrupted = true;
                            reason = "HITL 审批未通过（" + (decided == null ? "超时" : decided.status()) + "），节点 " + name + " 中断";
                            nodeResults.put(name, "interrupted");
                            break;
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        interrupted = true;
                        reason = "HITL 等待被中断";
                        nodeResults.put(name, "interrupted");
                        break;
                    }
                }

                // 执行（含重试）
                long startMs = System.currentTimeMillis();
                String result = executeWithRetry(node, state);
                long ms = System.currentTimeMillis() - startMs;
                if (options.tracer() != null) {
                    options.tracer().record(new GenAiSpan(Long.toHexString(System.nanoTime()), null,
                            "graph.node." + name, ms, null, null, null));
                }
                nodeResults.put(name, result);
                if (result.startsWith("ok")) {
                    executed.add(name);
                    state.markCompleted(name);
                    checkpoint(store, runId, state);
                } else {
                    reason = "节点 " + name + " 失败：" + result;
                    break;
                }
            } else {
                nodeResults.putIfAbsent(name, "skipped(已从检查点恢复)");
            }

            // 条件边展开
            for (Edge e : edgesByFrom.getOrDefault(name, List.of())) {
                if (e.matches(state) && !queue.contains(e.to())) {
                    queue.add(e.to());
                }
            }
        }
        if (steps >= options.maxSteps() && !queue.isEmpty()) {
            reason = "达到全局步数上限 " + options.maxSteps() + "（存在未收敛循环）";
        }
        return new GraphRunResult(state, List.copyOf(executed), Map.copyOf(nodeResults), interrupted, reason);
    }

    private String executeWithRetry(GraphNode node, GraphState state) {
        int attempts = 0;
        Exception last = null;
        while (attempts < node.maxAttempts()) {
            attempts++;
            try {
                GraphState next = node.action().apply(state);
                if (next != null) {
                    state.merge(next);
                }
                return "ok";
            } catch (Exception e) {
                last = e;
                log.warn("[Graph] 节点 {} 第 {} 次执行异常：{}", node.name(), attempts, e.getMessage());
            }
        }
        return "fail:" + (last == null ? "未知错误" : last.getMessage());
    }

    private static void checkpoint(CheckpointStore store, String runId, GraphState state) {
        if (store != null) {
            try {
                store.save(new Checkpoint(runId, state.toMap(), Instant.now()));
            } catch (Exception e) {
                log.warn("[Graph] 检查点保存失败（忽略）：{}", e.getMessage());
            }
        }
    }
}
