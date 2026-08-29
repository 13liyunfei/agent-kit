# Tool calling loop

Most tool-calling demos stop at "the model asked for a tool". A usable loop needs the full round trip: the model decides, the tool runs, the observation feeds back, and the model reasons again until it is done.

## The loop

```
you provide a goal
  |
  v  buildPrompt: goal + tool inventory + JSON decision format
model.chat(prompt)
  |
  v  {"action":"call_tool","tool":"current_time","arguments":{}}
loop executes the tool, appends the observation
  |
  v  buildPrompt again, now with the observation
model.chat(prompt)
  |
  v  {"action":"finish","answer":"It is 14:32 UTC."}
loop returns LoopResult(answer, toolCalls, iterations)
```

## Decision protocol

The model replies with one JSON object per iteration:

```json
{"action": "call_tool", "tool": "current_time", "arguments": {}}
{"action": "finish", "answer": "It is 14:32 UTC."}
```

## Usage

```java
ToolRegistry tools = new ToolRegistry();
tools.register(new BuiltinTools.CurrentTimeTool());
tools.register(new BuiltinTools.RegexScanTool());

ToolCallingLoop loop = new ToolCallingLoop(model, tools, 5);
LoopResult result = loop.run("Find hardcoded passwords in src/", projectContext);

result.answer();      // final answer, or a fallback notice if the cap was hit
result.toolCalls();   // every call made, useful for auditing
result.iterations();  // how many rounds it took
```

## Built-in tools

| Tool | Purpose |
|------|---------|
| `CurrentTimeTool` | Current time — the smallest possible end-to-end demo |
| `RegexScanTool` | Scan text against a pattern, for rule-style checks |
| `FileReadTool` | Read files under an allow-listed root, with path traversal blocked |

## Failure handling

Three failure modes are absorbed so a single bad step never kills the run:

- **Malformed JSON** — treated as the final answer rather than thrown
- **Unknown tool** — recorded as an observation so the model can correct itself
- **Tool throws** — captured as an observation, the loop continues

The iteration cap is a hard stop against runaway loops. When it is reached, the loop returns a fallback answer plus everything it has observed so far.

## Your own tools

Implement `AgentTool`:

```java
public class DbQueryTool implements AgentTool {
    public String name() { return "db_query"; }
    public String description() { return "Run a read-only SQL query"; }
    public String parameterSchema() { return "{\"sql\":\"string\"}"; }
    public ToolResult execute(Map<String, Object> args) {
        return ToolResult.success(runQuery((String) args.get("sql")));
    }
}
```

Register it and the model can call it on the next run — no other wiring.
