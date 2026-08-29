package com.codereview.kit.agent;

/**
 * Agent 输出（协作协议）：完成或转交。
 */
public sealed interface AgentOutput permits AgentOutput.FinalAnswer, AgentOutput.Handoff {

    /** 完成任务：给出最终结论。 */
    record FinalAnswer(String content) implements AgentOutput {
    }

    /** 转交：把子任务交给另一个 Agent。 */
    record Handoff(String target, String task) implements AgentOutput {
    }
}
