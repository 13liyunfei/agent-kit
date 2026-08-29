package com.codereview.kit.toolcalling;
import com.codereview.kit.model.ToolSchema;

import java.util.List;
import java.util.Map;

/**
 * 工具参数校验器：按 {@link ToolSchema} 校验参数（必填 / 类型），
 * 并对字符串参数做危险模式拦截（命令注入 / SQL 注入 / 路径穿越）。
 */
public final class ToolSchemaValidator {

    private ToolSchemaValidator() {
    }

    /** 危险子串（命令注入 / SQL 注入向量）。 */
    private static final List<String> DANGEROUS = List.of(
            ";", "&&", "||", "`", "$(", "rm -rf", "drop table", "truncate table",
            "delete from", "union select", "..\\", ".." + java.io.File.separator);

    /** 校验结果。 */
    public record Validation(boolean valid, String reason) {
        public static Validation ok() {
            return new Validation(true, null);
        }
    }

    /** 校验参数是否符合 schema 且无危险模式。 */
    public static Validation validate(ToolSchema schema, Map<String, Object> args) {
        Map<String, Object> props = schema.parameters() == null ? Map.of()
                : (Map<String, Object>) schema.parameters().get("properties");
        if (props == null) {
            props = Map.of();
        }
        List<?> required = schema.parameters() == null ? List.of()
                : (List<?>) schema.parameters().get("required");
        if (required != null) {
            for (Object r : required) {
                if (args == null || !args.containsKey(r.toString())) {
                    return new Validation(false, "缺少必填参数: " + r);
                }
            }
        }
        if (args == null) {
            return Validation.ok();
        }
        for (Map.Entry<String, Object> e : args.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String s) {
                for (String bad : DANGEROUS) {
                    if (s.toLowerCase().contains(bad.toLowerCase())) {
                        return new Validation(false,
                                "参数 " + e.getKey() + " 含危险模式: " + bad);
                    }
                }
            }
        }
        return Validation.ok();
    }
}
