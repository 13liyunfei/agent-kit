package com.codereview.kit.agent;

import com.codereview.kit.session.ChatMessage;
import com.codereview.kit.toolcalling.ToolRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 执行上下文：当前任务 + 对话历史 + 共享状态 + 工具（可空）。
 */
public class AgentContext {

    private final String task;
    private final List<ChatMessage> history;
    private final Map<String, Object> state;
    private final ToolRegistry tools;

    public AgentContext(String task, List<ChatMessage> history, Map<String, Object> state, ToolRegistry tools) {
        this.task = task;
        this.history = new ArrayList<>(history == null ? List.of() : history);
        this.state = state == null ? new java.util.LinkedHashMap<>() : state;
        this.tools = tools;
    }

    public String task() {
        return task;
    }

    public List<ChatMessage> history() {
        return List.copyOf(history);
    }

    public AgentContext addHistory(ChatMessage msg) {
        history.add(msg);
        return this;
    }

    public Map<String, Object> state() {
        return state;
    }

    public ToolRegistry tools() {
        return tools;
    }
}
