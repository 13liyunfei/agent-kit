package com.codereview.kit.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存向量库：余弦相似度暴力检索（小规模知识库 / 测试用）。
 */
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    private record Entry(List<Float> vector, Map<String, String> metadata) {
    }

    @Override
    public void index(String id, List<Float> vector, Map<String, String> metadata) {
        entries.put(id, new Entry(List.copyOf(vector), metadata == null ? Map.of() : Map.copyOf(metadata)));
    }

    @Override
    public List<Hit> search(List<Float> query, int topK) {
        List<Hit> hits = new ArrayList<>();
        for (Map.Entry<String, Entry> e : entries.entrySet()) {
            double sim = cosine(query, e.getValue().vector());
            if (sim > 0) {
                hits.add(new Hit(e.getKey(), sim, e.getValue().metadata()));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.subList(0, Math.min(topK, hits.size()));
    }

    @Override
    public void delete(String id) {
        entries.remove(id);
    }

    @Override
    public int size() {
        return entries.size();
    }

    private static double cosine(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.isEmpty() || a.size() != b.size()) {
            return 0;
        }
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i), y = b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
