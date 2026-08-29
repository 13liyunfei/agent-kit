package com.codereview.kit.model;
import com.codereview.kit.model.ToolSchema;

import java.util.List;

/**
 * 支持原生函数调用（tool calling）的模型边界。
 *
 * <p>{@code ChatModel} 是 kit 的唯一模型边界，但其形态只表达「一段文本进、一段文本出」，
 * 无法承载原生 tools 参数与工具结果 role 消息。本接口是它的**增强边界**：实现了它的
 * 适配器（如 {@link OpenAiChatModel}）可被 {@code toolcalling.NativeToolCallingLoop} 使用，
 * 获得可靠的并行函数调用；未实现的模型自动回退到 prompt-JSON 决策模式。
 */
public interface NativeChatModel {

    /** 一次原生对话（消息 + 工具清单 + 选项）。 */
    NativeResult chat(List<NativeMessage> messages, List<ToolSchema> tools, NativeOptions options);
}
