package com.codereview.kit.obs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 聚合 tracer：把 GenAI span 汇总为调用量 / 错误数 / token / 成本 / 延迟指标
 * （与 {@code model.UsageStats} 互补：前者在调用侧累计，本类在观测侧累计）。
 *
 * <p>0.1.1 新增：错误计数、按操作名分组的明细、快照与重置——
 * 落地项目要回答的是"这次审查一共调了几次模型、失败几次、花了多少"，
 * 只有一个全局调用数是不够的。
 */
public class AggregateTracer implements GenAiTracer {

    /** 一组累计值的不可变快照。 */
    public record Stats(long calls, long errors, long inputTokens, long outputTokens, long latencyMs, long costMicros) {

        /** 平均耗时（无调用时为 0）。 */
        public double avgLatencyMs() {
            return calls == 0 ? 0 : latencyMs / (double) calls;
        }

        /** 估算成本（USD，按微元累计避免浮点累加误差）。 */
        public double estimatedCostUsd() {
            return costMicros / 1_000_000.0;
        }

        /** 错误率（0~1，无调用时为 0）。 */
        public double errorRate() {
            return calls == 0 ? 0 : errors / (double) calls;
        }
    }

    /** 可变累计器。 */
    private static final class Acc {
        long calls;
        long errors;
        long inputTokens;
        long outputTokens;
        long latencyMs;
        long costMicros;

        Stats snapshot() {
            return new Stats(calls, errors, inputTokens, outputTokens, latencyMs, costMicros);
        }
    }

    private final Map<String, Acc> byOperation = new LinkedHashMap<>();

    @Override
    public synchronized void record(GenAiSpan span) {
        Acc acc = byOperation.computeIfAbsent(
                span.operation() == null ? "unknown" : span.operation(), k -> new Acc());
        acc.calls++;
        if (span.failed()) {
            acc.errors++;
        }
        acc.latencyMs += Math.max(0, span.durationMs());
        if (span.inputTokens() != null) {
            acc.inputTokens += span.inputTokens();
        }
        if (span.outputTokens() != null) {
            acc.outputTokens += span.outputTokens();
        }
        if (span.cost() != null) {
            acc.costMicros += Math.round(span.cost() * 1_000_000);
        }
    }

    /** 总调用次数。 */
    public synchronized long calls() {
        return overall().calls();
    }

    /** 总失败次数。 */
    public synchronized long errors() {
        return overall().errors();
    }

    /** 总输入 token。 */
    public synchronized long inputTokens() {
        return overall().inputTokens();
    }

    /** 总输出 token。 */
    public synchronized long outputTokens() {
        return overall().outputTokens();
    }

    /** 估算总成本（USD）。 */
    public synchronized double estimatedCostUsd() {
        return overall().estimatedCostUsd();
    }

    /** 平均耗时（ms）。 */
    public synchronized double avgLatencyMs() {
        return overall().avgLatencyMs();
    }

    /** 全量快照（所有操作合计）。 */
    public synchronized Stats snapshot() {
        return overall();
    }

    /** 按操作名分组的明细快照。 */
    public synchronized Map<String, Stats> byOperation() {
        Map<String, Stats> out = new LinkedHashMap<>();
        byOperation.forEach((k, v) -> out.put(k, v.snapshot()));
        return Collections.unmodifiableMap(out);
    }

    /** 清空累计（如按"每次审查"维度统计时在开始处调用）。 */
    public synchronized void reset() {
        byOperation.clear();
    }

    private Stats overall() {
        Acc total = new Acc();
        for (Acc a : byOperation.values()) {
            total.calls += a.calls;
            total.errors += a.errors;
            total.inputTokens += a.inputTokens;
            total.outputTokens += a.outputTokens;
            total.latencyMs += a.latencyMs;
            total.costMicros += a.costMicros;
        }
        return total.snapshot();
    }
}
