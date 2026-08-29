package com.codereview.kit.checkpoint;

import java.time.Instant;
import java.util.Map;

/**
 * 执行检查点：把一次 Agent 运行的可恢复状态落下来（供崩溃恢复 / 断点续跑）。
 *
 * @param runId     运行标识（同一次执行重跑时复用）
 * @param state     可恢复状态（键值，如已完成任务 / 中间结论）
 * @param createdAt 创建时间
 */
public record Checkpoint(String runId, Map<String, Object> state, Instant createdAt) {
}
