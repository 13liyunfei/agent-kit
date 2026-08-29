package com.codereview.kit.rag;

import java.util.Map;

/**
 * 检索文档（RAG 的基本单元）。
 *
 * @param id       文档标识（向量库索引键）
 * @param text     正文
 * @param metadata 元数据（来源 / 标题 / 时间等，随检索结果返回）
 */
public record Document(String id, String text, Map<String, String> metadata) {

    public Document {
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Document(String id, String text) {
        this(id, text, Map.of());
    }
}
