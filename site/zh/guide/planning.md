# 任务拆解与 DAG 执行

复杂目标很少能靠一次 prompt 解决。把它拆成带依赖的子任务，再让就绪的任务并行跑起来，才是从演示走向系统的关键。

## 组件

| 类 | 职责 |
|----|------|
| `TaskPlanner` | 让模型把目标拆成带依赖的子任务 |
| `TaskPlan` | 拆解出的 DAG，构造期即校验 |
| `DagExecutor` | 按依赖拓扑并行执行 |

## 用法

```java
TaskPlan plan = new TaskPlanner(model)
        .plan("审查 PR #42 的正确性与安全性",
              List.of("Logic", "Security", "Performance"));

Map<String, DagExecutor.TaskResult> results = new DagExecutor(executor)
        .execute(plan, node -> runMyAgent(node.assignee(), node.description()));

results.forEach((id, r) -> {
    if (r.failed()) log.warn("{} 被跳过或失败：{}", id, r.error());
    else process(id, r.output());
});
```

## 拆解产物

每个节点包含 id、描述、负责人与依赖：

```json
{
  "tasks": [
    { "id": "t1", "description": "检查控制流", "assignee": "Logic",    "dependsOn": [] },
    { "id": "t2", "description": "检查注入风险", "assignee": "Security", "dependsOn": [] },
    { "id": "t3", "description": "汇总发现",     "assignee": "Reporter", "dependsOn": ["t1", "t2"] }
  ]
}
```

## 构造期校验

`TaskPlan` 不允许以非法状态存在：

- id 重复 → 拒绝
- 依赖指向不存在的 id → 拒绝
- 存在环 → 用 Kahn 算法检测并拒绝

错误在计划构建时就暴露，而不是执行到第三层才炸。

## 执行语义

- 依赖已满足的节点立即并行启动
- 依赖全部完成后节点自动开始
- **上游失败则下游跳过**，其他分支不受影响

## 降级策略

规划是增强项，不是必需项。模型输出无法解析时，`TaskPlanner` 降级为单任务直通计划，目标照旧执行而不是失败。参考落地项目 code-review-agent 用的就是同一原则：规划可以让结果更好，但绝不能让结果更糟。
