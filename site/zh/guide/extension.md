# 扩展点 SPI

所有内置行为都可被替换。注册中心按序组织实现，同名注册即覆盖——你无需 fork 就能改造 agent-kit。

## 五类扩展点

| 接口 | 用途 |
|------|------|
| `LlmInterceptor` | 在每次模型调用前后改写 prompt 与响应 |
| `RagEnhancer` | 对检索结果重排、去重或补充 |
| `AgentProvider` | 把自己的领域 Agent 注入流水线 |
| `MemoryStrategy` | 把记忆实现换成向量库、数据库或任意存储 |
| `StageHook` | 在流水线各阶段收到回调，用于追踪、审计、降级 |

## 用法

```java
ExtensionRegistry registry = new ExtensionRegistry();

registry.register(LlmInterceptor.class, new MyPromptHardener(100));
registry.register(StageHook.class, new MyTraceHook(10));
registry.register(MemoryStrategy.class, new MyVectorMemory());

// 流水线按 order() 返回值排序后取用
List<LlmInterceptor> chain = registry.list(LlmInterceptor.class);
```

`order` 值越小越先执行，因此织入顺序是确定的。同名再次注册会覆盖前一条。

## 完整示例

在模型收到请求前拦截注入尝试：

```java
public class InjectionGuard implements LlmInterceptor {
    public String name() { return "injection-guard"; }
    public int order() { return 0; }   // 最先执行

    public String before(String prompt) {
        Risk risk = new PromptInjectionDetector().detect(prompt);
        return risk.highRisk() ? wrapWithWarning(prompt) : prompt;
    }
}
```

`security` 包里的 `InjectionGuardInterceptor` 就是这个实现，直接注册即可用。

## 为什么用 SPI 而不是继承

继承会逼你去接管一个不是你写的类的生命周期。扩展点让你在不继承任何东西的前提下叠加行为——你的实现保持单一职责的普通类，可以独立测试。
