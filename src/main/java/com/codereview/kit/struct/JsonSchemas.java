package com.codereview.kit.struct;

import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 Java 类型推导 JSON Schema（免手写 schema）。
 *
 * <p><b>为什么需要它：</b>早期 API 要求调用方手工拼 {@code Map<String,Object>} schema。
 * 一旦目标类型出现<b>嵌套</b>（如审查结果里含 {@code List<Finding>}），
 * 手写 schema 既冗长又极易与类型脱节——改了 DTO 忘了改 schema，
 * 而这类不一致往往要到线上才暴露。
 *
 * <p>本类用 Jackson 的 {@code introspect} 遍历属性，因此：
 * <ul>
 *   <li>嵌套 record / POJO 自动展开为 {@code object}；</li>
 *   <li>{@code List<T>}、数组展开为 {@code array + items}；</li>
 *   <li>枚举展开为 {@code enum} 取值列表（显著减少模型瞎编取值）；</li>
 *   <li>基础类型映射为 string / integer / number / boolean。</li>
 * </ul>
 *
 * <p><b>惯例：</b>属性是否必填由 {@code required} 列表承载——
 * 目前把所有属性都列为必填，以最大化模型输出字段的完整性；
 * 调用方拿到对象后再做业务层的空值兜底（这也是更稳妥的做法：
 * 信任边界上的容错放在自己代码里，而不是指望模型）。
 */
public final class JsonSchemas {

    /** 递归深度上限：防御类型自我引用导致的无限展开。 */
    private static final int MAX_DEPTH = 6;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonSchemas() {
    }

    /**
     * 从目标类型推导 JSON Schema。
     *
     * @param type 目标类型（record / POJO）
     * @return JSON Schema（可直接序列化进提示词）
     */
    public static Map<String, Object> fromType(Class<?> type) {
        if (type == null) {
            throw new IllegalArgumentException("type 不能为 null");
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        fill(type, MAPPER.constructType(type), schema, 0);
        return schema;
    }

    /** 递归填充 schema（同时处理基础类型、枚举、集合、嵌套对象）。 */
    private static void fill(Class<?> raw, JavaType javaType, Map<String, Object> out, int depth) {
        String simple = simpleTypeOf(raw);
        if (simple != null) {
            out.put("type", simple);
            return;
        }
        if (raw.isEnum()) {
            out.put("type", "string");
            List<String> values = new ArrayList<>();
            for (Object c : raw.getEnumConstants()) {
                values.add(((Enum<?>) c).name());
            }
            out.put("enum", values);
            return;
        }
        if (depth >= MAX_DEPTH) {
            // 到达深度上限：退化为 object，避免自我引用类型无限展开
            out.put("type", "object");
            return;
        }
        if (raw.isArray()) {
            out.put("type", "array");
            Map<String, Object> items = new LinkedHashMap<>();
            fill(raw.getComponentType(), MAPPER.constructType(raw.getComponentType()), items, depth + 1);
            out.put("items", items);
            return;
        }
        if (Collection.class.isAssignableFrom(raw)) {
            out.put("type", "array");
            Map<String, Object> items = new LinkedHashMap<>();
            JavaType element = javaType.getContentType();
            Class<?> elementRaw = element == null ? Object.class : element.getRawClass();
            fill(elementRaw, element == null ? MAPPER.constructType(Object.class) : element, items, depth + 1);
            out.put("items", items);
            return;
        }
        if (Map.class.isAssignableFrom(raw)) {
            out.put("type", "object");
            return;
        }

        // 普通 bean / record：展开属性
        out.put("type", "object");
        BeanDescription desc = MAPPER.getSerializationConfig().introspect(javaType);
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (BeanPropertyDefinition prop : desc.findProperties()) {
            JavaType propType = prop.getPrimaryType();
            if (propType == null) {
                continue;
            }
            Map<String, Object> propSchema = new LinkedHashMap<>();
            fill(propType.getRawClass(), propType, propSchema, depth + 1);
            properties.put(prop.getName(), propSchema);
            required.add(prop.getName());
        }
        out.put("properties", properties);
        out.put("required", required);
    }

    /** 基础类型映射；非基础类型返回 null。 */
    private static String simpleTypeOf(Class<?> type) {
        if (type == null) {
            return null;
        }
        if (type == String.class || type == Character.class || type == char.class
                || CharSequence.class.isAssignableFrom(type)) {
            return "string";
        }
        if (type == Integer.class || type == int.class
                || type == Long.class || type == long.class
                || type == Short.class || type == short.class
                || type == Byte.class || type == byte.class
                || type == BigInteger.class) {
            return "integer";
        }
        if (type == Double.class || type == double.class
                || type == Float.class || type == float.class
                || type == BigDecimal.class
                || Number.class == type) {
            return "number";
        }
        if (type == Boolean.class || type == boolean.class) {
            return "boolean";
        }
        return null;
    }
}
