package com.codereview.kit.model;

import java.util.List;

/**
 * 原生函数调用协议消息（对齐 OpenAI chat.completions 消息形态）。
 *
 * <p>相比 {@code session.ChatMessage} 增加 assistant 的 tool_calls 与 tool 的 tool_call_id，
 * 是 {@link NativeChatModel} 对话单元。普通场景用静态工厂即可。
 *
 * @param role        system / user / assistant / tool
 * @param content     文本内容（tool 消息可为空，工具结果放 content）
 * @param toolCallId  role=tool 时回填的调用 id（可空）
 * @param toolCalls   role=assistant 时的工具调用列表（可空）
 * @param name        工具名（兼容旧协议，可空）
 */
public record NativeMessage(String role, String content, String toolCallId,
                            List<NativeToolCall> toolCalls, String name) {

    public enum Role {
        SYSTEM("system"), USER("user"), ASSISTANT("assistant"), TOOL("tool");

        public final String wire;
        Role(String wire) {
            this.wire = wire;
        }
    }

    public static NativeMessage system(String content) {
        return new NativeMessage(Role.SYSTEM.wire, content, null, null, null);
    }

    public static NativeMessage user(String content) {
        return new NativeMessage(Role.USER.wire, content, null, null, null);
    }

    public static NativeMessage assistant(String content, List<NativeToolCall> toolCalls) {
        return new NativeMessage(Role.ASSISTANT.wire, content, null, toolCalls, null);
    }

    public static NativeMessage tool(String toolCallId, String content) {
        return new NativeMessage(Role.TOOL.wire, content, toolCallId, null, null);
    }
}
