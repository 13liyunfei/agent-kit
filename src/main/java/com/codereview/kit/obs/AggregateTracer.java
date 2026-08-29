package com.codereview.kit.obs;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 聚合 tracer：把 GenAI span 汇总为调用量 / token / 成本 / 延迟指标
 * （与 {@code model.UsageStats} 互补：前者在调用侧累计，本类在观测侧累计）。
 */
public class AggregateTracer implements GenAiTracer {

    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private final AtomicLong costCents = new AtomicLong();
    private final AtomicLong latencyMs = new AtomicLong();

    @Override
    public void record(GenAiSpan span) {
        calls.incrementAndGet();
        latencyMs.addAndGet(span.durationMs());
        if (span.inputTokens() != null) {
            inputTokens.addAndGet(span.inputTokens());
        }
        if (span.outputTokens() != null) {
            outputTokens.addAndGet(span.outputTokens());
        }
        if (span.cost() != null) {
            costCents.addAndGet(Math.round(span.cost() * 100));
        }
    }

    public long calls() {
        return calls.get();
    }

    public long inputTokens() {
        return inputTokens.get();
    }

    public long outputTokens() {
        return outputTokens.get();
    }

    public double estimatedCostUsd() {
        return costCents.get() / 100.0;
    }

    public double avgLatencyMs() {
        long c = calls.get();
        return c == 0 ? 0 : latencyMs.get() / (double) c;
    }
}
