package com.codereview.kit.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 敏感数据识别与脱敏（输出护栏）。
 */
class SensitiveDataGuardTest {

    @Test
    void detectsEmailPhoneAndToken() {
        String text = "联系 alice@example.com 或 13800138000，密钥 sk-abcdefghijklmnop123456";
        var findings = SensitiveDataGuard.detect(text);
        assertTrue(findings.stream().anyMatch(f -> f.type().equals("email")));
        assertTrue(findings.stream().anyMatch(f -> f.type().equals("phone")));
        assertTrue(findings.stream().anyMatch(f -> f.type().equals("api_key")));
    }

    @Test
    void redactsAllHitPatterns() {
        String redacted = SensitiveDataGuard.redact("邮箱 alice@example.com 电话 13800138000");
        assertFalse(redacted.contains("alice@example.com"));
        assertFalse(redacted.contains("13800138000"));
        assertTrue(redacted.contains("****"));
    }

    @Test
    void cleanTextUntouched() {
        assertEquals("普通文本 123", SensitiveDataGuard.redact("普通文本 123"));
    }

    @Test
    void outputGuardInterceptorRedacts() {
        OutputGuardInterceptor guard = new OutputGuardInterceptor();
        String out = guard.after("query", "我的邮箱是 bob@corp.com");
        assertFalse(out.contains("bob@corp.com"));
        assertEquals("query", guard.before("query"));
    }
}
