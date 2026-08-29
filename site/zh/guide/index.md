# 简介

agent-kit 是**多 Agent 系统的通用能力组件库**。它不接管你的运行时，而是把每个 Agent 应用最终都会用到的算法打包成一个 Maven 依赖。

## 它是什么，不是什么

| | agent-kit | LangGraph / CrewAI / 各类 SDK |
|---|---|---|
| 回答的问题 | 我的 Agent 需要哪些算法？ | 我的 Agent 怎么跑起来？ |
| 是否接管生命周期 | 否 | 是 |
| 运行时 / 状态机 | 你自己提供 | 框架提供 |
| 能否共存 | 可以，包括 LangChain4j、Spring AI | — |

正因为它不争生命周期，你可以把它塞进现有服务而不用重写。

## 能力地图

**执行层**
- `toolcalling` —— 决策循环、工具注册中心、内置工具
- `planning` —— 目标拆解为 DAG，拓扑并行执行
- `session` —— 多轮上下文窗口与裁剪
- `struct` —— Schema 绑定输出与校验重试
- `mcp` —— Model Context Protocol 客户端与工具适配

**质量与治理层**
- `eval` —— precision / recall / F1、llm-as-judge、回归数据集
- `checkpoint` —— 执行状态保存与恢复
- `obs` —— GenAI 追踪 span、成本与延迟指标
- `hitl` —— 人工审批门
- `router` —— 多模型路由与 failover

**基础层**
- `extension` —— 扩展点与注册中心
- `security` —— 提示注入检测与防护拦截器

## 唯一的模型边界

所有需要模型的组件都只认这一个接口：

```java
public interface ChatModel {
    String chat(String prompt);
    default Flow.Publisher<String> stream(String prompt) { ... }
}
```

你只需适配一次自己的 LLM 网关，所有组件都能用。组件不关心你调的是 OpenAI、私有化部署的模型，还是企业网关。

继续阅读 [快速开始](./quickstart)。
