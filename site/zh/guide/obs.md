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

需要给包装器覆盖不到的环节计时时，也可以手动记录：

```java
GenAiSpan span = tracer.start("planning");
try { plan(); } finally { span.end(); }
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
