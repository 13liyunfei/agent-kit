package com.codereview.kit.model;

/**
 * 模型返回的一次工具调用（原生函数调用协议）。
 *
 * @param id            调用 id（工具结果回填 tool_call_id）
 * @param name          工具名
 * @param argumentsJson 参数 JSON 字符串（{"a":1} 形态）
 */
public record NativeToolCall(String id, String name, String argumentsJson) {
}
