package com.codereview.kit.obs;

import com.codereview.kit.ChatModel;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * obs 包 0.1.1 增强的回归测试：失败留痕 / traceId 贯通 / 流式观测 / 聚合明细。
 */
class TracingEnhancementTest {

    @Test
    void failureIsRecordedAndRethrown() {
        LoggingGenAiTracer tracer = new LoggingGenAiTracer();
        ChatModel traced = new TracedChatModel(p -> {
            throw new IllegalStateException("模型不可用");
        }, tracer);

        assertThrows(IllegalStateException.class, () -> traced.chat("hi"));

        assertEquals(1, tracer.spans().size(), "失败的调用也必须留下 span");
        GenAiSpan span = tracer.spans().get(0);
        assertTrue(span.failed());
        assertTrue(span.error().contains("模型不可用"));
    }

    @Test
    void traceIdAndModelAreCarriedIntoSpans() {
        LoggingGenAiTracer tracer = new LoggingGenAiTracer();
        ChatModel traced = new TracedChatModel(p -> "ok", tracer, () -> "trace-abc123", "qwen-plus");

        traced.chat("hi");

        GenAiSpan span = tracer.spans().get(0);
        assertEquals("trace-abc123", span.traceId());
        assertEquals("qwen-plus", span.model());
        assertFalse(span.failed());
    }

    @Test
    void customTokenEstimatorIsUsed() {
        LoggingGenAiTracer tracer = new LoggingGenAiTracer();
        ChatModel traced = new TracedChatModel(p -> "ok", tracer, TraceIdSupplier.NONE, "m",
                text -> text == null ? 0 : 42);

        traced.chat("hi");

        GenAiSpan span = tracer.spans().get(0);
        assertEquals(42, span.inputTokens());
        assertEquals(42, span.outputTokens());
    }

    @Test
    void streamCallsAreTracedExactlyOnce() throws Exception {
        LoggingGenAiTracer tracer = new LoggingGenAiTracer();
        ChatModel traced = new TracedChatModel(p -> "你好世界", tracer);

        List<String> chunks = new ArrayList<>();
        collect(traced.stream("hi"), chunks);

        assertEquals(List.of("你好世界"), chunks);
        assertEquals(1, tracer.spans().size(), "流式调用此前完全绕过 tracer，现在应恰好记录一次");
        assertEquals("llm.stream", tracer.spans().get(0).operation());
    }

    @Test
    void streamErrorIsTraced() throws Exception {
        LoggingGenAiTracer tracer = new LoggingGenAiTracer();
        ChatModel delegate = new ChatModel() {
            @Override
            public String chat(String prompt) {
                return "unused";
            }

            @Override
            public Flow.Publisher<String> stream(String prompt) {
                return subscriber -> {
                    subscriber.onSubscribe(new Flow.Subscription() {
                        @Override
                        public void request(long n) {
                        }

                        @Override
                        public void cancel() {
                        }
                    });
                    subscriber.onNext("半截内容");
                    subscriber.onError(new IllegalStateException("流中断"));
                };
            }
        };
        ChatModel traced = new TracedChatModel(delegate, tracer);

        assertThrows(IllegalStateException.class, () -> collect(traced.stream("hi"), new ArrayList<>()));

        assertEquals(1, tracer.spans().size());
        assertTrue(tracer.spans().get(0).failed());
    }

    @Test
    void tracerFailureNeverBreaksTheCallChain() {
        GenAiTracer boom = span -> {
            throw new RuntimeException("tracer 挂了");
        };
        ChatModel traced = new TracedChatModel(p -> "仍然可用", boom);
        assertEquals("仍然可用", traced.chat("hi"), "观测是旁路，tracer 故障不得影响主链路");
    }

    @Test
    void aggregateTracerTracksErrorsAndGroupsByOperation() {
        AggregateTracer agg = new AggregateTracer();
        agg.record(GenAiSpan.builder("llm.chat").durationMs(100).tokens(10, 20).cost(0.01).build());
        agg.record(GenAiSpan.builder("llm.chat").durationMs(300).tokens(10, 20).error("超时").build());
        agg.record(GenAiSpan.builder("tool.call").durationMs(50).tokens(1, 1).build());

        assertEquals(3, agg.calls());
        assertEquals(1, agg.errors());
        assertEquals(21, agg.inputTokens());
        assertEquals(41, agg.outputTokens());
        assertEquals(150.0, agg.avgLatencyMs(), 0.001);
        assertEquals(0.01, agg.estimatedCostUsd(), 1e-9);

        Map<String, AggregateTracer.Stats> byOp = agg.byOperation();
        assertEquals(2, byOp.size());
        assertEquals(0.5, byOp.get("llm.chat").errorRate(), 0.001);
        assertEquals(200.0, byOp.get("llm.chat").avgLatencyMs(), 0.001);
        assertEquals(0.0, byOp.get("tool.call").errorRate(), 0.001);

        agg.reset();
        assertEquals(0, agg.calls());
    }

    @Test
    void snapshotExposesAllDimensions() {
        AggregateTracer agg = new AggregateTracer();
        agg.record(GenAiSpan.builder("llm.chat").durationMs(20).tokens(3, 4).cost(0.5).build());

        AggregateTracer.Stats s = agg.snapshot();
        assertEquals(1, s.calls());
        assertEquals(0, s.errors());
        assertEquals(3, s.inputTokens());
        assertEquals(4, s.outputTokens());
        assertEquals(20.0, s.avgLatencyMs(), 0.001);
        assertEquals(0.5, s.estimatedCostUsd(), 1e-9);
    }

    @Test
    void compositeTracerFansOutAndSurvivesThrowingMember() {
        LoggingGenAiTracer a = new LoggingGenAiTracer();
        AggregateTracer b = new AggregateTracer();
        GenAiTracer boom = span -> {
            throw new RuntimeException("故意失败");
        };
        GenAiTracer composite = GenAiTracer.composite(boom, a, b);

        composite.record(GenAiSpan.builder("llm.chat").durationMs(5).tokens(1, 1).build());

        assertEquals(1, a.spans().size(), "故障 tracer 不应阻断其他 tracer");
        assertEquals(1, b.calls());
    }

    @Test
    void legacySevenArgConstructorStillWorks() {
        GenAiSpan span = new GenAiSpan("s1", null, "llm.chat", 10L, 1, 2, 0.5);
        assertNull(span.traceId());
        assertNull(span.model());
        assertFalse(span.failed());
        assertTrue(span.attributes().isEmpty());
    }

    @Test
    void attributesAreImmutableAndReadable() {
        GenAiSpan span = GenAiSpan.builder("llm.chat")
                .attribute("agent", "security")
                .attribute("team", "t1")
                .build();
        assertEquals("security", span.attribute("agent"));
        assertEquals("t1", span.attribute("team"));
        assertNull(span.attribute("missing"));
        assertThrows(UnsupportedOperationException.class, () -> span.attributes().put("x", "y"));
    }

    @Test
    void traceRecordsFailureAndRethrows() {
        LoggingGenAiTracer tracer = new LoggingGenAiTracer();
        assertThrows(IllegalArgumentException.class,
                () -> tracer.trace("plan", () -> {
                    throw new IllegalArgumentException("计划不可解析");
                }));
        assertEquals(1, tracer.spans().size());
        assertNotNull(tracer.spans().get(0).error());
    }

    /** 同步收集流式输出（测试用）。 */
    private static void collect(Flow.Publisher<String> publisher, List<String> sink) throws Exception {
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        publisher.subscribe(new Flow.Subscriber<>() {
            Flow.Subscription subscription;

            @Override
            public void onSubscribe(Flow.Subscription s) {
                this.subscription = s;
                s.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(String item) {
                sink.add(item);
                subscription.request(1);
            }

            @Override
            public void onError(Throwable t) {
                if (t instanceof RuntimeException re) {
                    sink.clear();
                    throw re;
                }
                throw new RuntimeException(t);
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });
        latch.await(2, java.util.concurrent.TimeUnit.SECONDS);
    }
}
