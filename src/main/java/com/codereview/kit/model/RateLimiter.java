package com.codereview.kit.model;

/**
 * 令牌桶限流器（线程安全）：固定速率补充令牌，容量封顶。
 *
 * <p>超限时 {@link #tryAcquire()} 返回 false，调用方决定等待或放弃
 * （配合 {@link ResilientChatModel} 使用）。
 */
public class RateLimiter {

    private final double tokensPerSecond;
    private final double capacity;
    private double tokens;
    private long lastRefillNanos;

    public RateLimiter(double tokensPerSecond, double capacity) {
        this.tokensPerSecond = tokensPerSecond;
        this.capacity = capacity;
        this.tokens = capacity;
        this.lastRefillNanos = System.nanoTime();
    }

    /** 尝试取 1 个令牌：成功返回 true。 */
    public boolean tryAcquire() {
        return tryAcquire(1);
    }

    /** 尝试取 n 个令牌。 */
    public synchronized boolean tryAcquire(int n) {
        refill();
        if (tokens < n) {
            return false;
        }
        tokens -= n;
        return true;
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsedSec = (now - lastRefillNanos) / 1_000_000_000.0;
        tokens = Math.min(capacity, tokens + elapsedSec * tokensPerSecond);
        lastRefillNanos = now;
    }
}
