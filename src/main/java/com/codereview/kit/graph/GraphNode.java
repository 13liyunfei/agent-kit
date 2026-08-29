package com.codereview.kit.graph;

import com.codereview.kit.hitl.ApprovalGate;

import java.util.function.Function;

/**
 * 图节点：一段可执行动作 + 重试 + 可选 HITL 审批中断。
 *
 * @param name          节点名（图内唯一）
 * @param action        节点动作（入状态、出状态）
 * @param maxAttempts   失败重试次数（>=1）
 * @param approvalGate  非空则该节点执行前需人工审批
 * @param approvalTask  审批描述（展示给人看）
 */
public record GraphNode(String name, Function<GraphState, GraphState> action, int maxAttempts,
                        ApprovalGate approvalGate, String approvalTask) {

    public static GraphNode of(String name, Function<GraphState, GraphState> action) {
        return new GraphNode(name, action, 1, null, null);
    }

    public static GraphNode withRetry(String name, Function<GraphState, GraphState> action, int maxAttempts) {
        return new GraphNode(name, action, Math.max(1, maxAttempts), null, null);
    }

    public static GraphNode withApproval(String name, Function<GraphState, GraphState> action,
                                         ApprovalGate gate, String task) {
        return new GraphNode(name, action, 1, gate, task);
    }

    public GraphNode {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("节点名不能为空");
        }
        maxAttempts = Math.max(1, maxAttempts);
    }
}
