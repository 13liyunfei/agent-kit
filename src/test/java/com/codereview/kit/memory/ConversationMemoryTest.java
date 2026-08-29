package com.codereview.kit.memory;

import com.codereview.kit.session.ChatMessage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 会话记忆：溢出触发摘要压缩（summarization memory）。
 */
class ConversationMemoryTest {

    @Test
    void overflowTriggersSummarization() {
        AtomicReference<String> lastPrompt = new AtomicReference<>();
        ConversationMemory mem = new ConversationMemory(4, prompt -> {
            lastPrompt.set(prompt);
            return "摘要：用户想要构建一个 Java Agent 框架，已选定组件方案。";
        });
        mem.add(ChatMessage.user("问题1"));
        mem.add(ChatMessage.assistant("回答1"));
        mem.add(ChatMessage.user("问题2"));
        mem.add(ChatMessage.assistant("回答2"));
        mem.add(ChatMessage.user("问题3")); // 触发压缩（max=4，第 5 条溢出）

        assertTrue(lastPrompt.get() != null, "溢出时应调用摘要模型");
        assertTrue(mem.messages().stream().anyMatch(m -> m.content().contains("历史摘要")));
        assertTrue(mem.messages().size() <= 4);
    }

    @Test
    void withoutSummarizerFallsBackToAnchor() {
        ConversationMemory mem = new ConversationMemory(4, null);
        for (int i = 0; i < 6; i++) {
            mem.add(ChatMessage.user("消息" + i));
        }
        assertTrue(mem.messages().size() <= 4);
    }

    @Test
    void toPromptJoinsHistoryAndLatest() {
        ConversationMemory mem = new ConversationMemory(10, null);
        mem.add(ChatMessage.user("你好"));
        String p = mem.toPrompt("最新问题");
        assertTrue(p.contains("user: 你好"));
        assertTrue(p.contains("user: 最新问题"));
    }
}
