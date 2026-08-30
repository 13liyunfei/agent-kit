package com.codereview.kit.obs;

/**
 * token 估算策略。
 *
 * <p>基座默认按"字符数 / 4"粗估（零依赖、对中文也大致成立）；
 * 使用方若接了真实 tokenizer 或模型网关会回传 {@code usage}，可注入自己的实现。
 */
@FunctionalInterface
public interface TokenEstimator {

    /** 粗略但零依赖的默认估算。 */
    TokenEstimator DEFAULT = text -> text == null ? 0 : Math.max(1, text.length() / 4);

    /** 估算文本 token 数。 */
    int estimate(String text);
}
