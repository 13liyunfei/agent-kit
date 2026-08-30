# 可观测与路由

## 追踪

```java
GenAiTracer tracer = new LoggingGenAiTracer();
ChatModel traced = new TracedChatModel(model, tracer);

traced.chat("...");   // span 自动记录
```

每次调用都会产生一个 `GenAiSpan`，包含操作名、耗时，以及网关上报时的 token 数。由于 `TracedChatModel` 本身也是 `ChatModel`，它可以和其他组件自由组合：

```java
ChatModel model = new TracedChatModel(new RoutingChatModel(router), tracer);
```

### 带上链路 id

多 Agent 系统会跨线程扇出，span 需要有个东西把它归回"是哪次请求引起的"。基座刻意**不自带**链路上下文——id 的生成与传播（MDC、线程池、父子线程恢复）是使用方的关注点，你只需把已有的 id 交出来：

```java
TracedChatModel traced = new TracedChatModel(
        model, tracer, TraceContext::getTraceId, "qwen-plus");
```

### 失败同样留痕

抛异常的调用会被记录成一个带 `error` 的 span，然后原样抛出。0.1.0 的行为是**失败时完全不记录**——恰恰漏掉了最该被看见的那些调用。

```java
try {
    traced.chat("...");
} catch (RuntimeException e) {
    tracer.spans();  // 最后一个 failed() == true，error 为失败原因
}
```

流式调用同样被观测：`stream()` 整条流记一个 span，流中断时记录错误。

### 聚合成指标

```java
AggregateTracer agg = new AggregateTracer();
GenAiTracer tracer = GenAiTracer.composite(agg, new LoggingGenAiTracer());

AggregateTracer.Stats stats = agg.snapshot();
stats.calls();            // 调用次数
stats.errors();           // 失败次数
stats.errorRate();        // 0~1
stats.inputTokens();      // 输入 token
stats.outputTokens();     // 输出 token
stats.avgLatencyMs();     // 平均耗时
agg.byOperation();        // 按操作名分组的同样指标
agg.reset();              // 按"每次运行"重新计数
```

也可以给包装器覆盖不到的环节计时：

```java
GenAiSpan span = tracer.record("planning", this::plan);          // 无返回值
Plan plan = tracer.trace("planning", this::plan);                // 有返回值
```

## 模型路由

```java
ModelRouter router = new ModelRouter();
router.register("primary", primaryModel, 100);
router.register("backup",  backupModel, 1);

ChatModel model = new RoutingChatModel(router);
```

请求优先走优先级最高的健康模型。它抛异常就自动切到下一个。全部失败则异常上抛——静默成功比可见失败更糟。

## 这带来什么

没有追踪，Agent 流水线就是个偶尔又贵又慢的黑盒。有了 span，"哪一步慢""这次跑花了多少"这两个问题不用给每个组件加日志就能回答。
