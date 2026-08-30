---
layout: home

hero:
  name: agent-kit
  text: Multi-agent capability kit
  tagline: Tool calling loop, task decomposition DAG, LLM evaluation and extension SPI — pure Java 17, framework-free
  image:
    src: /architecture-en.svg
    alt: agent-kit
  actions:
    - theme: brand
      text: Get started
      link: /guide/quickstart
    - theme: alt
      text: View on GitHub
      link: https://github.com/13liyunfei/agent-kit

features:
  - icon: ⚙️
    title: Drop-in Maven component
    details: One dependency, no framework lock-in. Java 17 with only jackson and slf4j — nothing else on your classpath.
  - icon: 🔁
    title: Complete tool calling loop
    details: Think, decide, call, observe, keep reasoning. Bounded iterations, graceful degradation on malformed output, tool failures never break the loop.
  - icon: 🌳
    title: Task decomposition DAG
    details: Turn a complex goal into a dependency graph and execute it in parallel. Cycle detection, downstream skipping on upstream failure.
  - icon: 📊
    title: Evaluation built in
    details: precision / recall / F1 against ground truth plus llm-as-judge for false positives. Regression benchmarks over named datasets.
  - icon: 🔌
    title: Five extension points
    details: LlmInterceptor, RagEnhancer, AgentProvider, MemoryStrategy, StageHook — register your own behaviour and override defaults.
  - icon: 📦
    title: On Maven Central
    details: io.github.13liyunfei:agent-kit:0.1.1 — signed, with sources and javadoc. No private repository required.
---
