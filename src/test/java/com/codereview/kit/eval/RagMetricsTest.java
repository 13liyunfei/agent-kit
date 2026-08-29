package com.codereview.kit.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RAG 评测：上下文命中确定性指标 + 无 judge 模型时的启发式答案指标。
 */
class RagMetricsTest {

    @Test
    void contextMetricsDeterministic() {
        List<String> retrieved = List.of("d1", "d2", "d3");
        Set<String> relevant = Set.of("d1", "d3", "d4");
        assertEquals(2.0 / 3.0, RagMetrics.contextPrecision(retrieved, relevant), 1e-9);
        assertEquals(2.0 / 3.0, RagMetrics.contextRecall(retrieved, relevant), 1e-9);
        assertEquals(2.0 / 3.0, RagMetrics.contextF1(retrieved, relevant), 1e-9);
    }

    @Test
    void emptyRelevantMeansPerfectRecallNoPrecision() {
        assertEquals(1.0, RagMetrics.contextRecall(List.of("x"), Set.of()), 1e-9);
        assertEquals(0.0, RagMetrics.contextPrecision(List.of(), Set.of("d1")), 1e-9);
    }

    @Test
    void heuristicFaithfulnessHigherWhenAnswerUsesContext() {
        String context = "Java agent 框架支持 MCP 工具接入与记忆机制。";
        var grounded = RagMetrics.evaluate(null, "Java agent 支持什么？",
                List.of(context), Set.of("d1"), "MCP 工具接入与记忆机制");
        var hallucinated = RagMetrics.evaluate(null, "Java agent 支持什么？",
                List.of(context), Set.of("d1"), "可以发射火箭去火星");
        assertTrue(grounded.faithfulness() > hallucinated.faithfulness(),
                "基于上下文的答案 faithfulness 应更高");
    }

    @Test
    void aggregateAverages() {
        var a = new RagMetrics.RagEvalResult(1, 1, 1, 1, 1);
        var b = new RagMetrics.RagEvalResult(0, 0, 0, 0, 0);
        var avg = RagMetrics.aggregate(List.of(a, b));
        assertEquals(0.5, avg.average(), 1e-9);
    }
}
