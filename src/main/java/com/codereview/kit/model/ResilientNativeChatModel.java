package com.codereview.kit.model;
import com.codereview.kit.model.ToolSchema;

import com.codereview.kit.obs.GenAiSpan;
import com.codereview.kit.obs.GenAiTracer;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 原生函数调用路径的韧性包装（超时 / 重试 / 限流 / 用量成本累计）。
 *
 * <p>与 {@link ResilientChatModel} 互补：前者管文本路径，本类管 tools 参数路径。
 */
public class ResilientNativeChatModel implements NativeChatModel {

    private final NativeChatModel delegate;
    private final long timeoutMs;
    private final RetryPolicy retryPolicy;
    private final RateLimiter rateLimiter;
    private final UsageStats stats;
    private final GenAiTracer tracer;

    public ResilientNativeChatModel(NativeChatModel delegate, long timeoutMs, RetryPolicy retryPolicy,
                                    RateLimiter rateLimiter, UsageStats stats, GenAiTracer tracer) {
        this.delegate = delegate;
        this.timeoutMs = timeoutMs;
        this.retryPolicy = retryPolicy;
        this.rateLimiter = rateLimiter;
        this.stats = stats == null ? new UsageStats() : stats;
        this.tracer = tracer;
    }

    @Override
    public NativeResult chat(List<NativeMessage> messages, List<ToolSchema> tools, NativeOptions options) {
        if (rateLimiter != null && !rateLimiter.tryAcquire()) {
            stats.recordFailure();
            throw new IllegalStateException("限流：请求被拒绝");
        }
        NativeResult r = RetryPolicy.run(retryPolicy, () -> {
            long attemptStart = System.currentTimeMillis();
            try {
                NativeResult rr = timed(() -> delegate.chat(messages, tools, options));
                long ms = System.currentTimeMillis() - attemptStart;
                int in = rr.inputTokens() == null ? 0 : rr.inputTokens();
                int out = rr.outputTokens() == null ? 0 : rr.outputTokens();
                double cost = rr.cost() == null ? 0 : rr.cost();
                stats.record(in, out, cost, ms);
                if (tracer != null) {
                    tracer.record(new GenAiSpan(Long.toHexString(System.nanoTime()), null, "llm.chat.tools",
                            ms, in, out, cost));
                }
                return rr;
            } catch (RuntimeException e) {
                stats.recordFailure();
                throw e;
            }
        });
        return r;
    }

    public UsageStats stats() {
        return stats;
    }

    private NativeResult timed(java.util.function.Supplier<NativeResult> call) {
        try {
            CompletableFuture<NativeResult> f = CompletableFuture.supplyAsync(call);
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            stats.recordFailure();
            throw new RuntimeException("LLM 调用超时（>" + timeoutMs + "ms）", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            stats.recordFailure();
            throw new RuntimeException("LLM 调用被中断", e);
        } catch (ExecutionException e) {
            stats.recordFailure();
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw cause instanceof RuntimeException re ? re : new RuntimeException(cause);
        }
    }
}
