package com.codereview.kit.toolcalling;

import com.codereview.kit.model.ToolSchema;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 工具 schema 生成与参数校验：必填 / 危险模式拦截。
 */
class ToolSchemaValidatorTest {

    private static AgentTool tool(String name, String schema) {
        return new AgentTool() {
            @Override public String name() { return name; }
            @Override public String description() { return "test"; }
            @Override public String parameterSchema() { return schema; }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.ok("ok"); }
        };
    }

    @Test
    void simpleSchemaBecomesOpenAiTool() {
        ToolSchema schema = ToolSchema.from(tool("t", "{\"path\":\"string\",\"lines\":\"integer\"}"));
        Map<String, Object> openAi = schema.toOpenAiTool();
        assertEquals("function", openAi.get("type"));
        Map<?, ?> fn = (Map<?, ?>) openAi.get("function");
        assertEquals("t", fn.get("name"));
        Map<?, ?> params = (Map<?, ?>) fn.get("parameters");
        assertEquals("object", params.get("type"));
        assertTrue(((java.util.List<?>) params.get("required")).contains("path"));
        assertEquals("number", ((Map<?, ?>) ((Map<?, ?>) params.get("properties")).get("lines")).get("type"));
    }

    @Test
    void missingRequiredRejected() {
        ToolSchema schema = ToolSchema.from(tool("t", "{\"path\":\"string\"}"));
        assertTrue(ToolSchemaValidator.validate(schema, Map.of("path", "a")).valid());
        assertFalse(ToolSchemaValidator.validate(schema, Map.of()).valid());
    }

    @Test
    void dangerousPatternBlocked() {
        ToolSchema schema = ToolSchema.from(tool("t", "{\"cmd\":\"string\"}"));
        var v = ToolSchemaValidator.validate(schema, Map.of("cmd", "ls; rm -rf /"));
        assertFalse(v.valid());
        assertTrue(v.reason().contains("危险模式"));
    }

    @Test
    void guardedToolBlocksDangerousArgs() {
        AgentTool guarded = new GuardedTool(tool("t", "{\"path\":\"string\"}"));
        AgentTool.ToolResult r = guarded.execute(Map.of("path", "a; drop table users"));
        assertFalse(r.success());
        assertEquals("ok", guarded.execute(Map.of("path", "safe.txt")).output());
    }
}
