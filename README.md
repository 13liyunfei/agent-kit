# agent-kit

> [English](README.md) | [中文](README.zh-CN.md) | [📖 Documentation](https://13liyunfei.github.io/agent-kit/)

[![Docs](https://img.shields.io/badge/docs-github.io/agent--kit-4D1F7C)](https://13liyunfei.github.io/agent-kit/)

Reusable multi-agent capability kit — plug in as a Maven component, extend via SPI.

Pure Java 17, framework-free (only jackson-databind + slf4j). Any multi-agent project can adopt it as an **embedded capability library**: bring your own `ChatModel`, compose the building blocks, and customize behavior through extension points.

## Getting started

```xml
<dependency>
    <groupId>io.github.13liyunfei</groupId>
    <artifactId>agent-kit</artifactId>
    <version>0.1.0</version>
</dependency>
```

Build & test: see [BUILD.md](BUILD.md).

![agent-kit layered architecture](docs/architecture-en.svg)

## Components (14 components / 12 capability areas)

| Package | Components | Capability |
| --- | --- | --- |
| `kit.toolcalling` | `AgentTool` / `ToolRegistry` / `ToolCallingLoop` / `BuiltinTools` | Tool-calling decision loop (think → decide → call → observe → reason; max-iteration guard, illegal-JSON fallback, tool-error isolation) + built-ins (`current_time` / `regex_scan` / `file_read` with path-traversal guard) |
| `kit.planning` | `TaskPlanner` / `TaskPlan` / `DagExecutor` | LLM task decomposition into a dependency DAG (id-unique / dep-exists / Kahn acyclic) + topo-parallel execution (upstream failure skips downstream) |
| `kit.eval` | `LlmJudge` / `FindingLike` / `EvalDataset` / `EvalRunner` | Ground-truth precision/recall/F1 + llm-as-judge; named dataset regression (domain-decoupled via `FindingLike`) |
| `kit.extension` | `ExtensionPoint` / `ExtensionRegistry` + `spi/` 5 interfaces | Order-based weaving, same-name override, thread-safe; `LlmInterceptor` / `RagEnhancer` / `AgentProvider` / `MemoryStrategy` / `StageHook` |
| `kit.session` / `kit.stream` | `ChatMessage` / `ChatSession` / `ChatStreams` | Multi-turn context window (message-count + token-budget trimming); streaming utilities (JDK Flow.Publisher) |
| `kit.struct` | `StructuredChatModel` | Structured output: JSON Schema binding + validation with automatic retry |
| `kit.mcp` | `McpClient` / `McpTool` / `McpToolAdapter` | MCP (Model Context Protocol) client: stdio + JSON-RPC 2.0, connect to the tool ecosystem |
| `kit.checkpoint` | `CheckpointStore` (in-memory / file) | Checkpoint persistence: crash recovery / resume |
| `kit.obs` | `GenAiSpan` / `GenAiTracer` / `TracedChatModel` | Observability: GenAI spans / latency / tokens / cost |
| `kit.hitl` | `ApprovalRequest` / `ApprovalGate` | Human-in-the-loop: submit approval → human decision → blocking await |
| `kit.router` | `ModelRouter` / `RoutingChatModel` | Multi-model routing (priority) + automatic failover |
| `kit.security` | `PromptInjectionDetector` / `InjectionGuardInterceptor` | Prompt-injection defense (high/low risk), wired in as a `LlmInterceptor` SPI example |

## Model boundary: `ChatModel`

kit does not depend on any specific LLM vendor — a single interface is the only model boundary:

```java
public interface ChatModel {
    String chat(String prompt);                                   // sync
    default Flow.Publisher<String> stream(String prompt) { ... }  // streaming, overridable
}
```

Adapt your own gateway / SDK / MaaS with a one-liner:

```java
ChatModel model = prompt -> myLlmGateway.chat(prompt); // your implementation
```

## Extension points (custom behavior)

Implement an SPI → register in `ExtensionRegistry` → woven by `order()` ascending (built-ins use large order, custom extensions use small order to layer on top).

| SPI | Purpose | Key methods |
| --- | --- | --- |
| `LlmInterceptor` | Pre/post LLM call (injection guard / audit / correction) | `String before(prompt)` / `String after(prompt, response)` |
| `RagEnhancer<T>` | Retrieval enhancement (rerank / dedupe / inject KB) | `List<T> enhance(hits, query)` |
| `AgentProvider<A>` | Provide domain agent instances | `List<A> provide()` |
| `MemoryStrategy` | Replace memory read/write strategy | `Optional<String> get(key)` / `put(key, value)` |
| `StageHook` | Workflow stage callbacks (trace / trajectory / audit) | `void onStage(stage, ctx)` |

```java
ExtensionRegistry registry = new ExtensionRegistry();
registry.register(LlmInterceptor.class, new MyAuditInterceptor()); // custom extension
List<LlmInterceptor> chain = registry.list(LlmInterceptor.class);   // ordered chain
```

## Minimal usage

`model` below is a `ChatModel` — kit's only model boundary, provided by your project (a one-liner lambda or your own implementation, see [Model boundary](#model-boundary-chatmodel)). The prompt passed to `chat(prompt)` is assembled by the kit components (including the tool manifest and a decision-JSON format directive); your gateway just forwards request/response as-is — it never deals with MCP directly.

```java
// 0. Model: adapt your LLM gateway to kit's single model boundary ChatModel
//    The prompt is built by the components (tool manifest + decision format);
//    just forward it. No MCP awareness needed here.
ChatModel model = prompt -> myLlmGateway.chat(prompt);   // or new OpenAiChatModel(key)

// 1. Tool-calling loop: think → decide → call → observe → reason
ToolRegistry tools = new ToolRegistry();
tools.register(new BuiltinTools.CurrentTimeTool());                    // built-in: current time
McpClient mcp = McpClient.start("npx", "-y", "@modelcontextprotocol/server-github");
mcp.listTools().forEach(t -> tools.register(new McpToolAdapter(mcp, t))); // mount MCP tools

ToolCallingLoop loop = new ToolCallingLoop(model, tools, 5);  // max 5 rounds, no infinite loop
// Round 1: model returns {"action":"call_tool","tool":"current_time","arguments":{}}
// loop executes the tool, appends the observation, asks again;
// until the model returns {"action":"finish","answer":"..."}
LoopResult r = loop.run("What time is it now? Also check this repo's star count", "PR #42 context");
System.out.println(r.answer());      // final conclusion (given on finish)
System.out.println(r.toolCalls());   // the actual tool chain (audit)

// 2. Task decomposition + DAG parallel execution
TaskPlan plan = new TaskPlanner(model).plan("review PR #42", List.of("Logic", "Security"));
Map<String, DagExecutor.TaskResult> results = new DagExecutor(executor)
        .execute(plan, node -> runAgent(node.assignee(), node.description()));

// 3. Evaluation + regression benchmark (ground-truth precision/recall)
LlmJudge<MyFinding> judge = new LlmJudge<>(model);
EvalReport report = new EvalRunner().run(dataset, case -> produceFindings(case));
```

## Testing

```bash
mvn test   # 38 cases: loop semantics / DAG topo & cycle rejection / eval aggregation /
           # extension weaving / session trimming / structured retry / MCP full chain /
           # checkpoint restore / HITL approval / router failover / injection guard
```

## Adopters

- **[code-review-agent](https://gitee.com/13liyunfei/code-review-agent)** — multi-agent collaborative code review engine (Java 17 / Spring Boot 3.3). It consumes agent-kit as a Maven dependency, powering its tool-calling loop, task-decomposition DAG, LLM evaluation, and extension points; a production reference of how to embed this kit.

More projects will be added here as they adopt agent-kit.

## License

[MIT](LICENSE) © 2026 13liyunfei
