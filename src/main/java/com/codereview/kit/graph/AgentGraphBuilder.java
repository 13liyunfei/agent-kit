package com.codereview.kit.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * AgentGraph 构建器（流式 API）。
 *
 * <pre>
 * AgentGraph graph = AgentGraph.builder()
 *         .addNode("plan", s -> s.put("plan", "..." ))
 *         .addNode("act", ...)
 *         .addEdge("plan", "act")
 *         .addConditionalEdge("act", "done", s -> s.getBoolean("done"))
 *         .start("plan")
 *         .build();
 * </pre>
 */
public class AgentGraphBuilder {

    private final Map<String, GraphNode> nodes = new LinkedHashMap<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<String> starts = new ArrayList<>();

    public AgentGraphBuilder addNode(String name, Function<GraphState, GraphState> action) {
        return addNode(GraphNode.of(name, action));
    }

    public AgentGraphBuilder addNode(GraphNode node) {
        if (nodes.putIfAbsent(node.name(), node) != null) {
            throw new IllegalArgumentException("节点重复: " + node.name());
        }
        return this;
    }

    public AgentGraphBuilder addEdge(String from, String to) {
        edges.add(Edge.always(from, to));
        return this;
    }

    public AgentGraphBuilder addConditionalEdge(String from, String to, Predicate<GraphState> condition) {
        edges.add(new Edge(from, to, condition));
        return this;
    }

    public AgentGraphBuilder start(String node) {
        starts.add(node);
        return this;
    }

    public AgentGraph build() {
        if (starts.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个起点（start）");
        }
        for (String s : starts) {
            if (!nodes.containsKey(s)) {
                throw new IllegalArgumentException("起点不存在: " + s);
            }
        }
        for (Edge e : edges) {
            if (!nodes.containsKey(e.from()) || !nodes.containsKey(e.to())) {
                throw new IllegalArgumentException("边引用了不存在的节点: " + e.from() + " -> " + e.to());
            }
        }
        Map<String, List<Edge>> byFrom = new LinkedHashMap<>();
        edges.forEach(e -> byFrom.computeIfAbsent(e.from(), k -> new ArrayList<>()).add(e));
        return new AgentGraph(Map.copyOf(nodes), byFrom, List.copyOf(starts));
    }
}
