package com.codereview.kit.agent;

import com.codereview.kit.session.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多 Agent 协作运行时：Agent 之间通过 Handoff 转交任务。
 *
 * <pre>
 * AgentRuntime runtime = new AgentRuntime(
 *         new Agent("reviewer", "你是代码审查员", ctx -> ...),
 *         new Agent("reporter", "你是报告整理员", ctx -> ...));
 * AgentRunResult r = runtime.start("reviewer", "审查 PR #42");
 * </pre>
 *
 * <p>护栏：最大转交次数（防 Agent 之间无限踢皮球）；未知目标 Agent 报错返回。
 */
public class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final Map<String, Agent> agents = new LinkedHashMap<>();
    private final int maxHops;

    public AgentRuntime(Agent... agents) {
        this(10, agents);
    }

    public AgentRuntime(int maxHops, Agent... agents) {
        this.maxHops = Math.max(1, maxHops);
        for (Agent a : agents) {
            this.agents.put(a.name(), a);
        }
    }

    public AgentRunResult start(String agentName, String task) {
        String current = agentName;
        String currentTask = task;
        List<String> trajectory = new ArrayList<>();
        List<ChatMessage> history = new ArrayList<>();

        for (int hop = 0; hop < maxHops; hop++) {
            Agent agent = agents.get(current);
            if (agent == null) {
                return new AgentRunResult(current, "错误：Agent " + current + " 不存在", List.copyOf(trajectory));
            }
            trajectory.add(current + ": " + currentTask);
            AgentContext ctx = new AgentContext(currentTask, history, new LinkedHashMap<>(), null);
            AgentOutput out;
            try {
                out = agent.handler().apply(ctx);
            } catch (Exception e) {
                log.warn("[AgentRuntime] {} 执行异常：{}", current, e.getMessage());
                return new AgentRunResult(current, "Agent " + current + " 执行异常: " + e.getMessage(),
                        List.copyOf(trajectory));
            }
            if (out instanceof AgentOutput.FinalAnswer fa) {
                return new AgentRunResult(current, fa.content(), List.copyOf(trajectory));
            }
            if (out instanceof AgentOutput.Handoff h) {
                log.info("[AgentRuntime] {} 转交任务给 {}：{}", current, h.target(), h.task());
                history.add(ChatMessage.user("（来自 " + current + " 的转交）" + currentTask));
                current = h.target();
                currentTask = h.task();
            }
        }
        return new AgentRunResult(current, "达到最大转交次数（" + maxHops + "），任务未收敛",
                List.copyOf(trajectory));
    }
}
