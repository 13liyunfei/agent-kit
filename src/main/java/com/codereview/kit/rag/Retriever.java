package com.codereview.kit.rag;

import com.codereview.kit.extension.ExtensionRegistry;
import com.codereview.kit.extension.spi.RagEnhancer;

import java.util.List;

/**
 * 检索器：查询 → 向量化 → 向量库 topK → RagEnhancer 增强链（重排 / 去重 / 注入知识库）。
 */
public class Retriever {

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final ExtensionRegistry extensions;

    public Retriever(EmbeddingModel embeddingModel, VectorStore vectorStore, ExtensionRegistry extensions) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.extensions = extensions == null ? new ExtensionRegistry() : extensions;
    }

    /** 检索并返回命中文档（含增强链）。 */
    public List<Document> retrieve(String query, int topK) {
        List<Float> qv = embeddingModel.embed(query);
        List<VectorStore.Hit> hits = vectorStore.search(qv, Math.max(1, topK));
        List<Document> docs = hits.stream()
                .map(h -> new Document(h.id(), h.metadata().getOrDefault("text", ""), h.metadata()))
                .toList();
        @SuppressWarnings("unchecked")
        List<RagEnhancer<Document>> chain =
                (List<RagEnhancer<Document>>) (List<?>) extensions.list(RagEnhancer.class);
        for (RagEnhancer<Document> enhancer : chain) {
            docs = enhancer.enhance(docs, query);
        }
        return List.copyOf(docs);
    }

    /** 跳过增强链的原始检索（打分可见）。 */
    public List<VectorStore.Hit> searchRaw(String query, int topK) {
        return vectorStore.search(embeddingModel.embed(query), Math.max(1, topK));
    }
}
