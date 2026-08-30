---
layout: home

hero:
  name: agent-kit
  text: 多 Agent 通用能力积木
  tagline: 工具调用循环、任务拆解 DAG、LLM 评估与扩展点机制 —— 纯 Java 17，无框架依赖
  image:
    src: /architecture.svg
    alt: agent-kit
  actions:
    - theme: brand
      text: 快速开始
      link: /zh/guide/quickstart
    - theme: alt
      text: 在 Gitee 查看
      link: https://gitee.com/liyunfei2030/agent-kit

features:
  - icon: ⚙️
    title: 一行依赖引入
    details: 一个依赖搞定，无框架绑定。Java 17 仅依赖 jackson 与 slf4j，不污染你的 classpath。
  - icon: 🔁
    title: 完整的工具调用循环
    details: 思考 → 决策 → 调用 → 观察 → 继续推理。最大迭代防死循环，非法输出优雅降级，工具异常不炸主流程。
  - icon: 🌳
    title: 任务拆解与 DAG 执行
    details: 把复杂目标拆成依赖图并行执行。构造期环检测，上游失败自动跳过下游。
  - icon: 📊
    title: 内置评估能力
    details: 基于 ground-truth 的 precision / recall / F1，配合 llm-as-judge 判真假阳性，支持命名数据集回归基准。
  - icon: 🔌
    title: 五类扩展点
    details: LlmInterceptor、RagEnhancer、AgentProvider、MemoryStrategy、StageHook —— 注册即生效，可覆盖默认行为。
  - icon: 📦
    title: 已发布 Maven Central
    details: io.github.13liyunfei:agent-kit:0.1.1，GPG 签名，含 sources 与 javadoc，无需私有仓库。
---
