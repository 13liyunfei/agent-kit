package com.codereview.kit.memory;

import com.codereview.kit.ChatModel;
import com.codereview.kit.session.ChatMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * 会话记忆：短期消息窗口 + 溢出自动摘要（summarization memory）。
 *
 * <p>消息数超过上限时，把最老的半段交给摘要模型压缩为一条 summary 消息，
 * 长期信息不丢、短期窗口不爆。未提供摘要模型时退化为简单裁剪。
 */
public class ConversationMemory {

    private final int maxMessages;
    private final ChatModel summarizer; // 可空
    private final List<ChatMessage> messages = new ArrayList<>();

    public ConversationMemory(int maxMessages, ChatModel summarizer) {
        this.maxMessages = Math.max(4, maxMessages);
        this.summarizer = summarizer;
    }

    public ConversationMemory add(ChatMessage msg) {
        messages.add(msg);
        compact();
        return this;
    }

    public List<ChatMessage> messages() {
        return List.copyOf(messages);
    }

    /** 拼接为给模型的完整 prompt。 */
    public String toPrompt(String latestUserPrompt) {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            sb.append(m.role()).append(": ").append(m.content()).append("\n");
        }
        if (latestUserPrompt != null && !latestUserPrompt.isBlank()) {
            sb.append("user: ").append(latestUserPrompt);
        }
        return sb.toString();
    }

    private void compact() {
        if (messages.size() <= maxMessages) {
            return;
        }
        int half = messages.size() / 2;
        List<ChatMessage> oldest = new ArrayList<>(messages.subList(0, half));
        messages.subList(0, half).clear();
        if (summarizer != null && !oldest.isEmpty()) {
            String summary = summarize(oldest);
            if (summary != null && !summary.isBlank()) {
                messages.add(0, ChatMessage.system("历史摘要：" + summary));
                return;
            }
        }
        // 无摘要模型或摘要失败：保留最近一条旧消息作为上下文锚点
        messages.add(0, oldest.get(oldest.size() - 1));
    }

    private String summarize(List<ChatMessage> oldest) {
        StringBuilder sb = new StringBuilder("请把以下对话压缩成 2-3 句要点摘要（保留事实与决策）：\n");
        oldest.forEach(m -> sb.append(m.role()).append(": ").append(m.content()).append("\n"));
        try {
            return summarizer.chat(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
