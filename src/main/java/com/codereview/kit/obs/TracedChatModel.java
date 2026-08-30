package com.codereview.kit.obs;

import com.codereview.kit.ChatModel;

import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可观测 ChatModel 包装：每次调用自动记录 span（耗时 / 可选 token / 错误）。
 * 使用方包一层即可获得全链路可观测性，无需改业务代码。
 *
 * <p>0.1.1 相对 0.1.0 的四点增强（均来自落地项目的真实缺口）：
 * <ol>
 *   <li><b>失败也留痕</b>：0.1.0 在 {@code delegate.chat} 抛异常时不记录任何 span，
 *       恰恰漏掉了最该被观测的调用。现在记录带 {@code error} 的 span 后原样抛出。</li>
 *   <li><b>携带 traceId</b>：通过 {@link TraceIdSupplier} 把使用方已有的链路 id 写进 span，
 *       从而把散落在多个 agent 线程里的调用串成一条链（基座不自己造链路上下文）。</li>
 *   <li><b>流式也观测</b>：{@code stream()} 之前完全绕过 tracer，现在按完整流记录一个 span。</li>
 *   <li><b>token 估算可替换</b>：默认"字符数/4"，可注入真实 tokenizer 或网关回传的 usage。</li>
 * </ol>
 *
 * <p>观测是旁路：任何情况下都不会改变 {@code delegate} 的返回语义，
 * tracer 抛出的异常也不会冒泡到调用方。
 */
public class TracedChatModel implements ChatModel {

    private final ChatModel delegate;
    private final GenAiTracer tracer;
    private final TraceIdSupplier traceIdSupplier;
    private final TokenEstimator tokenEstimator;
    private final String model;

    public TracedChatModel(ChatModel delegate, GenAiTracer tracer) {
        this(delegate, tracer, TraceIdSupplier.NONE, null);
    }

    public TracedChatModel(ChatModel delegate, GenAiTracer tracer, TraceIdSupplier traceIdSupplier, String model) {
        this(delegate, tracer, traceIdSupplier, model, TokenEstimator.DEFAULT);
    }

    public TracedChatModel(ChatModel delegate, GenAiTracer tracer, TraceIdSupplier traceIdSupplier,
                           String model, TokenEstimator tokenEstimator) {
        this.delegate = delegate;
        this.tracer = tracer;
        this.traceIdSupplier = traceIdSupplier == null ? TraceIdSupplier.NONE : traceIdSupplier;
        this.model = model;
        this.tokenEstimator = tokenEstimator == null ? TokenEstimator.DEFAULT : tokenEstimator;
    }

    @Override
    public String chat(String prompt) {
        long start = System.currentTimeMillis();
        try {
            String resp = delegate.chat(prompt);
            record("llm.chat", start, prompt, resp, null);
            return resp;
        } catch (RuntimeException e) {
            record("llm.chat", start, prompt, null, e);
            throw e;
        }
    }

    @Override
    public Flow.Publisher<String> stream(String prompt) {
        long start = System.currentTimeMillis();
        AtomicLong chunks = new AtomicLong();
        StringBuilder acc = new StringBuilder();
        return downstream -> delegate.stream(prompt).subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                downstream.onSubscribe(subscription);
            }

            @Override
            public void onNext(String item) {
                chunks.incrementAndGet();
                if (item != null) {
                    acc.append(item);
                }
                downstream.onNext(item);
            }

            @Override
            public void onError(Throwable throwable) {
                record("llm.stream", start, prompt, acc.toString(),
                        throwable instanceof RuntimeException re ? re : new RuntimeException(throwable));
                downstream.onError(throwable);
            }

            @Override
            public void onComplete() {
                record("llm.stream", start, prompt, acc.toString(), null);
                downstream.onComplete();
            }
        });
    }

    private void record(String operation, long start, String prompt, String response, RuntimeException error) {
        try {
            tracer.record(GenAiSpan.builder(operation)
                    .traceId(traceIdSupplier.get())
                    .model(model)
                    .durationMs(System.currentTimeMillis() - start)
                    .tokens(tokenEstimator.estimate(prompt), tokenEstimator.estimate(response))
                    .attributes(attributes())
                    .error(error)
                    .build());
        } catch (RuntimeException ignored) {
            // 观测旁路：tracer 故障绝不影响主链路
        }
    }

    /** 业务标签扩展点：子类可覆盖以附加 agent / team / 请求维度信息。 */
    protected Map<String, String> attributes() {
        return Map.of();
    }

    /** 兼容 0.1.0 的静态估算方法。 */
    static int estimateTokens(String text) {
        return TokenEstimator.DEFAULT.estimate(text);
    }
}
