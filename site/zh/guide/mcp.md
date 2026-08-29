# MCP 接入

MCP（Model Context Protocol）是工具互操作的事实标准。与其把每个集成都重写一遍，不如接上 MCP 服务器，让它的工具直接进入标准决策循环。

## 用法

```java
McpClient mcp = McpClient.start("npx", "-y", "@modelcontextprotocol/server-github");
mcp.listTools().forEach(t -> tools.register(new McpToolAdapter(mcp, t)));

ToolCallingLoop loop = new ToolCallingLoop(model, tools, 5);
```

这就是全部集成代码。MCP 工具与你自己的工具在同一个注册中心里，以完全相同的方式提供给模型——循环分不出它们，也不需要分。

## 客户端做了什么

`McpClient` 通过 stdio 说 JSON-RPC 2.0：

1. `initialize` —— 握手
2. `tools/list` —— 发现服务器提供的工具
3. `tools/call` —— 调用工具并返回结果

`McpToolAdapter` 把每个发现的工具包装成 `AgentTool`，转换其名称、描述、参数 Schema 与执行逻辑。

## 模型完全无感

这一点值得强调：**你的模型适配器一行都不用改**。工具清单由循环组装，观察结果由循环回填，网关继续原样转发它收到的东西。

```java
ChatModel model = prompt -> myLlmGateway.chat(prompt);   // 无需改动
```

接入 MCP 改变的是"有哪些工具可用"，不是"模型怎么调用"。

## 传输方式选择

内置客户端使用 stdio，适配生态中以 `npx` 分发的大多数服务器。若服务器通过 HTTP/SSE 暴露，可直接实现 `AgentTool` 包装——适配器是便利设施，不是必需品。
