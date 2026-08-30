package com.codereview.kit.obs;

import java.util.Arrays;
import java.util.List;

/**
 * GenAI 可观测性 tracer（记录调用 span，供链路追踪 / 成本核算 / 质量评估）。
 *
 * <p>实现需保证：{@link #record(GenAiSpan)} 自身不抛异常，也不改变调用语义——
 * 观测永远是旁路。
 */
public interface GenAiTracer {

    /** 记录一次已完成调用。 */
    void record(GenAiSpan span);

    /** 便捷：记录一次普通调用（自动计时）。 */
    default GenAiSpan record(String operation, Runnable call) {
        long start = System.currentTimeMillis();
        call.run();
        GenAiSpan span = new GenAiSpan(genId(), null, operation,
                System.currentTimeMillis() - start, null, null, null);
        record(span);
        return span;
    }

    /**
     * 便捷：记录一次有返回值的调用，失败时也记录（把异常写进 {@link GenAiSpan#error()}）后原样抛出。
     *
     * <p>0.1.0 的行为是"异常时不记录任何 span"，导致失败调用在观测侧完全不可见——
     * 恰恰是最需要被看见的那部分。
     *
     * <p>刻意不重载 {@code record(String, Runnable)}：两个函数式接口在同一 lambda 上会产生
     * 重载歧义（void 返回值推断不确定），改名 {@code trace} 让调用点意图明确。
     */
    default <T> T trace(String operation, java.util.function.Supplier<T> call) {
        long start = System.currentTimeMillis();
        try {
            return call.get();
        } catch (RuntimeException e) {
            record(GenAiSpan.builder(operation)
                    .durationMs(System.currentTimeMillis() - start)
                    .error(e)
                    .build());
            throw e;
        }
    }

    private String genId() {
        return Long.toHexString(System.nanoTime());
    }

    /** 把多个 tracer 合成一个：任一失败不影响其余（观测旁路不得拖垮主链路）。 */
    static GenAiTracer composite(GenAiTracer... tracers) {
        List<GenAiTracer> copy = Arrays.stream(tracers)
                .filter(t -> t != null)
                .toList();
        if (copy.isEmpty()) {
            return span -> {
            };
        }
        if (copy.size() == 1) {
            return copy.get(0);
        }
        return span -> {
            for (GenAiTracer t : copy) {
                try {
                    t.record(span);
                } catch (RuntimeException ignored) {
                    // 观测旁路：单个 tracer 故障不影响主链路与其他 tracer
                }
            }
        };
    }
}
