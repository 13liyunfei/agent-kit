package com.codereview.kit.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 图执行的共享状态：节点间传递的键值容器。
 *
 * <p>线程安全由调用方保证（图执行默认串行）；提供类型化取值便利。
 */
public class GraphState {

    /** 保留键：已完成节点列表（检查点恢复用），使用者不应覆盖。 */
    public static final String KEY_COMPLETED = "__kit_completed_nodes";

    private final Map<String, Object> data;

    public GraphState() {
        this(new LinkedHashMap<>());
    }

    public GraphState(Map<String, Object> data) {
        this.data = data == null ? new LinkedHashMap<>() : new LinkedHashMap<>(data);
    }

    public static GraphState of() {
        return new GraphState();
    }

    public GraphState put(String key, Object value) {
        data.put(key, value);
        return this;
    }

    public Object get(String key) {
        return data.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T fallback) {
        Object v = data.get(key);
        return v == null ? fallback : (T) v;
    }

    public boolean getBoolean(String key) {
        return Boolean.TRUE.equals(data.get(key));
    }

    public String getString(String key) {
        Object v = data.get(key);
        return v == null ? null : String.valueOf(v);
    }

    /** 并入另一状态（后者覆盖同名键）。 */
    public GraphState merge(GraphState other) {
        other.data.forEach(data::put);
        return this;
    }

    public Map<String, Object> toMap() {
        return Map.copyOf(data);
    }

    @SuppressWarnings("unchecked")
    List<String> completedNodes() {
        Object v = data.get(KEY_COMPLETED);
        return v instanceof List<?> l ? (List<String>) l : List.of();
    }

    void markCompleted(String node) {
        List<String> done = new java.util.ArrayList<>(completedNodes());
        if (!done.contains(node)) {
            done.add(node);
        }
        data.put(KEY_COMPLETED, done);
    }
}
