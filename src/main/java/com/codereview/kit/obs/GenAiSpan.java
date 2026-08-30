package com.codereview.kit.obs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一次 GenAI 调用的可观测 span（对齐 OTel GenAI 语义约定的常用字段）。
 *
 * <p>0.1.1 相比 0.1.0 新增了 {@code traceId} / {@code model} / {@code error} /
 * {@code attributes} 四个字段——原因来自落地项目（code-review-agent）的真实需求：
 * 只靠 spanId + parentId 无法把一次业务请求里散落在多个 agent 线程中的 LLM 调用串起来，
 * 也无法回答"失败的那次调用到底错在哪"。
 *
 * @param spanId      本 span id（trace 内唯一）
 * @param traceId     所属链路 id（可空；由使用方的链路上下文提供，如 MDC traceId）
 * @param parentId    父 span id（可空）
 * @param operation   操作名（如 llm.chat / tool.call / plan）
 * @param model       模型名（可空）
 * @param durationMs  耗时
 * @param inputTokens 输入 token 数（可空）
 * @param outputTokens 输出 token 数（可空）
 * @param cost        成本（估算，可空）
 * @param error       错误信息（可空；非空即代表本次调用失败）
 * @param attributes  业务自定义标签（如 agent / team / pr），不可变副本
 */
public record GenAiSpan(String spanId, String traceId, String parentId, String operation, String model,
                        long durationMs, Integer inputTokens, Integer outputTokens, Double cost,
                        String error, Map<String, String> attributes) {

    public GenAiSpan {
        attributes = attributes == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /** 兼容 0.1.0 的 7 参构造（无 traceId / model / error / attributes）。 */
    public GenAiSpan(String spanId, String parentId, String operation,
                     long durationMs, Integer inputTokens, Integer outputTokens, Double cost) {
        this(spanId, null, parentId, operation, null, durationMs, inputTokens, outputTokens, cost, null, Map.of());
    }

    /** 本次调用是否失败。 */
    public boolean failed() {
        return error != null && !error.isBlank();
    }

    /** 读取业务标签，缺失返回 null。 */
    public String attribute(String key) {
        return attributes.get(key);
    }

    /** 链式构造：从最小字段出发补齐。 */
    public static Builder builder(String operation) {
        return new Builder(operation);
    }

    /** {@link GenAiSpan} 的流式构造器。 */
    public static final class Builder {
        private final String operation;
        private String spanId = Long.toHexString(System.nanoTime());
        private String traceId;
        private String parentId;
        private String model;
        private long durationMs;
        private Integer inputTokens;
        private Integer outputTokens;
        private Double cost;
        private String error;
        private final Map<String, String> attributes = new LinkedHashMap<>();

        private Builder(String operation) {
            this.operation = operation;
        }

        public Builder spanId(String v) {
            this.spanId = v;
            return this;
        }

        public Builder traceId(String v) {
            this.traceId = v;
            return this;
        }

        public Builder parentId(String v) {
            this.parentId = v;
            return this;
        }

        public Builder model(String v) {
            this.model = v;
            return this;
        }

        public Builder durationMs(long v) {
            this.durationMs = v;
            return this;
        }

        public Builder tokens(Integer in, Integer out) {
            this.inputTokens = in;
            this.outputTokens = out;
            return this;
        }

        public Builder cost(Double v) {
            this.cost = v;
            return this;
        }

        public Builder error(String v) {
            this.error = v;
            return this;
        }

        public Builder error(Throwable t) {
            this.error = t == null ? null : String.valueOf(t.getMessage());
            return this;
        }

        public Builder attribute(String k, String v) {
            if (k != null && v != null) {
                attributes.put(k, v);
            }
            return this;
        }

        public Builder attributes(Map<String, String> m) {
            if (m != null) {
                attributes.putAll(m);
            }
            return this;
        }

        public GenAiSpan build() {
            return new GenAiSpan(spanId, traceId, parentId, operation, model,
                    durationMs, inputTokens, outputTokens, cost, error, attributes);
        }
    }
}
