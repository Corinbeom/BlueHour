package com.bluehour.infra.ai.gemini;

import com.bluehour.domain.assistant.AssistantStreamException;
import com.bluehour.domain.assistant.port.AssistantAiPort;
import com.bluehour.domain.assistant.port.AssistantChatTurn;
import com.bluehour.domain.assistant.port.AssistantContext;
import com.bluehour.infra.ai.AssistantPromptBuilder;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
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
import java.util.stream.Stream;

@Component
public class GeminiAssistantAdapter implements AssistantAiPort {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int requestTimeoutSeconds;
    private final int maxOutputTokens;

    public GeminiAssistantAdapter(
            ObjectMapper objectMapper,
            @Value("${bluehour.gemini.api-key:}") String apiKey,
            @Value("${bluehour.gemini.model:gemini-3.5-flash-lite}") String model,
            @Value("${bluehour.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${bluehour.gemini.request-timeout-seconds:90}") int requestTimeoutSeconds,
            @Value("${bluehour.gemini.max-output-tokens:8192}") int maxOutputTokens
    ) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
        this.maxOutputTokens = Math.max(256, maxOutputTokens);
    }

    @Override
    public void stream(AssistantContext context, String message, List<AssistantChatTurn> history, TokenSink sink) {
        requireApiKey();

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", systemInstruction(AssistantPromptBuilder.buildSystemPrompt(context)));
        body.put("contents", contents(message, history));
        body.put("generationConfig", Map.of(
                "maxOutputTokens", Math.min(maxOutputTokens, 2048)
        ));

        String endpoint = baseUrl.replaceAll("/+$", "")
                + "/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + apiKey;

        HttpResponse<Stream<String>> response = postStreaming(endpoint, body);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String bodyText = readErrorBody(response);
            if (response.statusCode() == 429) {
                throw new AssistantStreamException("rate_limit", "Gemini 호출 제한(429): " + truncate(bodyText, 500));
            }
            throw new AssistantStreamException("upstream_error", "Gemini 스트리밍 호출 실패. status=" + response.statusCode());
        }

        try (Stream<String> lines = response.body()) {
            lines.forEach(line -> handleLine(line, sink));
        }
    }

    private HttpResponse<Stream<String>> postStreaming(String endpoint, Map<String, Object> body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            return httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
        } catch (java.net.http.HttpTimeoutException e) {
            throw new AssistantStreamException("timeout", "Gemini 스트리밍 요청 시간이 초과되었습니다.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssistantStreamException("cancelled", "Gemini 스트리밍 요청이 취소되었습니다.", e);
        } catch (IOException e) {
            throw new AssistantStreamException("upstream_error", "Gemini 스트리밍 요청에 실패했습니다.", e);
        }
    }

    private void handleLine(String line, TokenSink sink) {
        if (line == null || line.isBlank() || !line.startsWith("data:")) return;
        String data = line.substring("data:".length()).trim();
        if (data.isBlank()) return;
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode parts = root.at("/candidates/0/content/parts");
            if (!parts.isArray()) return;
            for (JsonNode part : parts) {
                JsonNode text = part.get("text");
                if (text != null && !text.isNull() && !text.asText().isEmpty()) {
                    sink.token(text.asText());
                }
            }
        } catch (IOException e) {
            throw new AssistantStreamException("upstream_error", "Gemini SSE 청크 파싱에 실패했습니다.", e);
        }
    }

    private static List<Map<String, Object>> contents(String message, List<AssistantChatTurn> history) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (AssistantChatTurn turn : history) {
            result.add(content(toGeminiRole(turn.role()), turn.content()));
        }
        result.add(content("user", message));

        if (!result.isEmpty() && "model".equals(result.get(0).get("role"))) {
            result.add(0, content("user", "이전 대화의 이어지는 답변입니다."));
        }
        return result;
    }

    private static String toGeminiRole(AssistantChatTurn.Role role) {
        return role == AssistantChatTurn.Role.ASSISTANT ? "model" : "user";
    }

    private static Map<String, Object> content(String role, String text) {
        return Map.of(
                "role", role,
                "parts", List.of(Map.of("text", text))
        );
    }

    private static Map<String, Object> systemInstruction(String text) {
        return Map.of("parts", List.of(Map.of("text", text)));
    }

    private static String readErrorBody(HttpResponse<Stream<String>> response) {
        if (response.body() == null) return "";
        try (Stream<String> lines = response.body()) {
            return truncate(String.join("\n", lines.toList()), 1000);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key가 설정되지 않았습니다. env 또는 application.yml에 bluehour.gemini.api-key를 설정하세요.");
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) return value == null ? "" : value;
        return value.substring(0, maxLength) + "...";
    }
}
