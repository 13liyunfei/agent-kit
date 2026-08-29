# 评估与回归

Agent 输出质量通常靠人工断言。agent-kit 提供两种可测试的度量：基于 ground-truth 的精确指标，以及 ground-truth 覆盖不到时的模型判定。

## 两种互补度量

| | ground-truth 指标 | llm-as-judge |
|---|---|---|
| 回答什么 | 已知问题是否都被发现？ | 我们多报的是不是真问题？ |
| 产出 | precision、recall、F1 | 误报判定 |
| 依赖 | 标注数据集 | 仅需一个模型 |

## 用法

```java
LlmJudge<MyFinding> judge = new LlmJudge<>(model);
LlmJudge.EvalResult result = judge.evaluate(findings, groundTruth);

result.precision();   // 报出的里面有多少是对的
result.recall();      // 真实存在的里面找到了多少
result.f1();
result.judgedFalsePositives();
```

`MyFinding` 只需实现 `FindingLike`，你自己的领域类型可以直接用：

```java
public record MyFinding(String file, String ruleId, String title)
        implements FindingLike {
    public String locator() { return file; }
    public String descriptor() { return title; }
}
```

## 回归基准

先固化一次质量基线，之后指标下降就让构建失败：

```java
EvalDataset dataset = EvalDataset.load(Path.of("eval/regression.json"));
EvalReport report = new EvalRunner().run(dataset, c -> produceFindings(c));

if (report.aggregateRecall() < baseline) {
    throw new IllegalStateException("召回率出现回归");
}
```

`EvalRunner` 把逐例结果聚合成一份报告，因此可以作为 CI 门禁，而不只是一次性脚本。

## judge 不可用时的降级

judge 需要模型。调用失败时评估降级为仅 ground-truth 指标，而不是抛异常——度量本身不能把被度量的流水线搞挂。
