# Evaluation

Agent output quality is usually asserted by hand. agent-kit ships the two measurements that make it testable: exact metrics against ground truth, and model judgement for the cases ground truth cannot cover.

## Two complementary measurements

| | Ground-truth metrics | llm-as-judge |
|---|---|---|
| Answers | Did we find the known issues? | Are our extra findings real? |
| Produces | precision, recall, F1 | false-positive verdicts |
| Needs | A labelled dataset | Only a model |

## Usage

```java
LlmJudge<MyFinding> judge = new LlmJudge<>(model);
LlmJudge.EvalResult result = judge.evaluate(findings, groundTruth);

result.precision();   // of what we reported, how much was right
result.recall();      // of what was really there, how much we found
result.f1();
result.judgedFalsePositives();
```

`MyFinding` only needs to satisfy `FindingLike`, so your own domain type works directly:

```java
public record MyFinding(String file, String ruleId, String title)
        implements FindingLike {
    public String locator() { return file; }
    public String descriptor() { return title; }
}
```

## Regression benchmarks

Snapshot quality once, then fail the build when it drops:

```java
EvalDataset dataset = EvalDataset.load(Path.of("eval/regression.json"));
EvalReport report = new EvalRunner().run(dataset, c -> produceFindings(c));

if (report.aggregateRecall() < baseline) {
    throw new IllegalStateException("Recall regressed");
}
```

`EvalRunner` aggregates per-case results into a single report, which makes it usable as a CI gate rather than a one-off script.

## When the judge is unavailable

The judge needs a model. If the call fails, evaluation degrades to ground-truth metrics only instead of throwing — measurement must not be able to break the pipeline it measures.
