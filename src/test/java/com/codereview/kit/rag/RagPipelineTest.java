package com.codereview.kit.rag;

import com.codereview.kit.extension.ExtensionRegistry;
import com.codereview.kit.extension.spi.RagEnhancer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAG 流水线：切分 / 向量化 / 检索 / 增强链 / RagChatModel 注入。
 */
class RagPipelineTest {

    /** 确定性 fake embedding：基于字符特征的稀疏向量（同主题词向量更近）。 */
    static class CharEmbedding implements EmbeddingModel {
        @Override public List<Float> embed(String text) {
            Map<String, Double> dims = new HashMap<>();
            for (String tok : text.toLowerCase().split("[^a-z0-9\\u4e00-\\u9fa5]+")) {
                dims.merge(tok, 1.0, Double::sum);
            }
            List<Float> v = new ArrayList<>();
            for (String k : List.of("java", "agent", "mcp", "rag", "memory", "工具", "记忆")) {
                v.add(dims.getOrDefault(k, 0.0).floatValue());
            }
            return v;
        }

        @Override public List<List<Float>> embedAll(List<String> texts) {
            return texts.stream().map(this::embed).toList();
        }
    }

    private RagPipeline pipeline(ExtensionRegistry registry) {
        return new RagPipeline(new CharEmbedding(), new InMemoryVectorStore(),
                new TextSplitter(200, 20), registry);
    }

    @Test
    void ingestRetrieveAndEnhance() {
        RagPipeline rag = pipeline(new ExtensionRegistry());
        int n = rag.ingest(List.of(
                new Document("d1", "Java agent 框架支持 MCP 工具接入。", Map.of("source", "wiki")),
                new Document("d2", "记忆机制负责长期记忆与上下文管理。", Map.of("source", "wiki"))));
        assertEquals(2, n);

        List<Document> hits = rag.retrieve("Java agent MCP", 1);
        assertEquals(1, hits.size());
        assertTrue(hits.get(0).id().startsWith("d1"));
    }

    @Test
    void ragEnhancerChainApplies() {
        ExtensionRegistry registry = new ExtensionRegistry();
        registry.register(RagEnhancer.class, new RagEnhancer<Document>() {
            @Override public List<Document> enhance(List<Document> hits, String query) {
                return hits.stream().filter(d -> d.metadata().getOrDefault("source", "").equals("kb")).toList();
            }

            @Override public String name() { return "test.kb-only"; }
            @Override public int order() { return 10; }
        });
        RagPipeline rag = pipeline(registry);
        rag.ingest(List.of(
                new Document("kb1", "Java agent 框架支持 MCP 工具接入。", Map.of("source", "kb")),
                new Document("w1", "Java agent 框架也出现在 wiki。", Map.of("source", "wiki"))));
        List<Document> hits = rag.retrieve("Java agent 框架", 5);
        assertTrue(hits.stream().allMatch(d -> d.metadata().get("source").equals("kb")));
    }

    @Test
    void ragChatModelInjectsContext() {
        RagPipeline rag = pipeline(new ExtensionRegistry());
        rag.ingest(List.of(new Document("d1", "MCP 是模型上下文协议，2026 年成为工具互操作事实标准。")));
        StringBuilder seen = new StringBuilder();
        RagChatModel model = new RagChatModel(prompt -> {
            seen.append(prompt);
            return "基于知识的回答";
        }, new com.codereview.kit.rag.Retriever(new CharEmbedding(), rag.store(), new ExtensionRegistry()), 2, "请基于知识回答。");

        String answer = model.chat("MCP 是什么？");
        assertEquals("基于知识的回答", answer);
        assertTrue(seen.toString().contains("模型上下文协议"), "RagChatModel 应注入检索到的知识");
    }
}
