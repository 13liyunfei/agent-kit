package com.codereview.kit.agent;

import com.codereview.kit.ChatModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Function;

/**
 * Supervisor Agent（路由式多 Agent 编排）：LLM 决策「派给谁、干什么、是否完成」，
 * 循环派发到 worker 直到目标达成（supervisor 路由模式）。
 *
 * <pre>
 * String result = new SupervisorAgent(model)
 *         .run("修复登录超时 bug", List.of(new Worker("security", "安全审查")),
 *              workerName -> runWorker(workerName), 5);
 * </pre>
 */
public class SupervisorAgent {

    private static final Logger log = LoggerFactory.getLogger(SupervisorAgent.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ChatModel model;

    public SupervisorAgent(ChatModel model) {
        this.model = model;
    }

    /** Worker 描述（决策上下文）。 */
    public record Worker(String name, String description) {
    }

    /**
     * @param goal         总体目标
     * @param workers      worker 清单
     * @param runWorker    执行单个 worker（返回其结论文本）
     * @param maxIterations 最大派发轮数
     */
    public String run(String goal, List<Worker> workers, Function<String, String> runWorker, int maxIterations) {
        StringBuilder transcript = new StringBuilder();
        for (int i = 1; i <= maxIterations; i++) {
            String decision = model.chat(buildPrompt(goal, workers, transcript));
            JsonNode json = extractJson(decision);
            if (json == null || (!json.hasNonNull("worker") && !json.path("done").asBoolean(false))) {
                log.warn("[Supervisor] 第 {} 轮输出非法 JSON，按完成返回", i);
                return "已完成（supervisor 决策异常）：" + decision;
            }
            if (json.path("done").asBoolean(false)) {
                String summary = json.path("answer").asText("目标已完成");
                log.info("[Supervisor] 目标完成，共派发 {} 轮", i);
                return summary;
            }
            String worker = json.path("worker").asText();
            String task = json.path("task").asText();
            boolean known = workers.stream().anyMatch(w -> w.name().equals(worker));
            String out;
            if (!known) {
                out = "错误：worker " + worker + " 不存在";
            } else {
                try {
                    out = runWorker.apply(worker + "：" + task);
                } catch (Exception e) {
                    out = "worker 执行异常: " + e.getMessage();
                }
            }
            transcript.append("派发 ").append(worker).append(" 任务：").append(task)
                    .append("\n结果：").append(out).append('\n');
        }
        return "达到最大派发轮数（" + maxIterations + "），任务未收敛";
    }

    private String buildPrompt(String goal, List<Worker> workers, StringBuilder transcript) {
        StringBuilder sb = new StringBuilder("你是任务调度 Supervisor。可用 worker：\n");
        workers.forEach(w -> sb.append("- ").append(w.name()).append(": ").append(w.description()).append('\n'));
        sb.append("\n目标：").append(goal).append('\n');
        if (!transcript.isEmpty()) {
            sb.append("\n已执行轨迹：\n").append(transcript);
        }
        sb.append("\n\n请输出 JSON 决策：{\"worker\":\"worker名\",\"task\":\"派发子任务\",\"done\":false}；"
                + "若目标已完成输出 {\"done\":true,\"answer\":\"最终结论\"}。");
        return sb.toString();
    }

    private static JsonNode extractJson(String text) {
        try {
            int s = text.indexOf('{');
            int e = text.lastIndexOf('}');
            return (s < 0 || e <= s) ? null : MAPPER.readTree(text.substring(s, e + 1));
        } catch (Exception ex) {
            return null;
        }
    }
}
