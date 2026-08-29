# 快速开始

## 1. 引入依赖

```xml
<dependency>
    <groupId>io.github.13liyunfei</groupId>
    <artifactId>agent-kit</artifactId>
    <version>0.1.0</version>
</dependency>
```

需要 Java 17 及以上。

## 2. 提供模型

agent-kit 不会自己调用 LLM，需要你提供一个 `ChatModel`：

```java
ChatModel model = prompt -> myLlmGateway.chat(prompt);
```

lambda、HTTP 客户端、你已有的网关封装，任何实现都可以。

## 3. 使用组件

```java
// 工具调用循环：思考 → 决策 → 调用 → 观察 → 继续推理
ToolRegistry tools = new ToolRegistry();
tools.register(new BuiltinTools.CurrentTimeTool());
ToolCallingLoop loop = new ToolCallingLoop(model, tools, 5);
LoopResult result = loop.run("现在几点？", null);
System.out.println(result.answer());

// 任务拆解：目标转 DAG，并行执行
TaskPlan plan = new TaskPlanner(model)
        .plan("审查 PR #42", List.of("Logic", "Security"));
Map<String, DagExecutor.TaskResult> out = new DagExecutor(executor)
        .execute(plan, node -> runMyAgent(node.assignee(), node.description()));

// 基于 ground-truth 的评估
LlmJudge<MyFinding> judge = new LlmJudge<>(model);
LlmJudge.EvalResult report = judge.evaluate(findings, groundTruth);
```

## prompt 里有什么

你不需要自己拼 prompt。每个组件都会组装好完整提示词（含工具清单与决策 JSON 格式要求），并在迭代之间回填观察结果。你的网关只需要原样转发请求、原样返回响应。

所以一行 lambda 就是完整的适配器——协议在组件里，不在你的模型里。

## 下一步

- [工具调用循环](./toolcalling) —— 决策循环详解
- [任务拆解与 DAG](./planning) —— 拆解复杂目标
- [扩展点 SPI](./extension) —— 接入自定义行为
