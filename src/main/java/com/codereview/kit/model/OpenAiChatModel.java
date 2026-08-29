package com.codereview.kit.model;
import com.codereview.kit.model.ToolSchema;

import com.codereview.kit.ChatModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
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
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容 Chat 适配器（JDK HttpClient，零外部依赖）。
 *
 * <p>覆盖 OpenAI / 国产 MaaS / 自研网关的兼容端点：
 * <ul>
 *   <li>{@link ChatModel}：文本对话（流式 SSE 推送）</li>
 *   <li>{@link NativeChatModel}：原生 tools 参数 + 工具结果 role + JSON mode（结构化输出）</li>
 *   <li>用量与成本：解析 usage 字段并按 {@link Pricing} 估算</li>
 * </ul>
 */
public class OpenAiChatModel implements ChatModel, NativeChatModel {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatModel.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final double temperature;
    private final int maxTokens;
    private final long timeoutMs;
    private final Pricing pricing;

    public OpenAiChatModel(String apiKey) {
        this("https://api.openai.com/v1", apiKey, "gpt-4o-mini", 0.7, 2048, 60_000);
    }

    public OpenAiChatModel(String baseUrl, String apiKey, String model) {
        this(baseUrl, apiKey, model, 0.7, 2048, 60_000);
    }

    public OpenAiChatModel(String baseUrl, String apiKey, String model,
                           double temperature, int maxTokens, long timeoutMs) {
        this.baseUrl = trimSlash(baseUrl);
        this.apiKey = apiKey;
        this.model = model;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
        this.timeoutMs = timeoutMs;
        this.pricing = Pricing.forModel(model);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String chat(String prompt) {
        NativeResult r = chat(List.of(NativeMessage.system("You are a helpful assistant."),
                NativeMessage.user(prompt)), List.of(), NativeOptions.defaults());
        return r.content() == null ? "" : r.content();
    }

    @Override
    public Flow.Publisher<String> stream(String prompt) {
        return stream(List.of(NativeMessage.system("You are a helpful assistant."),
                NativeMessage.user(prompt)), List.of(), false);
    }

    @Override
    public NativeResult chat(List<NativeMessage> messages, List<ToolSchema> tools, NativeOptions options) {
        Map<String, Object> body = buildBody(messages, tools, options);
        JsonNode resp = post("/chat/completions", body);
        JsonNode choice = resp.path("choices").path(0);
        JsonNode msg = choice.path("message");
        List<NativeToolCall> calls = parseToolCalls(msg.path("tool_calls"));
        JsonNode usage = resp.path("usage");
        int in = usage.path("prompt_tokens").asInt(0);
        int out = usage.path("completion_tokens").asInt(0);
        return new NativeResult(msg.path("content").asText(null), calls,
                in > 0 ? in : null, out > 0 ? out : null,
                in > 0 || out > 0 ? pricing.estimateCost(in, out) : null,
                resp.toString());
    }

    /** 流式原生调用：SSE 逐块推送文本（工具调用按整包返回，暂不逐块流式）。 */
    public Flow.Publisher<String> stream(List<NativeMessage> messages, List<ToolSchema> tools, boolean jsonMode) {
        Map<String, Object> body = buildBody(messages, tools,
                new NativeOptions(null, null, jsonMode, null));
        body.put("stream", true);
        SubmissionPublisher<String> pub = new SubmissionPublisher<>(Runnable::run, 64);
        new Thread(() -> {
            try {
                sse("/chat/completions", body, pub);
            } catch (Exception e) {
                log.warn("[OpenAiChatModel] 流式调用失败：{}", e.getMessage());
                pub.closeExceptionally(e);
            }
        }, "kit-sse").start();
        return pub;
    }

    /** 对话消息转 OpenAI 消息数组。 */
    public static List<Map<String, Object>> toOpenAiMessages(List<NativeMessage> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (NativeMessage m : messages) {
            Map<String, Object> mm = new LinkedHashMap<>();
            mm.put("role", m.role());
            if (m.content() != null) {
                mm.put("content", m.content());
            }
            if (m.toolCallId() != null) {
                mm.put("tool_call_id", m.toolCallId());
            }
            if (m.name() != null) {
                mm.put("name", m.name());
            }
            if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
                List<Map<String, Object>> calls = new ArrayList<>();
                for (NativeToolCall c : m.toolCalls()) {
                    Map<String, Object> fn = new LinkedHashMap<>();
                    fn.put("name", c.name());
                    fn.put("arguments", c.argumentsJson());
                    Map<String, Object> tc = new LinkedHashMap<>();
                    tc.put("id", c.id());
                    tc.put("type", "function");
                    tc.put("function", fn);
                    calls.add(tc);
                }
                mm.put("tool_calls", calls);
            }
            out.add(mm);
        }
        return out;
    }

    private Map<String, Object> buildBody(List<NativeMessage> messages, List<ToolSchema> tools, NativeOptions options) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", toOpenAiMessages(messages));
        double temp = options.temperature() != null ? options.temperature() : temperature;
        int maxTok = options.maxTokens() != null ? options.maxTokens() : maxTokens;
        body.put("temperature", temp);
        body.put("max_tokens", maxTok);
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools.stream().map(ToolSchema::toOpenAiTool).toList());
            body.put("tool_choice", "auto");
        }
        if (options.jsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        return body;
    }

    private JsonNode post(String path, Map<String, Object> body) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = MAPPER.readTree(resp.body());
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("OpenAI API " + resp.statusCode() + ": "
                        + node.path("error").path("message").asText(resp.body()));
            }
            return node;
        } catch (IOException e) {
            throw new RuntimeException("OpenAI 调用 IO 异常: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("OpenAI 调用被中断", e);
        }
    }

    private void sse(String path, Map<String, Object> body, SubmissionPublisher<String> pub) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<java.io.InputStream> resp = http.send(req,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (resp.statusCode() >= 400) {
                String err = new String(resp.body().readAllBytes(), StandardCharsets.UTF_8);
                throw new RuntimeException("OpenAI API " + resp.statusCode() + ": " + err);
            }
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resp.body(), StandardCharsets.UTF_8));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || "[DONE]".equals(data)) {
                    continue;
                }
                JsonNode node = MAPPER.readTree(data);
                String delta = node.path("choices").path(0).path("delta").path("content").asText(null);
                if (delta != null && !delta.isEmpty()) {
                    pub.submit(delta);
                }
            }
            pub.close();
        } catch (Exception e) {
            pub.closeExceptionally(e);
        }
    }

    private static List<NativeToolCall> parseToolCalls(JsonNode arr) {
        List<NativeToolCall> calls = new ArrayList<>();
        if (arr == null || !arr.isArray()) {
            return calls;
        }
        for (JsonNode c : arr) {
            calls.add(new NativeToolCall(c.path("id").asText("call_" + System.nanoTime()),
                    c.path("function").path("name").asText(),
                    c.path("function").path("arguments").asText("{}")));
        }
        return calls;
    }

    private static String trimSlash(String url) {
        return url == null ? "https://api.openai.com/v1" : url.replaceAll("/+$", "");
    }

    /** 便捷：同步超时等待发布者结束（测试 / 聚合用）。 */
    public static String await(Flow.Publisher<String> pub, long timeoutMs) {
        StringBuilder sb = new StringBuilder();
        try {
            var f = new java.util.concurrent.CompletableFuture<String>();
            pub.subscribe(new Flow.Subscriber<>() {
                @Override public void onSubscribe(Flow.Subscription s) {
                    s.request(Long.MAX_VALUE);
                }

                @Override public void onNext(String item) {
                    sb.append(item);
                }

                @Override public void onError(Throwable throwable) {
                    f.completeExceptionally(throwable);
                }

                @Override public void onComplete() {
                    f.complete(sb.toString());
                }
            });
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("流式等待失败: " + e.getMessage(), e);
        }
    }
}
