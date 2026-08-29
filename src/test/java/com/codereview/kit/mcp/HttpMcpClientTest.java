package com.codereview.kit.mcp;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MCP Streamable HTTP 客户端：JDK HttpServer 模拟服务器，覆盖 JSON 与 SSE 响应。
 */
class HttpMcpClientTest {

    private static HttpServer server;
    private static String endpoint;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/mcp", HttpMcpClientTest::handle);
        server.start();
        endpoint = "http://localhost:" + server.getAddress().getPort() + "/mcp";
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String resp;
        if (body.contains("\"tools/list\"")) {
            resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"tools\":["
                    + "{\"name\":\"echo\",\"description\":\"回显\","
                    + "\"inputSchema\":{\"type\":\"object\",\"properties\":{\"text\":{\"type\":\"string\"}}}}]}}";
        } else if (body.contains("\"resources/list\"")) {
            resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"resources\":["
                    + "{\"uri\":\"file:///README.md\",\"name\":\"README\",\"mimeType\":\"text/markdown\"}]}}";
        } else if (body.contains("\"tools/call\"")) {
            resp = "data: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}]}}\n\n";
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
        } else {
            resp = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{\"serverInfo\":{\"name\":\"fake\"}}}";
        }
        byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    void jsonListToolsAndResources() throws IOException {
        HttpMcpClient client = new HttpMcpClient(endpoint, null);
        client.initialize();
        var tools = client.listTools();
        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).name());
        var resources = client.listResources();
        assertEquals(1, resources.size());
        assertTrue(resources.get(0).uri().contains("README"));
        client.close();
    }

    @Test
    void sseToolCallResponseParsed() throws IOException {
        HttpMcpClient client = new HttpMcpClient(endpoint, null);
        String out = client.callTool("echo", java.util.Map.of("text", "hi"));
        assertEquals("hello", out);
        client.close();
    }
}
