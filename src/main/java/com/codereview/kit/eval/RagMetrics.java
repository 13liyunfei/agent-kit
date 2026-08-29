package com.codereview.kit.eval;

import com.codereview.kit.ChatModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * RAG 评测指标：上下文命中（precision/recall/F1）+ 答案质量（faithfulness / relevance）。
 *
 * <p>上下文指标为确定性计算（对照 ground-truth 相关文档 id）；
 * 答案指标用 LLM-as-judge（JSON verdict 0/1），模型失败时降级为词汇包含的启发式。
 */
public final class RagMetrics {

    private RagMetrics() {
    }

    /** 单条 RAG 评测结果。 */
    public record RagEvalResult(double contextPrecision, double contextRecall, double contextF1,
                                double faithfulness, double relevance) {

        public double average() {
            return (contextPrecision + contextRecall + contextF1 + faithfulness + relevance) / 5.0;
        }
    }

    /** 上下文命中：检索结果与相关文档的重合度（确定性）。 */
    public static double contextPrecision(List<String> retrieved, Set<String> relevant) {
        if (retrieved == null || retrieved.isEmpty()) {
            return 0;
        }
        long hit = retrieved.stream().filter(relevant::contains).count();
        return hit / (double) retrieved.size();
    }

    public static double contextRecall(List<String> retrieved, Set<String> relevant) {
        if (relevant == null || relevant.isEmpty()) {
            return retrieved != null && !retrieved.isEmpty() ? 1 : 0;
        }
        long hit = relevant.stream().filter(id -> retrieved != null && retrieved.contains(id)).count();
        return hit / (double) relevant.size();
    }

    public static double contextF1(List<String> retrieved, Set<String> relevant) {
        double p = contextPrecision(retrieved, relevant);
        double r = contextRecall(retrieved, relevant);
        return p + r == 0 ? 0 : 2 * p * r / (p + r);
    }

    /**
     * 综合评测一条 RAG 用例。
     *
     * @param model      judge 模型（可空，为空则答案指标走启发式）
     * @param question   用户问题
     * @param contexts   检索命中文档 id
     * @param relevant   ground-truth 相关文档 id
     * @param answer     模型答案
     */
    public static RagEvalResult evaluate(ChatModel model, String question,
                                         List<String> contexts, Set<String> relevant, String answer) {
        double cp = contextPrecision(contexts, relevant);
        double cr = contextRecall(contexts, relevant);
        double cf = contextF1(contexts, relevant);
        double faith = model == null ? lexicalFaithfulness(answer, contexts) : llmScore(model, question, answer,
                "请判断答案是否严格基于给定上下文（不编造）。只输出 {\"score\":0} 或 {\"score\":1}。\n上下文：" + contexts);
        double rel = model == null ? lexicalRelevance(answer, question) : llmScore(model, question, answer,
                "请判断答案是否切题回答了问题。只输出 {\"score\":0} 或 {\"score\":1}。");
        return new RagEvalResult(cp, cr, cf, faith, rel);
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static double llmScore(ChatModel model, String question, String answer, String instruction) {
        try {
            String out = model.chat(instruction + "\n问题：" + question + "\n答案：" + answer);
            int s = out.indexOf('{');
            int e = out.lastIndexOf('}');
            if (s >= 0 && e > s) {
                JsonNode node = MAPPER.readTree(out.substring(s, e + 1));
                return node.path("score").asDouble(-1);
            }
        } catch (Exception ignored) {
            // judge 失败降级启发式
        }
        return lexicalFaithfulness(answer, List.of(question));
    }

    private static double lexicalFaithfulness(String answer, List<String> contexts) {
        if (answer == null || answer.isBlank()) {
            return 0;
        }
        if (contexts == null || contexts.isEmpty()) {
            return 0;
        }
        Set<String> tokens = tokenize(answer);
        if (tokens.isEmpty()) {
            return 0;
        }
        String corpus = contexts.stream().map(String::valueOf).collect(Collectors.joining(" ")).toLowerCase();
        long hit = tokens.stream().filter(t -> corpus.contains(t)).count();
        return hit / (double) tokens.size();
    }

    private static double lexicalRelevance(String answer, String question) {
        if (answer == null || answer.isBlank() || question == null || question.isBlank()) {
            return 0;
        }
        Set<String> q = tokenize(question);
        if (q.isEmpty()) {
            return 0;
        }
        String a = answer.toLowerCase();
        long hit = q.stream().filter(a::contains).count();
        return hit / (double) q.size();
    }

    private static Set<String> tokenize(String text) {
        return java.util.Arrays.stream(text.toLowerCase().split("[^\\p{IsAlphabetic}\\p{IsHan}]+"))
                .filter(t -> t.length() > 1)
                .collect(Collectors.toSet());
    }

    /** 便捷：批量评测聚合。 */
    public static RagEvalResult aggregate(List<RagEvalResult> results) {
        if (results.isEmpty()) {
            return new RagEvalResult(0, 0, 0, 0, 0);
        }
        double cp = 0, cr = 0, cf = 0, fa = 0, re = 0;
        for (RagEvalResult r : results) {
            cp += r.contextPrecision();
            cr += r.contextRecall();
            cf += r.contextF1();
            fa += r.faithfulness();
            re += r.relevance();
        }
        int n = results.size();
        return new RagEvalResult(cp / n, cr / n, cf / n, fa / n, re / n);
    }
}
