# 检查点与人机协作

长时间运行的 Agent 需要两样生产系统的标配能力：崩溃后可恢复，必要时可停下来等人。

## 检查点

```java
CheckpointStore store = new FileCheckpointStore(Path.of("./checkpoints"));

store.save(new Checkpoint("run-42", "t3", stateJson, Instant.now()));

Optional<Checkpoint> resume = store.load("run-42");
resume.ifPresent(cp -> continueFrom(cp.stage(), cp.state()));
```

内置两种实现：

| 实现 | 适用场景 |
|------|---------|
| `InMemoryCheckpointStore` | 测试与单进程运行 |
| `FileCheckpointStore` | 跨进程重启恢复 |

状态对存储是透明的——它只持久化一个 JSON 字符串，"进度"在你的领域里意味着什么由你决定。

## 人工审批

```java
ApprovalGate gate = new InMemoryApprovalGate();

// Agent 侧：提交并阻塞等待
ApprovalRequest req = gate.submit("deploy", "是否部署到生产环境？");
ApprovalRequest decided = gate.await(req.id(), Duration.ofHours(2));

if (decided.approved()) proceed();
else abort();
```

```java
// 人工侧：在界面或运维接口中裁决
gate.decide(req.id(), true, "alice");
```

Agent 会阻塞到决策到达或超时。不必自己写轮询循环，也不会陷入回调地狱。

## 为什么放在同一页

两者都是对"时间"的控制：检查点让执行能跨越中断继续，审批门让执行能在中断期间暂停。这两样都做不到的流水线，作为演示没问题，上生产就脆。
