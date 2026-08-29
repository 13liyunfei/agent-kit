# 工具调用循环

多数工具调用演示停在"模型说要调工具"这一步。可用的循环需要完整往返：模型决策 → 工具执行 → 观察回填 → 模型继续推理，直到给出结论。

## 循环过程

```
你提供目标
  |
  v  组装 prompt：目标 + 工具清单 + 决策 JSON 格式
model.chat(prompt)
  |
  v  {"action":"call_tool","tool":"current_time","arguments":{}}
循环执行工具，把结果作为观察追加
  |
  v  再次组装 prompt（此时已含观察）
model.chat(prompt)
  |
  v  {"action":"finish","answer":"现在是 14:32"}
返回 LoopResult(answer, toolCalls, iterations)
```

## 决策协议

模型每轮只返回一个 JSON 对象：

```json
{"action": "call_tool", "tool": "current_time", "arguments": {}}
{"action": "finish", "answer": "现在是 14:32"}
```

## 用法

```java
ToolRegistry tools = new ToolRegistry();
tools.register(new BuiltinTools.CurrentTimeTool());
tools.register(new BuiltinTools.RegexScanTool());

ToolCallingLoop loop = new ToolCallingLoop(model, tools, 5);
LoopResult result = loop.run("在 src/ 下找硬编码密码", projectContext);

result.answer();      // 最终结论（达到上限时为兜底结论）
result.toolCalls();   // 全部调用记录，便于审计
result.iterations();  // 实际迭代轮次
```

## 内置工具

| 工具 | 用途 |
|------|------|
| `CurrentTimeTool` | 当前时间——最小可跑通的端到端示例 |
| `RegexScanTool` | 按正则扫描文本，用于规则类检查 |
| `FileReadTool` | 在白名单根目录内读取文件，拒绝路径穿越 |

## 容错设计

三种失败都被吸收，单步出错不会终止整个循环：

- **非法 JSON** —— 降级为最终答案，不抛异常
- **未知工具** —— 记为观察结果，模型可自我纠正
- **工具抛异常** —— 捕获为观察结果，循环继续

迭代上限是防死循环的硬闸。触达上限时返回兜底结论与已观察到的全部内容。

## 自定义工具

实现 `AgentTool` 即可：

```java
public class DbQueryTool implements AgentTool {
    public String name() { return "db_query"; }
    public String description() { return "执行只读 SQL 查询"; }
    public String parameterSchema() { return "{\"sql\":\"string\"}"; }
    public ToolResult execute(Map<String, Object> args) {
        return ToolResult.success(runQuery((String) args.get("sql")));
    }
}
```

注册后模型即可调用，无需其他接线。
