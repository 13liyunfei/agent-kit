package com.codereview.kit.agent;

import java.util.function.Function;

/**
 * 领域 Agent：一个可被 {@link AgentRuntime} 调度的执行单元。
 *
 * <p>handler 返回 {@link FinalAnswer}（完成任务）或 {@link Handoff}（转交其他 Agent），
 * 运行时负责消息传递与协作协议。
 *
 * @param name          Agent 唯一名（Handoff 目标引用）
 * @param systemPrompt  系统指令（进入对话上下文）
 * @param handler       执行逻辑（入上下文、出结果）
 */
public record Agent(String name, String systemPrompt, Function<AgentContext, AgentOutput> handler) {
}
