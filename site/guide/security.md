# Security

Tool calling widens the attack surface: untrusted content now reaches a system that can take actions.

## Injection detection

```java
PromptInjectionDetector detector = new PromptInjectionDetector();
Risk risk = detector.detect(userSuppliedText);

if (risk.highRisk()) reject("Blocked: possible prompt injection");
```

The detector looks for the patterns that matter in practice — instructions to ignore previous directions, attempts to reveal the system prompt, and requests to exfiltrate context.

## Guardrail interceptor

Detection is only useful if it runs on every call. `InjectionGuardInterceptor` is an `LlmInterceptor`, so it plugs into the extension point:

```java
registry.register(LlmInterceptor.class, new InjectionGuardInterceptor());
```

High-risk prompts are refused; low-risk ones are hardened and passed through. Because it is an extension point, you can replace it with your own policy without touching any component.

## Practical guidance

- **Treat retrieved content as untrusted.** RAG results, file contents and issue descriptions are data, not instructions.
- **Keep tools least-privilege.** `FileReadTool` takes an allow-listed root and rejects traversal for exactly this reason.
- **Log every tool invocation.** `LoopResult.toolCalls()` exists so you can audit what happened after the fact.
- **Cap the loop.** An unbounded agent can make unbounded calls; the iteration limit is a control, not just a guard against bugs.
