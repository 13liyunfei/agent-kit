package com.codereview.kit.mcp;

/**
 * MCP 提示模板元数据（prompts/list 条目）。
 *
 * @param name        模板名
 * @param description 描述（可空）
 */
public record McpPrompt(String name, String description) {
}
