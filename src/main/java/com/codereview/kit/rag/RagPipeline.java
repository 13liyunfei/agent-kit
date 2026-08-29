package com.codereview.kit.rag;

import com.codereview.kit.extension.ExtensionRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 流水线：入库（切分 → 向量化 → 索引）与检索（查询 → 增强）一站式。
 *
 * <pre>
 * RagPipeline rag = new RagPipeline(embeddingModel, new InMemoryVectorStore(),
 *         new TextSplitter(800, 80), extensions);
 * rag.ingest(List.of(new Document("d1", "正文...", Map.of("source", "wiki"))));
 * List&lt;Document&gt; hits = rag.retrieve("问题", 3);
 * </pre>
 */
public class RagPipeline {

    private final EmbeddingModel embeddingModel;
    private final VectorStore vectorStore;
    private final TextSplitter splitter;
    private final Retriever retriever;

    public RagPipeline(EmbeddingModel embeddingModel, VectorStore vectorStore,
                       TextSplitter splitter, ExtensionRegistry extensions) {
        this.embeddingModel = embeddingModel;
        this.vectorStore = vectorStore;
        this.splitter = splitter;
        this.retriever = new Retriever(embeddingModel, vectorStore, extensions);
    }

    /** 文档入库：切分 → 批量向量化 → 索引（幂等：同 id 覆盖）。 */
    public int ingest(List<Document> docs) {
        List<String> chunks = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        List<Map<String, String>> metas = new ArrayList<>();
        for (Document d : docs) {
            List<String> parts = splitter.split(d.text());
            for (int i = 0; i < parts.size(); i++) {
                chunks.add(parts.get(i));
                ids.add(d.id() + "#" + i);
                Map<String, String> m = new LinkedHashMap<>(d.metadata());
                m.put("text", parts.get(i));
                m.put("docId", d.id());
                metas.add(m);
            }
        }
        if (chunks.isEmpty()) {
            return 0;
        }
        List<List<Float>> vectors = embeddingModel.embedAll(chunks);
        for (int i = 0; i < ids.size(); i++) {
            vectorStore.index(ids.get(i), vectors.get(i), metas.get(i));
        }
        return ids.size();
    }

    public List<Document> retrieve(String query, int topK) {
        return retriever.retrieve(query, topK);
    }

    public VectorStore store() {
        return vectorStore;
    }
}
