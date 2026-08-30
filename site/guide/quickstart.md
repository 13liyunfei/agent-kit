# Quick start

## 1. Add the dependency

```xml
<dependency>
    <groupId>io.github.13liyunfei</groupId>
    <artifactId>agent-kit</artifactId>
    <version>0.1.1</version>
</dependency>
```

Requires Java 17 or later.

## 2. Provide a model

agent-kit never calls an LLM on its own. You supply a `ChatModel`:

```java
ChatModel model = prompt -> myLlmGateway.chat(prompt);
```

Any implementation works — a lambda, an HTTP client, or your existing gateway wrapper.

## 3. Use the components

```java
// Tool calling loop: think, decide, call, observe, keep reasoning
ToolRegistry tools = new ToolRegistry();
tools.register(new BuiltinTools.CurrentTimeTool());
ToolCallingLoop loop = new ToolCallingLoop(model, tools, 5);
LoopResult result = loop.run("What time is it?", null);
System.out.println(result.answer());

// Task decomposition: goal to DAG, executed in parallel
TaskPlan plan = new TaskPlanner(model)
        .plan("review PR #42", List.of("Logic", "Security"));
Map<String, DagExecutor.TaskResult> out = new DagExecutor(executor)
        .execute(plan, node -> runMyAgent(node.assignee(), node.description()));

// Evaluation against ground truth
LlmJudge<MyFinding> judge = new LlmJudge<>(model);
LlmJudge.EvalResult report = judge.evaluate(findings, groundTruth);
```

## What the prompt contains

You do not build prompts yourself. Each component assembles them — including the tool inventory and the required JSON decision format — and appends observations between iterations. Your gateway just forwards the request and returns the response verbatim.

This is why a one-line lambda is a complete adapter: the protocol lives in the components, not in your model.

## Next

- [Tool calling loop](./toolcalling) — the decision loop in detail
- [Planning and DAG](./planning) — decomposing complex goals
- [Extension SPI](./extension) — plugging in your own behaviour
