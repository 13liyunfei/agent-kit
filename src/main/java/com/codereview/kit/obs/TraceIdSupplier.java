package com.codereview.kit.obs;

/**
 * 链路 id 来源。
 *
 * <p>基座刻意<b>不</b>自带链路上下文实现：traceId 的生成与跨线程传播是业务侧关注点
 * （落地项目里通常基于 SLF4J MDC，并要处理线程池复用、父子线程恢复等细节）。
 * 基座只负责"把使用方已有的 traceId 记进 span"，不重复造一份。
 *
 * <p>典型用法：
 * <pre>{@code
 * new TracedChatModel(delegate, tracer, TraceContext::getTraceId, "gpt-4o-mini");
 * }</pre>
 */
@FunctionalInterface
public interface TraceIdSupplier {

    /** 无链路上下文（span.traceId 为 null）。 */
    TraceIdSupplier NONE = () -> null;

    /** 返回当前链路 id，可能为 null。 */
    String get();
}
