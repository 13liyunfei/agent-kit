# 会话与流式

单次 `chat()` 是无状态的。真实对话需要历史，真实界面需要 token 边生成边显示。

## 多轮会话

```java
ChatSession session = new ChatSession(20, 4000);  // 最多 20 条，4000 token 预算

session.system("你是一个代码审查助手。");
session.user("审查这段 diff：...");
String reply = model.chat(session.toPrompt());
session.assistant(reply);

session.user("现在只关注安全问题。");
String reply2 = model.chat(session.toPrompt());
```

`toPrompt()` 把历史渲染成单个 prompt。会话自己从不调用模型——它只管上下文，这样模型边界始终只有一处。

## 自动裁剪

两条相互独立的限制，都在读取时生效：

- **条数上限** —— 丢弃最早的轮次，system 消息始终保留
- **token 预算** —— 丢弃最早轮次直到估算值装得下

裁剪自动发生，长对话因此优雅降级，而不是撞上上下文长度错误。

## 流式输出

`ChatModel` 通过 JDK 自带的 `Flow.Publisher` 暴露流式能力——不引入任何响应式库：

```java
model.stream(prompt).subscribe(new Flow.Subscriber<>() {
    public void onSubscribe(Flow.Subscription s) { s.request(Long.MAX_VALUE); }
    public void onNext(String chunk) { print(chunk); }
    public void onError(Throwable t) { showError(t); }
    public void onComplete() { println(); }
});
```

也可以一次性收集成完整字符串：

```java
String full = ChatStreams.collect(model.stream(prompt));
```

`stream()` 的默认实现会把完整响应作为单个分块发出，因此任何 `ChatModel` 天生支持流式。网关能做得更好时覆盖它即可。
