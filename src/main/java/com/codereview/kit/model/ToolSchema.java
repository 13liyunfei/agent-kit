package com.codereview.kit.model;

import com.codereview.kit.toolcalling.AgentTool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具参数 JSON Schema（原生函数调用协议用）。
 *
 * <p>从 {@link AgentTool#parameterSchema()} 的简易文本形态（{"a":"string"}）
 * 提升为完整 JSON Schema（type=object / properties / required），供
 * OpenAI 等供应商的原生 tools 参数使用。
 */
public record ToolSchema(String name, String description, Map<String, Object> parameters) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 从简易文本 schema 构造（{"path":"string","lines":"integer"}）。 */
    public static ToolSchema from(AgentTool tool) {
        Map<String, Object> props = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        Map<String, String> parsed = parseSimple(tool.parameterSchema());
        parsed.forEach((k, v) -> {
            props.put(k, Map.of("type", normalizeType(v)));
            required.add(k);
        });
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", props);
        params.put("required", required);
        return new ToolSchema(tool.name(), tool.description(), params);
    }

    /** 从完整 JSON Schema 字符串构造（用户自定义高级 schema 时用）。 */
    public static ToolSchema fromJson(AgentTool tool, String jsonSchema) {
        try {
            Map<String, Object> params = MAPPER.readValue(jsonSchema, Map.class);
            return new ToolSchema(tool.name(), tool.description(), params);
        } catch (Exception e) {
            throw new IllegalArgumentException("非法 JSON Schema: " + e.getMessage(), e);
        }
    }

    /** 转成 OpenAI tools 参数条目。 */
    public Map<String, Object> toOpenAiTool() {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", name);
        fn.put("description", description);
        fn.put("parameters", parameters);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "function");
        out.put("function", fn);
        return out;
    }

    /** 转成 prompt 文本清单（回退模式用）。 */
    public String toPromptText() {
        return "- " + name + ": " + description + " 参数: " + parameters;
    }

    /** 解析简易 schema：字段名 -> 类型名。非法输入按空 map 处理（不阻断注册）。 */
    private static Map<String, String> parseSimple(String text) {
        Map<String, String> out = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return out;
        }
        try {
            JsonNode node = MAPPER.readTree(text);
            if (node.isObject()) {
                node.properties().forEach(e -> out.put(e.getKey(),
                        e.getValue().isTextual() ? e.getValue().asText()
                                : e.getValue().path("type").asText("string")));
            }
        } catch (Exception ignored) {
            // 无法解析则空 schema（模型可能不调用该工具，但不炸流程）
        }
        return out;
    }

    private static String normalizeType(String t) {
        if (t == null) {
            return "string";
        }
        return switch (t.toLowerCase()) {
            case "int", "integer", "long", "number" -> "number";
            case "bool", "boolean" -> "boolean";
            case "object" -> "object";
            case "array", "list" -> "array";
            default -> "string";
        };
    }
}
