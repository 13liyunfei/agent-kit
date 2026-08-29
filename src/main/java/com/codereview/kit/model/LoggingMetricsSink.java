package com.codereview.kit.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志式指标导出：JSON 行输出（slf4j），监控侧可采集。
 */
public class LoggingMetricsSink implements MetricsSink {

    private static final Logger log = LoggerFactory.getLogger(LoggingMetricsSink.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void export(UsageStats.Snapshot s) {
        try {
            log.info("[metrics] {}", MAPPER.writeValueAsString(s));
        } catch (Exception e) {
            log.warn("[metrics] 序列化失败：{}", e.getMessage());
        }
    }
}
