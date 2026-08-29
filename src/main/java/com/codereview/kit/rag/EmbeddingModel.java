package com.codereview.kit.rag;

import java.util.List;

/**
 * 文本向量化模型（RAG 检索的嵌入边界）。
 *
 * <p>与 {@code ChatModel} 同理：kit 不绑定供应商，一行适配任意 embedding 服务。
 */
public interface EmbeddingModel {

    /** 单文本向量化。 */
    List<Float> embed(String text);

    /** 批量向量化（适配器可并行 / 合并请求）。 */
    List<List<Float>> embedAll(List<String> texts);
}
