package com.codereview.kit.mcp;

/**
 * MCP 资源元数据（resources/list 条目）。
 *
 * @param uri         资源 URI
 * @param name        资源名
 * @param description 描述（可空）
 * @param mimeType    MIME 类型（可空）
 */
public record McpResource(String uri, String name, String description, String mimeType) {
}
