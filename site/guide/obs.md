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

### Carrying a trace id

Multi-agent systems fan out across threads, so a span needs something to tie it back to the request that caused it. The kit deliberately does **not** ship a trace context — generating and propagating ids is your concern (MDC, thread pools, parent/child restore). You just hand the id over:

```java
TracedChatModel traced = new TracedChatModel(
        model, tracer, TraceContext::getTraceId, "qwen-plus");
```

### Failures leave a trace too

A call that throws is recorded as a span with `error` set, then rethrown unchanged. In 0.1.0 a failing call produced **no span at all** — hiding precisely the calls you most need to see.

```java
try {
    traced.chat("...");
} catch (RuntimeException e) {
    tracer.spans();  // last span has failed() == true and the message
}
```

Streaming is traced as well: `stream()` produces one span for the whole stream, with the error recorded if the stream fails.

### Aggregating into metrics

```java
AggregateTracer agg = new AggregateTracer();
GenAiTracer tracer = GenAiTracer.composite(agg, new LoggingGenAiTracer());

AggregateTracer.Stats stats = agg.snapshot();
stats.calls();            // how many calls
stats.errors();           // how many failed
stats.errorRate();        // 0..1
stats.inputTokens();      // tokens in
stats.outputTokens();     // tokens out
stats.avgLatencyMs();     // mean duration
agg.byOperation();        // same numbers, grouped by operation
agg.reset();              // per-run accounting
```

You can also time anything the wrapper does not cover:

```java
GenAiSpan span = tracer.record("planning", this::plan);          // no return value
Plan plan = tracer.trace("planning", this::plan);                // with a return value
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
