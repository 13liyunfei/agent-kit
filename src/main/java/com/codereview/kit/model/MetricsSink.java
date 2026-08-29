package com.codereview.kit.model;

/**
 * 指标导出 sink（可观测性出口）。
 *
 * <p>对接日志 / 监控 / 成本平台：实现本接口并注入 {@link ResilientChatModel}，
 * 即可周期性导出 {@link UsageStats.Snapshot}。
 */
public interface MetricsSink {

    /** 导出一次快照。 */
    void export(UsageStats.Snapshot snapshot);

    /** 空实现（不导出）。 */
    MetricsSink NONE = snapshot -> {
    };
}
