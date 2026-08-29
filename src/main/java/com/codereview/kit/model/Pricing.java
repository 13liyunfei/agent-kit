package com.codereview.kit.model;

import java.util.List;

/**
 * 模型定价表（每百万 token 美元），用于成本估算。
 *
 * <p>以模型名前缀匹配，未命中的按默认价。价格随供应商变动，使用方可自行覆盖。
 */
public record Pricing(double inputPerMillion, double outputPerMillion) {

    /** 常见模型价目（近似值，仅估算用）。 */
    public static final List<Entry> TABLE = List.of(
            new Entry("gpt-4o", 2.50, 10.00),
            new Entry("gpt-4.1", 2.00, 8.00),
            new Entry("gpt-4", 30.00, 60.00),
            new Entry("gpt-3.5", 0.50, 1.50),
            new Entry("deepseek", 0.27, 1.10),
            new Entry("qwen", 0.80, 2.00),
            new Entry("glm", 0.50, 2.00),
            new Entry("claude", 3.00, 15.00),
            new Entry("gemini", 1.25, 5.00));

    public static final Pricing DEFAULT = new Pricing(2.50, 10.00);

    public record Entry(String prefix, double inputPerMillion, double outputPerMillion) {
    }

    /** 按模型名查价（前缀匹配），未命中用默认。 */
    public static Pricing forModel(String model) {
        if (model == null) {
            return DEFAULT;
        }
        String m = model.toLowerCase();
        for (Entry e : TABLE) {
            if (m.startsWith(e.prefix())) {
                return new Pricing(e.inputPerMillion(), e.outputPerMillion());
            }
        }
        return DEFAULT;
    }

    public double estimateCost(int inputTokens, int outputTokens) {
        return inputTokens / 1_000_000.0 * inputPerMillion
                + outputTokens / 1_000_000.0 * outputPerMillion;
    }
}
