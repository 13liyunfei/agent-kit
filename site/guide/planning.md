# Planning and DAG execution

A single prompt rarely solves a complex goal. Decomposing it into subtasks with dependencies, then running whatever is ready in parallel, is what turns a demo into a system.

## Components

| Class | Role |
|-------|------|
| `TaskPlanner` | Asks the model to break a goal into subtasks with dependencies |
| `TaskPlan` | The resulting DAG, validated at construction |
| `DagExecutor` | Runs the DAG, respecting dependencies, in parallel |

## Usage

```java
TaskPlan plan = new TaskPlanner(model)
        .plan("Review PR #42 for correctness and security",
              List.of("Logic", "Security", "Performance"));

Map<String, DagExecutor.TaskResult> results = new DagExecutor(executor)
        .execute(plan, node -> runMyAgent(node.assignee(), node.description()));

results.forEach((id, r) -> {
    if (r.failed()) log.warn("{} skipped or failed: {}", id, r.error());
    else process(id, r.output());
});
```

## What the planner produces

Each node carries an id, a description, an assignee, and the ids it depends on:

```json
{
  "tasks": [
    { "id": "t1", "description": "Check control flow", "assignee": "Logic",     "dependsOn": [] },
    { "id": "t2", "description": "Check for injection",  "assignee": "Security",  "dependsOn": [] },
    { "id": "t3", "description": "Summarise findings",   "assignee": "Reporter",  "dependsOn": ["t1", "t2"] }
  ]
}
```

## Validation at construction

`TaskPlan` refuses to exist in a broken state:

- Duplicate ids are rejected
- Dependencies pointing at unknown ids are rejected
- Cycles are rejected via Kahn's algorithm

You get the error when the plan is built, not three layers deep into execution.

## Execution semantics

- Nodes with no unmet dependencies start immediately and run in parallel
- A node starts as soon as all its dependencies finish
- If a dependency fails, downstream nodes are **skipped** — other branches are unaffected

## Degradation

Planning is an enhancement, not a requirement. If the model returns something unparseable, `TaskPlanner` falls back to a single-task pass-through plan, so the goal still executes rather than failing. This is the same principle used in the reference adoption, code-review-agent: planning can improve the result, but it must never be able to break it.
