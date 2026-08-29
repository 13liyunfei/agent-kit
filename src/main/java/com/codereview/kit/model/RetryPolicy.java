package com.codereview.kit.model;

import java.util.function.Supplier;

/**
 * 重试策略（指数退避 + 抖动），配合超时实现调用韧性。
 *
 * @param maxAttempts 最大尝试次数（>=1）
 * @param baseBackoffMs 首次退避基数
 */
public record RetryPolicy(int maxAttempts, long baseBackoffMs) {

    public static RetryPolicy none() {
        return new RetryPolicy(1, 0);
    }

    /** 最多 retries 次重试，首次退避 baseMs，之后翻倍。 */
    public static RetryPolicy exponential(int retries, long baseMs) {
        return new RetryPolicy(Math.max(1, retries + 1), Math.max(1, baseMs));
    }

    /** 执行重试策略（结果空表示最终失败）。 */
    public static <T> T run(RetryPolicy policy, Supplier<T> attempt) {
        int tries = Math.max(1, policy.maxAttempts());
        long backoff = Math.max(0, policy.baseBackoffMs());
        for (int i = 1; i <= tries; i++) {
            try {
                T r = attempt.get();
                if (r != null) {
                    return r;
                }
            } catch (RuntimeException e) {
                if (i == tries) {
                    throw e;
                }
            }
            if (i < tries) {
                sleep(backoff);
                backoff = Math.min(60_000, backoff * 2);
            }
        }
        return null;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms + (long) (Math.random() * 50));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
