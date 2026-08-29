# Observability and routing

## Tracing

```java
GenAiTracer tracer = new LoggingGenAiTracer();
ChatModel traced = new TracedChatModel(model, tracer);

traced.chat("...");   // span recorded automatically
```

Each call produces a `GenAiSpan` with the operation name, duration, and token counts when your gateway reports them. Because `TracedChatModel` is itself a `ChatModel`, it composes with everything else:

```java
ChatModel model = new TracedChatModel(new RoutingChatModel(router), tracer);
```

You can also record spans manually when you need to time something the wrapper does not cover:

```java
GenAiSpan span = tracer.start("planning");
try { plan(); } finally { span.end(); }
```

## Model routing

```java
ModelRouter router = new ModelRouter();
router.register("primary", primaryModel, 100);
router.register("backup",  backupModel, 1);

ChatModel model = new RoutingChatModel(router);
```

Requests go to the highest-priority healthy model. If it throws, the router fails over to the next one. If every model fails, the exception propagates — silent success is worse than a visible failure.

## What this buys you

Without tracing, an agent pipeline is a black box that occasionally costs too much and takes too long. With spans, you can answer "which step is slow" and "what did this run cost" without adding logging to every component.
