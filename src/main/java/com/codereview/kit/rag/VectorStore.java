package com.codereview.kit.rag;

import java.util.List;
import java.util.Map;

/**
 * 向量库抽象（检索存储边界）。
 *
 * <p>实现可选择内存 / 磁盘 / 外部向量数据库（Milvus / ES / Redis 等）。
 */
public interface VectorStore {

    /** 单条索引。 */
    void index(String id, List<Float> vector, Map<String, String> metadata);

    /** 按查询向量取 topK 命中（score 越大越相关，实现需归一化语义）。 */
    List<Hit> search(List<Float> query, int topK);

    /** 按 id 删除。 */
    void delete(String id);

    int size();

    /** 检索命中。 */
    record Hit(String id, double score, Map<String, String> metadata) {
    }
}
