package com.codereview.kit.security;

import com.codereview.kit.extension.spi.LlmInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 输出护栏（LlmInterceptor.after）：LLM 响应脱敏 + 敏感命中告警。
 *
 * <p>作为内置扩展注册到 {@code ExtensionRegistry}，order=100（标准实现层），
 * 项目自定义审计可 order<100 叠加。
 */
public class OutputGuardInterceptor implements LlmInterceptor {

    private static final Logger log = LoggerFactory.getLogger(OutputGuardInterceptor.class);

    @Override
    public String before(String prompt) {
        return prompt;
    }

    @Override
    public String after(String prompt, String response) {
        var findings = SensitiveDataGuard.detect(response);
        if (!findings.isEmpty()) {
            log.warn("[OutputGuard] 响应包含敏感数据 {} 类，已脱敏", findings.size());
            return SensitiveDataGuard.redact(response);
        }
        return response;
    }

    @Override
    public String name() {
        return "security.output-guard";
    }

    @Override
    public int order() {
        return 100;
    }
}
