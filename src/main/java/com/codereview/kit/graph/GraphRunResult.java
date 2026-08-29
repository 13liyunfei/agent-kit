package com.codereview.kit.graph;

import java.util.List;
import java.util.Map;

/**
 * 图执行结果。
 *
 * @param finalState  最终状态（含全部节点写入）
 * @param executed    实际执行的节点序列（含重复执行）
 * @param nodeResults 每节点最近一次结果（ok / fail / skipped）
 * @param interrupted 是否因 HITL 拒绝而中断
 * @param reason      中断 / 停止原因
 */
public record GraphRunResult(GraphState finalState, List<String> executed,
                             Map<String, String> nodeResults, boolean interrupted, String reason) {

    public String nodeResult(String name) {
        return nodeResults.getOrDefault(name, "skipped");
    }
}
