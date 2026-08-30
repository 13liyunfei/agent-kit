package com.codereview.kit.model;

import com.codereview.kit.ChatModel;
import com.codereview.kit.obs.GenAiSpan;
import com.codereview.kit.obs.GenAiTracer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 韧性包装（超时 / 重试退避 / 限流 / 指标成本），调用治理层。
 *
 * <p>把任意 {@link ChatModel} 包一层即可获得生产级调用语义，每次尝试（含失败重试）
 * 都会计入 {@link UsageStats}：
 * <pre>
 * ChatModel model = new ResilientChatModel(openAi,
 *         new ResilientChatModel.Config().timeoutMs(30_000).retries(3, 200).rateLimiter(rl),
 *         usageStats, tracer, metricsSink);
 * </pre>
 */
public class ResilientChatModel implements ChatModel {

    /** 配置。 */
    public static class Config {
        long timeoutMs = 60_000;
        RetryPolicy retryPolicy = RetryPolicy.none();
        RateLimiter rateLimiter = null;

        public Config timeoutMs(long ms) {
            this.timeoutMs = ms;
            return this;
        }

        public Config retries(int times, long baseBackoffMs) {
            this.retryPolicy = RetryPolicy.exponential(times, baseBackoffMs);
            return this;
        }

        public Config rateLimiter(RateLimiter rl) {
            this.rateLimiter = rl;
            return this;
        }
    }

    private final ChatModel delegate;
    private final Config config;
    private final UsageStats stats;
    private final GenAiTracer tracer;
    private final MetricsSink sink;

    public ResilientChatModel(ChatModel delegate, Config config) {
        this(delegate, config, new UsageStats(), null, MetricsSink.NONE);
    }

    public ResilientChatModel(ChatModel delegate, Config config, UsageStats stats,
                              GenAiTracer tracer, MetricsSink sink) {
        this.delegate = delegate;
        this.config = config;
        this.stats = stats == null ? new UsageStats() : stats;
        this.tracer = tracer;
        this.sink = sink == null ? MetricsSink.NONE : sink;
    }

    public UsageStats stats() {
        return stats;
    }

    @Override
    public String chat(String prompt) {
        if (config.rateLimiter != null && !config.rateLimiter.tryAcquire()) {
            stats.recordFailure();
            throw new IllegalStateException("限流：请求被拒绝");
        }
        String result = RetryPolicy.run(config.retryPolicy, () -> attempt(prompt));
        sink.export(stats.snapshot());
        return result;
    }

    /** 单次尝试：执行 + 记账（成功记 token/延迟，失败记 failure）。 */
    private String attempt(String prompt) {
        long start = System.currentTimeMillis();
        try {
            String r = timed(() -> delegate.chat(prompt));
            long ms = System.currentTimeMillis() - start;
            int out = estimateTokens(r);
            stats.record(0, out, 0, ms);
            if (tracer != null) {
                tracer.record(new GenAiSpan(Long.toHexString(System.nanoTime()), null, "llm.chat",
                        ms, null, out, null));
            }
            return r;
        } catch (RuntimeException e) {
            stats.recordFailure();
            if (tracer != null) {
                tracer.record(GenAiSpan.builder("llm.chat")
                        .durationMs(System.currentTimeMillis() - start)
                        .error(e)
                        .build());
            }
            throw e;
        }
    }

    private <T> T timed(java.util.function.Supplier<T> call) {
        try {
            CompletableFuture<T> f = CompletableFuture.supplyAsync(call);
            return f.get(config.timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new RuntimeException("LLM 调用超时（>" + config.timeoutMs + "ms）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("LLM 调用被中断", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw cause instanceof RuntimeException re ? re : new RuntimeException(cause);
        }
    }

    private static int estimateTokens(String text) {
        return text == null ? 0 : Math.max(1, text.length() / 4);
    }
}
