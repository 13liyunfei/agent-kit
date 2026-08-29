# Checkpoint and human approval

Long-running agent work needs two things production systems take for granted: the ability to resume after a crash, and the ability to pause for a human.

## Checkpoints

```java
CheckpointStore store = new FileCheckpointStore(Path.of("./checkpoints"));

store.save(new Checkpoint("run-42", "t3", stateJson, Instant.now()));

Optional<Checkpoint> resume = store.load("run-42");
resume.ifPresent(cp -> continueFrom(cp.stage(), cp.state()));
```

Two implementations ship:

| Implementation | Use for |
|----------------|---------|
| `InMemoryCheckpointStore` | Tests and single-process runs |
| `FileCheckpointStore` | Resuming across process restarts |

The state is opaque to the store — it persists a JSON string, so you decide what "progress" means in your domain.

## Human approval

```java
ApprovalGate gate = new InMemoryApprovalGate();

// Agent side: request and block
ApprovalRequest req = gate.submit("deploy", "Deploy to production?");
ApprovalRequest decided = gate.await(req.id(), Duration.ofHours(2));

if (decided.approved()) proceed();
else abort();
```

```java
// Human side: decide, from a UI or an ops endpoint
gate.decide(req.id(), true, "alice");
```

The agent blocks until a decision arrives or the timeout expires. No polling loop to write, no callback spaghetti.

## Why these belong together

Both are about control over time: a checkpoint lets execution survive a gap, an approval gate lets execution pause during one. Pipelines that cannot do either are fine as demos and fragile in production.
