package com.codereview.kit.model;

import java.util.List;

/**
 * 一次原生对话调用的结果（含工具调用与用量）。
 *
 * @param content      模型文本回复（可为空，若只有工具调用）
 * @param toolCalls    模型请求的工具调用（空表示本轮无需工具）
 * @param inputTokens  输入 token（可空）
 * @param outputTokens 输出 token（可空）
 * @param cost         估算成本（可空）
 * @param raw          原始响应（调试用）
 */
public record NativeResult(String content, List<NativeToolCall> toolCalls,
                           Integer inputTokens, Integer outputTokens, Double cost, String raw) {

    public boolean wantsToolCall() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}
