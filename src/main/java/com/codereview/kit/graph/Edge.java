package com.codereview.kit.graph;

import java.util.function.Predicate;

/**
 * 图的有向边（可带条件——条件不满足则该边不激活）。
 *
 * @param from      源节点
 * @param to        目标节点
 * @param condition 条件（可空 = 恒真）
 */
public record Edge(String from, String to, Predicate<GraphState> condition) {

    public static Edge always(String from, String to) {
        return new Edge(from, to, null);
    }

    public boolean matches(GraphState state) {
        return condition == null || condition.test(state);
    }
}
