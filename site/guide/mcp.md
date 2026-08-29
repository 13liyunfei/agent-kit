# MCP integration

MCP (Model Context Protocol) is the interoperability standard for tools. Rather than reimplementing every integration, connect an MCP server and let its tools enter the normal decision loop.

## Usage

```java
McpClient mcp = McpClient.start("npx", "-y", "@modelcontextprotocol/server-github");
mcp.listTools().forEach(t -> tools.register(new McpToolAdapter(mcp, t)));

ToolCallingLoop loop = new ToolCallingLoop(model, tools, 5);
```

That is the whole integration. MCP tools and your own tools sit in the same registry and are offered to the model identically — the loop cannot tell them apart, and does not need to.

## What the client does

`McpClient` speaks JSON-RPC 2.0 over stdio:

1. `initialize` — handshake
2. `tools/list` — discover what the server offers
3. `tools/call` — invoke a tool and return its result

`McpToolAdapter` wraps each discovered tool as an `AgentTool`, translating name, description, parameter schema and execution.

## The model stays unaware

This is the point worth emphasising: **your model adapter does not change at all**. The tool inventory is assembled by the loop, the observations are appended by the loop, and the gateway keeps forwarding whatever it is given.

```java
ChatModel model = prompt -> myLlmGateway.chat(prompt);   // unchanged
```

Adding MCP changes what tools exist. It does not change how the model is called.

## Choosing a transport

The bundled client uses stdio, which works with the `npx`-distributed servers that make up most of the ecosystem. Servers exposed over HTTP/SSE can be wrapped by implementing `AgentTool` directly — the adapter is a convenience, not a requirement.
