package com.codereview.kit.model;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LLM 调用聚合指标（线程安全）：调用量 / 失败 / token / 估算成本 / 延迟分布。
 *
 * <p>由 {@link ResilientChatModel} 或 {@code obs.TracedChatModel} 累计，
 * 经 {@link MetricsSink} 导出（日志 / 监控 / 成本核算）。
 */
public class UsageStats {

    private final AtomicLong calls = new AtomicLong();
    private final AtomicLong failures = new AtomicLong();
    private final AtomicLong inputTokens = new AtomicLong();
    private final AtomicLong outputTokens = new AtomicLong();
    private final AtomicLong costCents = new AtomicLong(); // 成本以美分整数累计，避免浮点漂移
    private final AtomicLong latencyMs = new AtomicLong();
    private final AtomicLong maxLatencyMs = new AtomicLong();

    public void record(int in, int out, double costUsd, long latency) {
        calls.incrementAndGet();
        inputTokens.addAndGet(Math.max(0, in));
        outputTokens.addAndGet(Math.max(0, out));
        costCents.addAndGet(Math.round(costUsd * 100));
        latencyMs.addAndGet(latency);
        maxLatencyMs.accumulateAndGet(latency, Math::max);
    }

    public void recordFailure() {
        calls.incrementAndGet();
        failures.incrementAndGet();
    }

    public long calls() {
        return calls.get();
    }

    public long failures() {
        return failures.get();
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

    public long maxLatencyMs() {
        return maxLatencyMs.get();
    }

    public Snapshot snapshot() {
        return new Snapshot(calls.get(), failures.get(), inputTokens.get(), outputTokens.get(),
                estimatedCostUsd(), avgLatencyMs(), maxLatencyMs.get());
    }

    /** 不可变快照（导出 / 序列化用）。 */
    public record Snapshot(long calls, long failures, long inputTokens, long outputTokens,
                           double estimatedCostUsd, double avgLatencyMs, long maxLatencyMs) {
    }
}
