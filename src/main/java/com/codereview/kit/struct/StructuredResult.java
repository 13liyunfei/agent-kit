package com.codereview.kit.struct;

import java.util.function.Function;

/**
 * 结构化输出的结果（<b>非抛出式</b>，便于调用方优雅降级）。
 *
 * <p>为什么需要它：生产系统里"模型这次没返回合法 JSON"是<b>常态</b>而非异常。
 * 若结构化输出以抛异常告终，调用方就只能 {@code try/catch} 后丢失现场；
 * 而返回本结果对象时，调用方可以：
 * <ol>
 *   <li>拿 {@link #rawResponse()} 回退到文本解析；</li>
 *   <li>拿 {@link #attempts()} 做质量统计（重试率过高说明提示词或 schema 有问题）；</li>
 *   <li>拿 {@link #error()} 记日志定位。</li>
 * </ol>
 *
 * <p>这是"结构化输出是增强而非必需"这一设计原则的落点——
 * 增强失败时，调用方仍能走没有它时本该走的通路。
 *
 * @param ok           是否解析并校验成功
 * @param value        成功时的强类型对象；失败为 {@code null}
 * @param rawResponse  模型最后一次的原始输出（失败时可供回退解析）
 * @param attempts     实际调用模型的次数（1 表示首次即成功）
 * @param error        失败原因；成功为 {@code null}
 */
public record StructuredResult<T>(boolean ok,
                                  T value,
                                  String rawResponse,
                                  int attempts,
                                  String error) {

    public static <T> StructuredResult<T> success(T value, String rawResponse, int attempts) {
        return new StructuredResult<>(true, value, rawResponse, attempts, null);
    }

    public static <T> StructuredResult<T> failure(String rawResponse, int attempts, String error) {
        return new StructuredResult<>(false, null, rawResponse, attempts, error);
    }

    /** 成功返回值，失败返回兜底值。 */
    public T orElse(T fallback) {
        return ok ? value : fallback;
    }

    /** 失败时改用原始文本自行解析（回退通路的入口）。 */
    public <R> R onFailureParseRaw(Function<String, R> rawParser, R fallback) {
        if (ok || rawResponse == null) {
            return fallback;
        }
        try {
            R parsed = rawParser.apply(rawResponse);
            return parsed == null ? fallback : parsed;
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 是否重试过（首次即成功为 false）。 */
    public boolean retried() {
        return attempts > 1;
    }
}
