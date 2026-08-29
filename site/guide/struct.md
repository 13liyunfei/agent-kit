# Structured output

Asking a model to "return JSON" works until it doesn't — a stray explanation, a trailing comma, a case change. Structured output binds a schema and retries when validation fails.

## The problem

```java
String raw = model.chat("Return JSON with fields action and level");
// "Sure! Here is the JSON:\n```json\n{...}\n```"   <- parse error
```

## The fix

```java
public record Decision(String action, int level) {}

StructuredChatModel structured = new StructuredChatModel(model);
Decision d = structured.chat("Classify this change", Decision.class, 3);

d.action();  // "reject"
d.level();   // 3
```

The schema is derived from the target type, appended to the prompt, and the response is validated. On failure it retries, up to the limit you pass, then throws.

## Why retries are part of it

A single retry converts most malformed responses into valid ones. Without it you write the same loop in every call site — and usually forget the error message that makes the retry succeed.

## Behaviour

| Situation | Outcome |
|-----------|---------|
| Valid JSON on the first try | Parsed immediately |
| Invalid JSON | Retry, up to the configured limit |
| Still invalid after the limit | Throws, with the last response attached |
| Model call fails | Propagates — this is not a formatting problem |

Structured output is an enhancement on top of `ChatModel`, not a replacement. The same model instance keeps working everywhere else.
