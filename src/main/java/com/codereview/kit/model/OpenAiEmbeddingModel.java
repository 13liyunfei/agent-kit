package com.codereview.kit.model;

import com.codereview.kit.rag.EmbeddingModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容 Embedding 适配器（JDK HttpClient）。
 *
 * <p>默认 text-embedding-3-small，维度 1536；兼容任意 OpenAI 规范 embedding 端点。
 */
public class OpenAiEmbeddingModel implements EmbeddingModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final HttpClient http;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final long timeoutMs;

    public OpenAiEmbeddingModel(String apiKey) {
        this("https://api.openai.com/v1", apiKey, "text-embedding-3-small");
    }

    public OpenAiEmbeddingModel(String baseUrl, String apiKey, String model) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.model = model;
        this.timeoutMs = 60_000;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    }

    @Override
    public List<Float> embed(String text) {
        List<List<Float>> all = embedAll(List.of(text));
        return all.isEmpty() ? List.of() : all.get(0);
    }

    @Override
    public List<List<Float>> embedAll(List<String> texts) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("input", texts);
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/embeddings"))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode node = MAPPER.readTree(resp.body());
            if (resp.statusCode() >= 400) {
                throw new RuntimeException("Embedding API " + resp.statusCode() + ": "
                        + node.path("error").path("message").asText(resp.body()));
            }
            List<List<Float>> out = new ArrayList<>();
            for (JsonNode e : node.path("data")) {
                List<Float> vec = new ArrayList<>();
                e.path("embedding").forEach(v -> vec.add((float) v.asDouble()));
                out.add(vec);
            }
            return out;
        } catch (IOException e) {
            throw new RuntimeException("Embedding 调用 IO 异常: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Embedding 调用被中断", e);
        }
    }
}
