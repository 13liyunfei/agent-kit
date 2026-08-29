# Extension SPI

Every built-in behaviour is replaceable. The registry keeps implementations ordered, and same-name registration overrides — so you can adjust agent-kit without forking it.

## The five extension points

| Interface | Purpose |
|-----------|---------|
| `LlmInterceptor` | Rewrite prompts before and responses after every model call |
| `RagEnhancer` | Re-rank, deduplicate or augment retrieved context |
| `AgentProvider` | Contribute your own domain agents into the pipeline |
| `MemoryStrategy` | Swap the memory backend for vectors, a database, or anything else |
| `StageHook` | Receive callbacks at pipeline stages for tracing, auditing, degradation |

## Usage

```java
ExtensionRegistry registry = new ExtensionRegistry();

registry.register(LlmInterceptor.class, new MyPromptHardener(100));
registry.register(StageHook.class, new MyTraceHook(10));
registry.register(MemoryStrategy.class, new MyVectorMemory());

// The pipeline picks them up, ordered by the value returned from order()
List<LlmInterceptor> chain = registry.list(LlmInterceptor.class);
```

Lower `order` runs earlier, so chains are deterministic. Registering the same name again replaces the previous entry.

## A worked example

A guardrail that flags injection attempts before they reach the model:

```java
public class InjectionGuard implements LlmInterceptor {
    public String name() { return "injection-guard"; }
    public int order() { return 0; }   // run first

    public String before(String prompt) {
        Risk risk = new PromptInjectionDetector().detect(prompt);
        return risk.highRisk() ? wrapWithWarning(prompt) : prompt;
    }
}
```

`InjectionGuardInterceptor` in the `security` package is exactly this, already implemented — registration is all that is needed.

## Why SPI instead of subclassing

Subclassing forces you to own the lifecycle of a class you did not write. An extension point lets you add behaviour without inheriting anything — your implementation stays a plain class with one responsibility, testable in isolation.
