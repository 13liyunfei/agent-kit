package com.codereview.kit.struct;

import com.codereview.kit.ChatModel;
import com.codereview.kit.session.ChatSession;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构化输出增强能力：类型推导 schema、可降级结果、重试反馈、会话记忆。
 *
 * <p>这些能力是为满足真实落地场景（如 code-review-agent 的审查结果输出：
 * 嵌套 DTO + 按 PR 隔离的短期记忆 + 失败须回退文本解析）而补齐的。
 */
class StructuredOutputEnhancementTest {

    // ---------------- 测试用领域类型 ----------------

    public enum Severity { BLOCKER, MAJOR, MINOR, INFO }

    /** 嵌套结构：结果里含 List，元素含枚举——手写 schema 极易与类型脱节。 */
    public record Finding(String file, int line, String title, Severity severity) {
    }

    public record ReviewResult(List<Finding> findings, String summary) {
    }

    public record Decision(String action, int level) {
    }

    /** 记录每次收到的 prompt，用于验证重试是否真的带了反馈。 */
    static class RecordingModel implements ChatModel {
        final List<String> prompts = new ArrayList<>();
        private final List<String> script;

        RecordingModel(String... script) {
            this.script = List.of(script);
        }

        @Override public String chat(String prompt) {
            prompts.add(prompt);
            return script.get(Math.min(prompts.size() - 1, script.size() - 1));
        }
    }

    // ---------------- 1. 类型推导 schema ----------------

    @Test
    void derivesSchemaForNestedTypeWithListAndEnum() {
        Map<String, Object> schema = JsonSchemas.fromType(ReviewResult.class);

        assertEquals("object", schema.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("findings"));
        assertTrue(props.containsKey("summary"));

        @SuppressWarnings("unchecked")
        Map<String, Object> findings = (Map<String, Object>) props.get("findings");
        assertEquals("array", findings.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> item = (Map<String, Object>) findings.get("items");
        assertEquals("object", item.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> itemProps = (Map<String, Object>) item.get("properties");
        assertEquals("string", ((Map<?, ?>) itemProps.get("title")).get("type"));
        assertEquals("integer", ((Map<?, ?>) itemProps.get("line")).get("type"));

        // 枚举展开为取值列表，显著减少模型瞎编取值
        Map<?, ?> severity = (Map<?, ?>) itemProps.get("severity");
        assertEquals("string", severity.get("type"));
        assertEquals(List.of("BLOCKER", "MAJOR", "MINOR", "INFO"), severity.get("enum"));
    }

    @Test
    void derivesSchemaForFlatRecord() {
        Map<String, Object> schema = JsonSchemas.fromType(Decision.class);
        assertEquals("object", schema.get("type"));
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.get("properties");
        assertEquals("string", ((Map<?, ?>) props.get("action")).get("type"));
        assertEquals("integer", ((Map<?, ?>) props.get("level")).get("type"));
    }

    // ---------------- 2. 非抛出式结果 + 降级 ----------------

    @Test
    void returnsFailureResultInsteadOfThrowing() {
        StructuredChatModel model = new StructuredChatModel(
                prompt -> "抱歉，我无法完成这个请求。");

        StructuredResult<Decision> result = model.chat("决策", Decision.class, 2);

        assertFalse(result.ok());
        assertNull(result.value());
        assertNotNull(result.error());
        // 关键：原始输出被保留，调用方可以回退到文本解析
        assertEquals("抱歉，我无法完成这个请求。", result.rawResponse());
    }

    @Test
    void failureResultSupportsRawFallbackParsing() {
        StructuredChatModel model = new StructuredChatModel(
                prompt -> "RAW:action=approve");

        StructuredResult<Decision> result = model.chat("决策", Decision.class, 1);

        // 结构化失败，但调用方能用原始输出走自己的兜底解析
        String fallback = result.onFailureParseRaw(
                raw -> raw.contains("approve") ? "approve" : null, "unknown");
        assertEquals("approve", fallback);
    }

    @Test
    void orElseReturnsFallbackOnFailure() {
        StructuredChatModel model = new StructuredChatModel(prompt -> "not json at all");
        Decision fallback = new Decision("unknown", 0);
        assertEquals(fallback, model.chat("决策", Decision.class, 1).orElse(fallback));
    }

    @Test
    void requireThrowsWhenStructuredOutputIsMandatory() {
        StructuredChatModel model = new StructuredChatModel(prompt -> "nope");
        assertThrows(IllegalStateException.class,
                () -> model.require("决策", Decision.class, 1));
    }

    // ---------------- 3. 重试带反馈 ----------------

    @Test
    void retryFeedsPreviousBadOutputBackToModel() {
        RecordingModel llm = new RecordingModel("I cannot do that", "{\"action\":\"approve\",\"level\":2}");
        StructuredChatModel model = new StructuredChatModel(llm);

        StructuredResult<Decision> result = model.chat("决策", Decision.class, 2);

        assertTrue(result.ok());
        assertEquals("approve", result.value().action());
        assertEquals(2, llm.prompts.size());
        assertTrue(result.retried());

        // 第二次调用的提示词必须包含上一次的错误输出与失败原因
        String secondPrompt = llm.prompts.get(1);
        assertTrue(secondPrompt.contains("I cannot do that"),
                "重试时应把上一次的错误输出回灌给模型");
        assertTrue(secondPrompt.contains("无法解析"),
                "重试时应把失败原因告诉模型");
    }

    @Test
    void stripsMarkdownFence() {
        StructuredChatModel model = new StructuredChatModel(
                prompt -> "好的，结果如下：\n```json\n{\"action\":\"reject\",\"level\":5}\n```\n希望有帮助！");

        StructuredResult<Decision> result = model.chat("决策", Decision.class, 1);

        assertTrue(result.ok());
        assertEquals("reject", result.value().action());
        assertEquals(5, result.value().level());
    }

    // ---------------- 4. 嵌套类型解析 ----------------

    @Test
    void parsesNestedStructureWithListAndEnum() {
        String json = """
                {"findings":[{"file":"A.java","line":10,"title":"NPE","severity":"BLOCKER"},
                             {"file":"B.java","line":20,"title":"Style","severity":"INFO"}],
                 "summary":"2 issues"}""";
        StructuredChatModel model = new StructuredChatModel(prompt -> json);

        StructuredResult<ReviewResult> result = model.chat("审查", ReviewResult.class, 1);

        assertTrue(result.ok());
        assertEquals("2 issues", result.value().summary());
        assertEquals(2, result.value().findings().size());
        assertEquals("A.java", result.value().findings().get(0).file());
        assertEquals(Severity.BLOCKER, result.value().findings().get(0).severity());
    }

    // ---------------- 5. 会话记忆集成 ----------------

    @Test
    void sessionRecordsExchangeAfterSuccess() {
        ChatSession session = new ChatSession(20, 4000);
        session.add(com.codereview.kit.session.ChatMessage.system("你是代码审查助手"));
        int before = session.messages().size();

        StructuredChatModel model = new StructuredChatModel(
                prompt -> "{\"findings\":[],\"summary\":\"无问题\"}");

        StructuredResult<ReviewResult> result = model.chatWithSession(session, "审查 PR#1", ReviewResult.class, 1);

        assertTrue(result.ok());
        // 成功后本轮 user + assistant 自动写回会话
        assertEquals(before + 2, session.messages().size());
        assertTrue(session.messages().stream().anyMatch(m -> "assistant".equals(m.role())));
    }

    @Test
    void sessionHistoryIsIncludedInPrompt() {
        ChatSession session = new ChatSession(20, 4000);
        session.add(com.codereview.kit.session.ChatMessage.user("上一轮的结论要记住"));

        RecordingModel llm = new RecordingModel("{\"findings\":[],\"summary\":\"ok\"}");
        StructuredChatModel model = new StructuredChatModel(llm);

        model.chatWithSession(session, "本轮", ReviewResult.class, 1);

        assertTrue(llm.prompts.get(0).contains("上一轮的结论要记住"),
                "会话历史应拼进提示词");
    }

    @Test
    void failureDoesNotPolluteSession() {
        ChatSession session = new ChatSession(20, 4000);
        int before = session.messages().size();

        StructuredChatModel model = new StructuredChatModel(prompt -> "无法输出 JSON");

        assertFalse(model.chatWithSession(session, "审查", ReviewResult.class, 1).ok());
        assertEquals(before, session.messages().size(), "解析失败不应把脏输出写入会话记忆");
    }

    // ---------------- 6. 模型异常直接上抛 ----------------

    @Test
    void modelFailurePropagatesNotSwallowed() {
        StructuredChatModel model = new StructuredChatModel(prompt -> {
            throw new IllegalStateException("网关不可用");
        });
        // 模型调用失败不是格式问题，应由调用方的降级链处理，而不是被重试吞掉
        assertThrows(IllegalStateException.class,
                () -> model.chat("决策", Decision.class, 3));
    }
}
