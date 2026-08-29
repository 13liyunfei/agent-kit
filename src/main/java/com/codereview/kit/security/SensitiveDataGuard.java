package com.codereview.kit.security;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 敏感数据识别与脱敏（输出护栏）：邮箱 / 手机号 / 身份证 / 银行卡 / API Key / IP。
 *
 * <p>用于 LLM 输出侧防泄露（OWASP LLM09 过度代理 / 数据泄露的兜底手段之一），
 * 也可用于日志脱敏。
 */
public class SensitiveDataGuard {

    public record Finding(String type, String sample) {
    }

    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"),
            Pattern.compile("(?<!\\d)1[3-9]\\d{9}(?!\\d)"),
            Pattern.compile("(?<!\\d)[1-9]\\d{5}(?:19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx](?!\\d)"),
            Pattern.compile("(?<!\\d)(?:4\\d{3}|5[1-5]\\d{2}|6(?:011|5\\d{2}))[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}(?!\\d)"),
            Pattern.compile("sk-[A-Za-z0-9]{16,}"),
            Pattern.compile("(?:api[_-]?key|token|secret)\\s*[=:]\\s*[A-Za-z0-9_\\-]{12,}", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?<!\\d)\\d{1,3}(?:\\.\\d{1,3}){3}(?!\\d)"));

    private static final List<String> TYPES = List.of(
            "email", "phone", "id_card", "bank_card", "api_key", "credential", "ip");

    /** 识别文本中的敏感信息。 */
    public static List<Finding> detect(String text) {
        List<Finding> out = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        for (int i = 0; i < PATTERNS.size(); i++) {
            Matcher m = PATTERNS.get(i).matcher(text);
            if (m.find()) {
                out.add(new Finding(TYPES.get(i), mask(m.group())));
            }
        }
        return out;
    }

    /** 脱敏：命中部分替换为掩码。 */
    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        String out = text;
        for (Pattern p : PATTERNS) {
            out = p.matcher(out).replaceAll(m -> mask(m.group()));
        }
        return out;
    }

    private static String mask(String v) {
        if (v.length() <= 4) {
            return "****";
        }
        return v.substring(0, 2) + "****" + v.substring(v.length() - 2);
    }
}
