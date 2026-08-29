package com.codereview.kit.model;

import com.codereview.kit.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 韧性包装：重试退避 / 超时 / 限流 / 指标累计。
 */
class ResilientChatModelTest {

    @Test
    void retriesTransientFailure() {
        AtomicInteger calls = new AtomicInteger();
        ChatModel flaky = prompt -> {
            if (calls.incrementAndGet() < 3) {
                throw new IllegalStateException("瞬时故障");
            }
            return "ok";
        };
        UsageStats stats = new UsageStats();
        ResilientChatModel model = new ResilientChatModel(flaky,
                new ResilientChatModel.Config().timeoutMs(2000).retries(4, 5), stats, null, MetricsSink.NONE);
        assertEquals("ok", model.chat("hi"));
        assertEquals(3, calls.get());
        assertEquals(3, stats.calls());
    }

    @Test
    void timeoutAborts() {
        ChatModel slow = prompt -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return "late";
        };
        ResilientChatModel model = new ResilientChatModel(slow,
                new ResilientChatModel.Config().timeoutMs(100).retries(0, 0), new UsageStats(), null, MetricsSink.NONE);
        assertThrows(RuntimeException.class, () -> model.chat("hi"));
    }

    @Test
    void rateLimiterRejectsWhenExhausted() {
        ChatModel ok = prompt -> "ok";
        RateLimiter rl = new RateLimiter(0.001, 1); // 容量 1，几乎不补充
        ResilientChatModel model = new ResilientChatModel(ok,
                new ResilientChatModel.Config().rateLimiter(rl), new UsageStats(), null, MetricsSink.NONE);
        assertEquals("ok", model.chat("1"));
        assertThrows(IllegalStateException.class, () -> model.chat("2"));
    }

    @Test
    void nativeResilientRecordsUsage() {
        NativeChatModel raw = (messages, tools, options) ->
                new NativeResult("hi", java.util.List.of(), 100, 20, 0.01, "{}");
        UsageStats stats = new UsageStats();
        ResilientNativeChatModel model = new ResilientNativeChatModel(raw, 2000, RetryPolicy.none(),
                null, stats, null);
        model.chat(java.util.List.of(), java.util.List.of(), NativeOptions.defaults());
        assertTrue(stats.calls() == 1);
        assertEquals(100, stats.inputTokens());
        assertEquals(20, stats.outputTokens());
    }
}
