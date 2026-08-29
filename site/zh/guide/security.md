# 安全

工具调用会放大攻击面：不可信内容现在可以触达一个能执行动作的系统。

## 注入检测

```java
PromptInjectionDetector detector = new PromptInjectionDetector();
Risk risk = detector.detect(userSuppliedText);

if (risk.highRisk()) reject("已拦截：疑似提示注入");
```

检测覆盖实践中真正会出问题的模式——要求忽略先前指令、试图套取系统提示、要求外传上下文。

## 防护拦截器

检测只有在每次调用都执行时才有意义。`InjectionGuardInterceptor` 实现了 `LlmInterceptor`，因此可以直接插到扩展点上：

```java
registry.register(LlmInterceptor.class, new InjectionGuardInterceptor());
```

高风险 prompt 直接拒绝，低风险的做硬化处理后放行。因为它是扩展点，你可以不碰任何组件就换成自己的策略。

## 实践建议

- **把检索到的内容当不可信输入。** RAG 结果、文件内容、issue 描述都是数据，不是指令。
- **工具保持最小权限。** `FileReadTool` 要求传入白名单根目录并拒绝路径穿越，正是为此。
- **记录每一次工具调用。** `LoopResult.toolCalls()` 的存在就是为了事后审计。
- **给循环设上限。** 不受约束的 Agent 会产生不受约束的调用——迭代上限是一种控制手段，不只是防 bug。
