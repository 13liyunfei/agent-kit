package com.codereview.kit.model;

/**
 * 原生对话调用的可选参数。
 *
 * @param temperature 采样温度（可空，由适配器决定默认）
 * @param maxTokens   输出上限（可空）
 * @param jsonMode    是否强制 JSON 结构化输出（对齐 response_format）
 * @param timeoutMs   单次调用超时（可空，默认 60s）
 */
public record NativeOptions(Double temperature, Integer maxTokens, boolean jsonMode, Long timeoutMs) {

    public static NativeOptions defaults() {
        return new NativeOptions(null, null, false, null);
    }
}
