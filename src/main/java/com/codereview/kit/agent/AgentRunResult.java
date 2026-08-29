package com.codereview.kit.agent;

import java.util.List;

/**
 * 一次多 Agent 协作运行的最终结果。
 *
 * @param finalAgent  产出最终结论的 Agent
 * @param answer      最终结论
 * @param trajectory  协作轨迹（agent: task 序列，审计用）
 */
public record AgentRunResult(String finalAgent, String answer, List<String> trajectory) {
}
