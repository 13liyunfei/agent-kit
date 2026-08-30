package com.codereview.kit.struct;

import com.codereview.kit.ChatModel;
import com.codereview.kit.session.ChatMessage;
import com.codereview.kit.session.ChatSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 结构化输出（类型安全契约 + 自动校验重试 + <b>可降级</b>）。
 *
 * <p>把"让模型返回 JSON"这件脆弱的事收敛成一次调用：推导 schema → 约束输出 →
 * 解析校验 → 失败带反馈重试 → 仍失败则返回 {@link StructuredResult} 交回调用方降级。
 *
 * <h3>三种用法，覆盖不同容错诉求</h3>
 * <ol>
 *   <li>{@link #chat(String, Class, int)} —— <b>推荐</b>。失败不抛异常，
 *       返回 {@link StructuredResult}，调用方可拿 {@code rawResponse} 回退文本解析。</li>
 *   <li>{@link #require(String, Class, int)} —— 结构化输出是<b>硬需求</b>时用，
 *       重试耗尽抛 {@link IllegalStateException}。</li>
 *   <li>{@link #chatWithSession(ChatSession, String, Class, int)} —— 需要<b>多轮记忆</b>时，
 *       结构化输出与 {@link ChatSession} 结合，本次对话自动写入会话。</li>
 * </ol>
 *
 * <h3>相比"手写提示词要求返回 JSON"多做了什么</h3>
 * <ul>
 *   <li><b>schema 由类型推导</b>（{@link JsonSchemas}），改 DTO 即同步，不会脱节；</li>
 *   <li><b>重试带反馈</b>——把上一次的错误输出与失败原因回灌给模型，
 *       而不是原样重发同一个提示词（原样重发大概率得到同样的错误结果）；</li>
 *   <li><b>宽容解析</b>——自动剥离 Markdown 代码块围栏与前后闲聊文本。</li>
 * </ul>
 *
 * <p><b>设计原则：</b>结构化输出是增强而非必需。因此默认 API 不抛异常——
 * 增强失败时，调用方仍应能走没有它时本该走的通路。
 */
public class StructuredChatModel {

    private final ChatModel delegate;
    private final ObjectMapper mapper = new ObjectMapper();

    public StructuredChatModel(ChatModel delegate) {
        this.delegate = delegate;
    }

    // ------------------------------------------------------------------
    // 推荐用法：非抛出式
    // ------------------------------------------------------------------

    /**
     * 结构化对话（<b>失败不抛异常</b>）：schema 由 {@code type} 自动推导。
     *
     * @param prompt     提示词
     * @param type       目标类型（record / POJO，支持嵌套与 {@code List<T>}）
     * @param maxRetries 解析/校验失败最大重试次数
     * @return 结构化结果；失败时 {@code ok=false} 并保留原始输出供降级
     */
    public <T> StructuredResult<T> chat(String prompt, Class<T> type, int maxRetries) {
        return chat(prompt, JsonSchemas.fromType(type), type, maxRetries, null);
    }

    /**
     * 结构化对话（携带多轮会话记忆）。
     *
     * <p>会话历史会拼进提示词；成功后本次的用户提问与模型回复<b>自动写回会话</b>，
     * 因此下一轮能引用上一轮的结构化结论。
     *
     * @param session    多轮会话（为 null 时等价于 {@link #chat(String, Class, int)}）
     * @param prompt     本轮提示词
     * @param type       目标类型
     * @param maxRetries 最大重试次数
     * @return 结构化结果
     */
    public <T> StructuredResult<T> chatWithSession(ChatSession session, String prompt,
                                                   Class<T> type, int maxRetries) {
        return chat(prompt, JsonSchemas.fromType(type), type, maxRetries, session);
    }

    // ------------------------------------------------------------------
    // 硬需求用法：抛出式
    // ------------------------------------------------------------------

    /**
     * 结构化对话，重试耗尽抛异常。仅在结构化输出是<b>硬需求</b>时使用
     * （如必须得到分类结果才能继续的流程）；一般场景请用 {@link #chat}。
     *
     * @throws IllegalStateException 重试耗尽仍未通过
     */
    public <T> T require(String prompt, Class<T> type, int maxRetries) {
        StructuredResult<T> result = chat(prompt, type, maxRetries);
        if (result.ok()) {
            return result.value();
        }
        throw new IllegalStateException(
                "结构化输出解析失败（重试 " + maxRetries + " 次后放弃）：" + result.error());
    }

    // ------------------------------------------------------------------
    // 兼容旧 API（手工 schema）
    // ------------------------------------------------------------------

    /**
     * 结构化对话：输出必须是满足 {@code schema} 的 JSON 对象。
     *
     * @deprecated 推荐改用 {@link #chat(String, Class, int)} 让 schema 由类型推导，
     *             避免手写 schema 与 DTO 脱节。本方法保留以保证二进制兼容。
     */
    @Deprecated
    public <T> T chatStructured(String prompt, Map<String, Object> schema, Class<T> type, int maxRetries) {
        StructuredResult<T> result = chat(prompt, schema, type, maxRetries, null);
        if (result.ok()) {
            return result.value();
        }
        throw new IllegalStateException("结构化输出解析失败（重试 " + maxRetries + " 次后放弃）");
    }

    /** 便捷构造：常用字段的 object schema。 */
    public static Map<String, Object> objectSchema(Map<String, Object> properties, String... required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of(required));
        return schema;
    }

    /** 便捷构造：string / integer / number / boolean 字段。 */
    public static Map<String, Object> field(String type) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("type", type);
        return f;
    }

    // ------------------------------------------------------------------
    // 核心实现
    // ------------------------------------------------------------------

    private <T> StructuredResult<T> chat(String prompt,
                                         Map<String, Object> schema,
                                         Class<T> type,
                                         int maxRetries,
                                         ChatSession session) {
        String schemaJson = toSchemaJson(schema);
        String base = (session != null ? session.toPrompt(prompt) : prompt)
                + "\n\n只输出满足以下 JSON Schema 的 JSON 对象（不要 Markdown 代码块，不要解释）：\n"
                + schemaJson;

        String currentPrompt = base;
        String lastError = null;
        String lastRaw = null;

        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            String resp;
            try {
                resp = delegate.chat(currentPrompt);
            } catch (RuntimeException e) {
                // 模型调用失败不是格式问题——直接向上抛，交由调用方的降级链处理
                throw e;
            }
            lastRaw = resp;
            try {
                T parsed = parseLenient(resp, type);
                if (parsed != null) {
                    if (session != null) {
                        session.add(ChatMessage.user(prompt));
                        session.add(ChatMessage.assistant(resp));
                    }
                    return StructuredResult.success(parsed, resp, attempt);
                }
                lastError = "输出中没有可解析的目标 JSON 对象";
            } catch (Exception ex) {
                lastError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            }
            // 重试前把上一次的错误输出与原因回灌给模型，否则原样重发大概率得到同样结果
            currentPrompt = base
                    + "\n\n你上一次的输出无法解析，失败原因：" + lastError
                    + "\n上一次的输出是：\n" + truncate(resp, 800)
                    + "\n请严格只输出符合上述 JSON Schema 的 JSON 对象，不要任何额外文字。";
        }
        return StructuredResult.failure(lastRaw, maxRetries + 1, lastError);
    }

    private String toSchemaJson(Map<String, Object> schema) {
        try {
            return mapper.writeValueAsString(schema);
        } catch (Exception e) {
            throw new IllegalArgumentException("非法 schema", e);
        }
    }

    /** 宽容解析：剥离 Markdown 围栏与前后闲聊，再定位 JSON 对象。 */
    private <T> T parseLenient(String text, Class<T> type) throws Exception {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = text.trim();
        // 剥离 ```json / ``` 围栏
        if (t.contains("```")) {
            int start = t.indexOf("```");
            int afterFence = t.indexOf('\n', start);
            int end = t.lastIndexOf("```");
            if (afterFence > 0 && end > afterFence) {
                t = t.substring(afterFence + 1, end).trim();
            }
        }
        int s = t.indexOf('{');
        int e = t.lastIndexOf('}');
        if (s < 0 || e <= s) {
            return null;
        }
        JsonNode node = mapper.readTree(t.substring(s, e + 1));
        return mapper.treeToValue(node, type);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...(截断)";
    }
}
