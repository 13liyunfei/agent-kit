package com.codereview.kit.graph;

import com.codereview.kit.checkpoint.CheckpointStore;
import com.codereview.kit.obs.GenAiTracer;

/**
 * 图执行选项。
 *
 * @param maxSteps       全局执行步数上限（防无限循环，默认 100）
 * @param maxNodeRuns    单节点最多执行次数（循环回边场景防死循环，默认 3）
 * @param checkpointStore 非空则每节点完成后落检查点（断点续跑）
 * @param runId          检查点 runId（续跑时复用）
 * @param tracer         非空则记录节点 span
 * @param approvalTimeoutMs HITL 审批等待超时（默认 5 分钟）
 */
public record GraphExecutorOptions(int maxSteps, int maxNodeRuns, CheckpointStore checkpointStore,
                                   String runId, GenAiTracer tracer, long approvalTimeoutMs) {

    public static GraphExecutorOptions defaults() {
        return new GraphExecutorOptions(100, 3, null, null, null, 300_000);
    }

    public GraphExecutorOptions {
        maxSteps = maxSteps <= 0 ? 100 : maxSteps;
        maxNodeRuns = maxNodeRuns <= 0 ? 3 : maxNodeRuns;
        approvalTimeoutMs = approvalTimeoutMs <= 0 ? 300_000 : approvalTimeoutMs;
    }
}
