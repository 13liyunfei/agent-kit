# Introduction

agent-kit is a **reusable capability kit for multi-agent systems**. It is not a framework that owns your runtime — it is a library of the algorithms every agent application ends up needing, packaged as a single Maven dependency.

## What it is, and what it is not

| | agent-kit | LangGraph / CrewAI / SDKs |
|---|---|---|
| Question it answers | *What algorithms does my agent need?* | *How does my agent run?* |
| Owns your lifecycle | No | Yes |
| Runtime / state machine | Yours | Provided |
| Works alongside | Anything, including LangChain4j or Spring AI | — |

Because it claims no lifecycle, you can drop agent-kit into an existing service without rewriting it.

## Capability map

**Execution**
- `toolcalling` — decision loop, tool registry, built-in tools
- `planning` — task decomposition into a DAG, topologically parallel execution
- `session` — multi-turn context window with trimming
- `struct` — schema-bound output with validation retry
- `mcp` — Model Context Protocol client and tool adapter

**Quality and governance**
- `eval` — precision / recall / F1, llm-as-judge, regression datasets
- `checkpoint` — save and resume execution state
- `obs` — GenAI tracing spans, cost and latency metrics
- `hitl` — human approval gates
- `router` — multi-model routing with failover

**Foundation**
- `extension` — extension points and registry
- `security` — prompt injection detection and guardrail interceptor

## The single model boundary

Every component that needs a model talks to one interface:

```java
public interface ChatModel {
    String chat(String prompt);
    default Flow.Publisher<String> stream(String prompt) { ... }
}
```

You adapt your existing LLM gateway once, and every component can use it. No component knows whether you call OpenAI, a self-hosted model, or a corporate gateway.

Continue to [Quick start](./quickstart).
