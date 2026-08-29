# Session and streaming

A single `chat()` call is stateless. Real conversations need history, and real interfaces need tokens to appear as they are produced.

## Multi-turn sessions

```java
ChatSession session = new ChatSession(20, 4000);  // 20 messages, 4000 token budget

session.system("You are a code review assistant.");
session.user("Review this diff: ...");
String reply = model.chat(session.toPrompt());
session.assistant(reply);

session.user("Now focus on security only.");
String reply2 = model.chat(session.toPrompt());
```

`toPrompt()` renders the history into a single prompt. The session never calls a model itself — it only manages context, which keeps the model boundary in one place.

## Automatic trimming

Two independent limits, both enforced on read:

- **Message count** — oldest turns are dropped, the system message is always kept
- **Token budget** — oldest turns are dropped until the estimate fits

Trimming happens automatically, so a long conversation degrades gracefully instead of failing with a context-length error.

## Streaming

`ChatModel` exposes streaming through the JDK's own `Flow.Publisher` — no reactive library required:

```java
model.stream(prompt).subscribe(new Flow.Subscriber<>() {
    public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
    public void onNext(String chunk) { print(chunk); }
    public void onError(Throwable t) { showError(t); }
    public void onComplete() { println(); }
});
```

Or collect it back into a whole string:

```java
String full = ChatStreams.collect(model.stream(prompt));
```

The default `stream()` implementation emits the full response as a single chunk, so every `ChatModel` supports streaming immediately. Override it when your gateway can do better.
