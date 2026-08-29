package com.codereview.kit.rag;

import com.codereview.kit.ChatModel;

import java.util.List;

/**
 * 检索增强对话模型：chat 前自动检索相关知识注入 prompt（RAG 闭环）。
 *
 * <p>把任意 {@link ChatModel} + {@link Retriever} 组合成「先检索、后生成」的模型，
 * 使用方无需感知检索细节。
 */
public class RagChatModel implements ChatModel {

    private final ChatModel delegate;
    private final Retriever retriever;
    private final int topK;
    private final String systemInstruction; // 可空

    public RagChatModel(ChatModel delegate, Retriever retriever, int topK, String systemInstruction) {
        this.delegate = delegate;
        this.retriever = retriever;
        this.topK = Math.max(1, topK);
        this.systemInstruction = systemInstruction;
    }

    @Override
    public String chat(String prompt) {
        List<Document> hits = retriever.retrieve(prompt, topK);
        StringBuilder sb = new StringBuilder();
        if (systemInstruction != null && !systemInstruction.isBlank()) {
            sb.append(systemInstruction).append('\n');
        }
        sb.append("以下是相关知识片段（可能不完全相关，请以事实为准）：\n");
        if (hits.isEmpty()) {
            sb.append("（无相关知识命中）\n");
        } else {
            int i = 1;
            for (Document d : hits) {
                sb.append('[').append(i++).append("] ").append(d.text()).append('\n');
            }
        }
        sb.append("\n用户问题：").append(prompt);
        return delegate.chat(sb.toString());
    }
}
