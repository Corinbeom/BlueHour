package com.bluehour.infra.ai.gemini;

import com.bluehour.domain.assistant.AssistantStreamException;
import com.bluehour.domain.assistant.port.AssistantChatTurn;
import com.bluehour.domain.assistant.port.AssistantContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiAssistantAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Gemini SSE 응답을 토큰으로 스트리밍한다")
    void stream_parsesSseTokens() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        startServer(exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    data: {"candidates":[{"content":{"parts":[{"text":"안"}]}}]}

                    data: {"candidates":[{"content":{"parts":[{"text":"녕"}]}}]}

                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        GeminiAssistantAdapter adapter = adapter();
        List<String> tokens = new ArrayList<>();

        adapter.stream(
                context(),
                "이번 주 계획 알려줘",
                List.of(new AssistantChatTurn(AssistantChatTurn.Role.ASSISTANT, "첫 진단")),
                tokens::add
        );

        assertThat(tokens).containsExactly("안", "녕");
        JsonNode body = objectMapper.readTree(requestBody.get());
        assertThat(body.at("/contents/0/role").asText()).isEqualTo("user");
        assertThat(body.at("/contents/1/role").asText()).isEqualTo("model");
        assertThat(body.at("/contents/2/role").asText()).isEqualTo("user");
        assertThat(body.at("/systemInstruction/parts/0/text").asText()).contains("[데이터]");
    }

    @Test
    @DisplayName("Gemini 429 응답은 rate_limit 스트림 오류로 매핑한다")
    void stream_rateLimit() throws Exception {
        startServer(exchange -> {
            byte[] response = "{\"error\":{\"message\":\"quota\"}}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(429, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        GeminiAssistantAdapter adapter = adapter();

        assertThatThrownBy(() -> adapter.stream(context(), "질문", List.of(), token -> {}))
                .isInstanceOf(AssistantStreamException.class)
                .extracting(ex -> ((AssistantStreamException) ex).getCode())
                .isEqualTo("rate_limit");
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler::handle);
        server.start();
    }

    private GeminiAssistantAdapter adapter() {
        return new GeminiAssistantAdapter(
                objectMapper,
                "test-key",
                "gemini-test",
                "http://127.0.0.1:" + server.getAddress().getPort(),
                5,
                8192
        );
    }

    private static AssistantContext context() {
        return new AssistantContext(
                "홍길동",
                List.of("백엔드 개발자"),
                new AssistantContext.RecruitmentSnapshot(0, Map.of(), List.of()),
                new AssistantContext.ResumeSnapshot(0, -1),
                new AssistantContext.InterviewSnapshot(0, 0, 0.0),
                List.of(),
                new AssistantContext.QuizSnapshot(0, List.of())
        );
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
