package com.codereview.kit.obs;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 日志式 tracer：JSON 行输出（经 slf4j，使用方自带日志后端），并保留在内存供断言/导出。
 */
public class LoggingGenAiTracer implements GenAiTracer {

    private static final Logger log = LoggerFactory.getLogger(LoggingGenAiTracer.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<GenAiSpan> spans = new ArrayList<>();

    @Override
    public synchronized void record(GenAiSpan span) {
        spans.add(span);
        try {
            log.info("[genai] {}", mapper.writeValueAsString(span));
        } catch (Exception e) {
            log.warn("[genai] span 序列化失败（忽略）：{}", e.getMessage());
        }
    }

    /** 已记录 span（供测试断言 / 导出）。 */
    public synchronized List<GenAiSpan> spans() {
        return List.copyOf(spans);
    }

    public synchronized void reset() {
        spans.clear();
    }
}
