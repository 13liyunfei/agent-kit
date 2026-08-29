package com.codereview.kit.model;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * OpenAI 兼容适配器：用 JDK HttpServer 模拟网关，覆盖文本 / 原生工具调用 / SSE 流式 / embedding。
 */
class OpenAiChatModelTest {

    private static HttpServer server;
    private static String baseUrl;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/chat/completions", OpenAiChatModelTest::handleChat);
        server.createContext("/embeddings", OpenAiChatModelTest::handleEmbeddings);
        server.start();
        baseUrl = "http://localhost:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private static void handleChat(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String resp;
        if (body.contains("\"stream\":true")) {
            resp = "data: {\"choices\":[{\"delta\":{\"content\":\"你好\"}}]}\n\n"
                    + "data: {\"choices\":[{\"delta\":{\"content\":\"，世界\"}}]}\n\n"
                    + "data: [DONE]\n\n";
            ex.getResponseHeaders().set("Content-Type", "text/event-stream");
        } else if (body.contains("\"tools\"")) {
            resp = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"\","
                    + "\"tool_calls\":[{\"id\":\"c1\",\"type\":\"function\","
                    + "\"function\":{\"name\":\"current_time\",\"arguments\":\"{}\"}}]}}],"
                    + "\"usage\":{\"prompt_tokens\":12,\"completion_tokens\":3}}";
        } else {
            resp = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"模型回复\"}}],"
                    + "\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}}";
        }
        byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void handleEmbeddings(HttpExchange ex) throws IOException {
        String resp = "{\"data\":[{\"embedding\":[0.1,0.2,0.3]},{\"embedding\":[0.4,0.5,0.6]}]}";
        byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    void chatReturnsContentAndUsage() {
        OpenAiChatModel model = new OpenAiChatModel(baseUrl, "test-key", "gpt-4o-mini");
        NativeResult r = model.chat(List.of(NativeMessage.user("hi")), List.of(), NativeOptions.defaults());
        assertEquals("模型回复", r.content());
        assertEquals(10, r.inputTokens());
        assertEquals(5, r.outputTokens());
        assertTrue(r.cost() > 0);
    }

    @Test
    void nativeToolCallingParsesToolCalls() {
        OpenAiChatModel model = new OpenAiChatModel(baseUrl, "test-key", "gpt-4o-mini");
        var tools = List.of(new com.codereview.kit.model.ToolSchema("current_time", "当前时间",
                Map.of("type", "object", "properties", Map.of(), "required", List.of())));
        NativeResult r = model.chat(List.of(NativeMessage.user("几点了")), tools, NativeOptions.defaults());
        assertTrue(r.wantsToolCall());
        assertEquals("current_time", r.toolCalls().get(0).name());
    }

    @Test
    void sseStreamingAssemblesChunks() {
        OpenAiChatModel model = new OpenAiChatModel(baseUrl, "test-key", "gpt-4o-mini");
        var pub = model.stream(List.of(NativeMessage.user("hi")), List.of(), false);
        String text = OpenAiChatModel.await(pub, 5000);
        assertEquals("你好，世界", text);
    }

    @Test
    void embeddingModelParsesVectors() {
        OpenAiEmbeddingModel emb = new OpenAiEmbeddingModel(baseUrl, "test-key", "text-embedding-3-small");
        List<List<Float>> all = emb.embedAll(List.of("a", "b"));
        assertEquals(2, all.size());
        assertEquals(3, all.get(0).size());
        assertEquals(0.3f, all.get(0).get(2), 1e-6);
    }
}
