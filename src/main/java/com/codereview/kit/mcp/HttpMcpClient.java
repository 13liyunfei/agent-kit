package com.codereview.kit.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP Streamable HTTP 客户端（JDK HttpClient）。
 *
 * <p>对齐 2025-03-26 MCP 规范的 HTTP 传输：POST JSON-RPC 2.0，
 * 兼容纯 JSON 与 SSE 两种响应；支持 tools / resources / prompts 全量方法。
 */
public class HttpMcpClient implements Closeable {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final String endpoint;
    private final String apiKey; // 可空（无鉴权服务器）
    private final long timeoutMs;
    private final AtomicLong idSeq = new AtomicLong(1);
    private volatile String sessionId;

    public HttpMcpClient(String endpoint, String apiKey) {
        this(endpoint, apiKey, 60_000);
    }

    public HttpMcpClient(String endpoint, String apiKey, long timeoutMs) {
        this.endpoint = endpoint;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutMs;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    /** 握手：initialize（可选，多数服务器不要求也可直接 listTools）。 */
    public void initialize() throws IOException {
        JsonNode resp = call(Map.of("method", "initialize", "params", Map.of(
                "protocolVersion", "2025-03-26",
                "capabilities", Map.of(),
                "clientInfo", Map.of("name", "agent-kit-http", "version", "0.1.0"))));
        if (resp.path("result").hasNonNull("serverInfo")) {
            JsonNode session = resp.path("result").path("_meta").path("sessionId");
            if (session.isTextual()) {
                sessionId = session.asText();
            }
        }
        call(Map.of("method", "notifications/initialized", "params", Map.of()), true);
    }

    public List<McpTool> listTools() throws IOException {
        JsonNode resp = call(Map.of("method", "tools/list", "params", Map.of()));
        List<McpTool> out = new ArrayList<>();
        for (JsonNode t : resp.path("result").path("tools")) {
            out.add(new McpTool(t.path("name").asText(), t.path("description").asText(),
                    MAPPER.convertValue(t.path("inputSchema"), Map.class)));
        }
        return out;
    }

    public String callTool(String name, Map<String, Object> args) throws IOException {
        JsonNode resp = call(Map.of("method", "tools/call",
                "params", Map.of("name", name, "arguments", args == null ? Map.of() : args)));
        StringBuilder sb = new StringBuilder();
        for (JsonNode c : resp.path("result").path("content")) {
            if ("text".equals(c.path("type").asText())) {
                sb.append(c.path("text").asText());
            }
        }
        return sb.toString();
    }

    public List<McpResource> listResources() throws IOException {
        JsonNode resp = call(Map.of("method", "resources/list", "params", Map.of()));
        List<McpResource> out = new ArrayList<>();
        for (JsonNode r : resp.path("result").path("resources")) {
            out.add(new McpResource(r.path("uri").asText(), r.path("name").asText(),
                    r.path("description").asText(null), r.path("mimeType").asText(null)));
        }
        return out;
    }

    public String readResource(String uri) throws IOException {
        JsonNode resp = call(Map.of("method", "resources/read", "params", Map.of("uri", uri)));
        StringBuilder sb = new StringBuilder();
        for (JsonNode c : resp.path("result").path("contents")) {
            if (c.hasNonNull("text")) {
                sb.append(c.path("text").asText());
            }
        }
        return sb.toString();
    }

    public List<McpPrompt> listPrompts() throws IOException {
        JsonNode resp = call(Map.of("method", "prompts/list", "params", Map.of()));
        List<McpPrompt> out = new ArrayList<>();
        for (JsonNode p : resp.path("result").path("prompts")) {
            out.add(new McpPrompt(p.path("name").asText(), p.path("description").asText(null)));
        }
        return out;
    }

    /** 发一次 JSON-RPC 请求（自动 id）。 */
    public JsonNode call(Map<String, Object> methodParams) throws IOException {
        return call(methodParams, false);
    }

    private JsonNode call(Map<String, Object> methodParams, boolean isNotification) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jsonrpc", "2.0");
        if (!isNotification) {
            body.put("id", idSeq.getAndIncrement());
        }
        body.putAll(methodParams);
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)));
            if (apiKey != null && !apiKey.isBlank()) {
                b.header("Authorization", "Bearer " + apiKey);
            }
            if (sessionId != null) {
                b.header("Mcp-Session-Id", sessionId);
            }
            HttpResponse<java.io.InputStream> resp = http.send(b.build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 400) {
                throw new IOException("MCP HTTP " + resp.statusCode() + ": "
                        + new String(resp.body().readAllBytes(), StandardCharsets.UTF_8));
            }
            String contentType = resp.headers().firstValue("Content-Type").orElse("");
            String session = resp.headers().firstValue("Mcp-Session-Id").orElse(null);
            if (session != null && !session.isBlank()) {
                sessionId = session;
            }
            String bodyStr = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
            if (isNotification || bodyStr.isBlank()) {
                return MAPPER.createObjectNode();
            }
            if (contentType.contains("text/event-stream")) {
                return parseSse(bodyStr);
            }
            return MAPPER.readTree(bodyStr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("MCP 调用被中断", e);
        }
    }

    private static JsonNode parseSse(String sse) throws IOException {
        BufferedReader r = new BufferedReader(
                new InputStreamReader(new java.io.ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)),
                        StandardCharsets.UTF_8));
        String line;
        while ((line = r.readLine()) != null) {
            if (line.startsWith("data:")) {
                String data = line.substring(5).trim();
                if (!data.isEmpty() && !"[DONE]".equals(data)) {
                    return MAPPER.readTree(data);
                }
            }
        }
        throw new IOException("MCP SSE 响应中未找到 data 帧");
    }

    @Override
    public void close() {
        // 无连接池需显式关闭；HttpClient 由 JVM 管理
    }
}
