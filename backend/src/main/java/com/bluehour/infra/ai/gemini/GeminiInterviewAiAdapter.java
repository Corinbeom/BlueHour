package com.bluehour.infra.ai.gemini;

import com.bluehour.domain.resume.session.port.InterviewAiPort;
import com.bluehour.domain.coach.port.CoachAiPort;
import com.bluehour.domain.studyquiz.session.model.CsQuizDifficulty;
import com.bluehour.domain.studyquiz.session.model.CsQuizQuestionType;
import com.bluehour.domain.studyquiz.session.model.CsQuizTopic;
import com.bluehour.domain.studyquiz.session.port.CsQuizAiPort;
import com.bluehour.common.UpstreamRateLimitException;
import com.bluehour.infra.ai.AiMetrics;
import com.bluehour.infra.ai.AiPromptBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Qualifier("geminiAi")
public class GeminiInterviewAiAdapter implements InterviewAiPort, CsQuizAiPort, CoachAiPort {

    private static final Logger log = LoggerFactory.getLogger(GeminiInterviewAiAdapter.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final AiMetrics aiMetrics;

    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final int requestTimeoutSeconds;
    private final int maxOutputTokens;

    public GeminiInterviewAiAdapter(
            ObjectMapper objectMapper,
            AiMetrics aiMetrics,
            @Value("${bluehour.gemini.api-key:}") String apiKey,
            @Value("${bluehour.gemini.model:gemini-2.5-flash}") String model,
            @Value("${bluehour.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${bluehour.gemini.request-timeout-seconds:90}") int requestTimeoutSeconds,
            @Value("${bluehour.gemini.max-output-tokens:8192}") int maxOutputTokens
    ) {
        this.objectMapper = objectMapper;
        this.aiMetrics = aiMetrics;
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
    public List<GeneratedQuestion> generateQuestions(String systemInstruction, String positionType, String resumeText, String portfolioText, String portfolioUrl, List<String> targetTechnologies) {
        requireApiKey();

        String prompt = AiPromptBuilder.buildQuestionsPrompt(positionType, resumeText, portfolioText, portfolioUrl, targetTechnologies);
        return doGenerateQuestions(systemInstruction, prompt);
    }

    @Override
    public List<GeneratedQuestion> generateQuestionsWithHistory(String systemInstruction, String positionType, String resumeText, String portfolioText, String portfolioUrl, List<String> targetTechnologies, List<String> previousQuestions) {
        requireApiKey();

        String prompt = AiPromptBuilder.buildQuestionsPromptWithHistory(positionType, resumeText, portfolioText, portfolioUrl, targetTechnologies, previousQuestions);
        return doGenerateQuestions(systemInstruction, prompt);
    }

    private List<GeneratedQuestion> doGenerateQuestions(String systemInstruction, String prompt) {
        Map<String, Object> schema = questionResponseSchema();

        JsonNode json = generateStructuredJsonWithRetry(systemInstruction, prompt, schema, RetryProfile.QUESTIONS);
        JsonNode questions = json.get("questions");
        if (questions == null || !questions.isArray()) {
            throw new IllegalStateException("Gemini 응답에 questions 배열이 없습니다.");
        }

        return objectMapper.convertValue(
                questions,
                objectMapper.getTypeFactory().constructCollectionType(List.class, GeneratedQuestion.class)
        );
    }

    @Override
    public InterviewAiPort.GeneratedFeedback generateFeedback(String systemInstruction, String question, String intention, String keywords, String modelAnswer, String answerText) {
        requireApiKey();

        String prompt = AiPromptBuilder.buildFeedbackPrompt(question, intention, keywords, modelAnswer, answerText);
        Map<String, Object> schema = feedbackResponseSchema();

        JsonNode json = generateStructuredJsonWithRetry(systemInstruction, prompt, schema, RetryProfile.FEEDBACK);
        FeedbackPayload payload = parseFeedbackPayload(json);
        return new InterviewAiPort.GeneratedFeedback(payload.strengths(), payload.improvements(), payload.suggestedAnswer(), payload.followups());
    }

    @Override
    public List<GeneratedQuestion> generateCultureFitQuestions(String systemInstruction, String companyCultureText, String jobDescriptionText) {
        requireApiKey();

        String prompt = AiPromptBuilder.buildCultureFitQuestionPrompt(companyCultureText, jobDescriptionText);
        return doGenerateQuestions(systemInstruction, prompt);
    }

    @Override
    public InterviewAiPort.GeneratedCultureFitFeedback generateCultureFitFeedback(
            String systemInstruction,
            String companyCultureText,
            String jobDescriptionText,
            String question,
            String answerText
    ) {
        requireApiKey();

        String prompt = AiPromptBuilder.buildCultureFitFeedbackPrompt(companyCultureText, jobDescriptionText, question, answerText);
        JsonNode json = generateStructuredJsonWithRetry(systemInstruction, prompt, cultureFitFeedbackResponseSchema(), RetryProfile.FEEDBACK);
        try {
            return objectMapper.treeToValue(json, InterviewAiPort.GeneratedCultureFitFeedback.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gemini 컬처핏 피드백 응답 파싱에 실패했습니다.", e);
        }
    }


    @Override
    public List<CsQuizAiPort.GeneratedQuizQuestion> generateQuestions(
            String systemInstruction,
            Set<CsQuizTopic> topics,
            CsQuizDifficulty difficulty,
            int multipleChoiceCount,
            int shortAnswerCount
    ) {
        requireApiKey();
        if (topics == null || topics.isEmpty()) throw new IllegalArgumentException("topics는 1개 이상 필요합니다.");
        if (difficulty == null) throw new IllegalArgumentException("difficulty는 필수입니다.");
        if (multipleChoiceCount < 0 || shortAnswerCount < 0) throw new IllegalArgumentException("count는 0 이상이어야 합니다.");

        List<CsQuizAiPort.GeneratedQuizQuestion> out = new java.util.ArrayList<>();
        if (multipleChoiceCount > 0) {
            out.addAll(generateQuizQuestionsInBatches(systemInstruction, topics, difficulty, CsQuizQuestionType.MULTIPLE_CHOICE, multipleChoiceCount));
        }
        if (shortAnswerCount > 0) {
            out.addAll(generateQuizQuestionsInBatches(systemInstruction, topics, difficulty, CsQuizQuestionType.SHORT_ANSWER, shortAnswerCount));
        }
        return out;
    }

    private List<CsQuizAiPort.GeneratedQuizQuestion> generateQuizQuestionsInBatches(
            String systemInstruction,
            Set<CsQuizTopic> topics,
            CsQuizDifficulty difficulty,
            CsQuizQuestionType type,
            int totalCount
    ) {
        int remaining = totalCount;
        List<CsQuizAiPort.GeneratedQuizQuestion> out = new java.util.ArrayList<>();
        List<String> generatedPrompts = new java.util.ArrayList<>();
        while (remaining > 0) {
            int batch = Math.min(3, remaining);
            String prompt = AiPromptBuilder.buildCsQuizQuestionsPrompt(topics, difficulty, type, batch, generatedPrompts);
            JsonNode json = generateStructuredJsonWithRetry(systemInstruction, prompt, csQuizQuestionResponseSchema(), RetryProfile.QUIZ_QUESTIONS);
            JsonNode questions = json.get("questions");
            if (questions == null || !questions.isArray()) {
                throw new IllegalStateException("Gemini 응답에 questions 배열이 없습니다.");
            }
            List<CsQuizAiPort.GeneratedQuizQuestion> got = objectMapper.convertValue(
                    questions,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, CsQuizAiPort.GeneratedQuizQuestion.class)
            );
            got.forEach(q -> { if (q.prompt() != null) generatedPrompts.add(q.prompt()); });
            out.addAll(got);
            remaining -= got.size();
            if (got.size() == 0) break;
        }
        if (out.size() < totalCount) {
            throw new IllegalStateException("Gemini가 CS 문제를 충분히 생성하지 못했습니다. need=" + totalCount + " got=" + out.size());
        }
        return out.subList(0, totalCount);
    }

    private static Map<String, Object> csQuizQuestionResponseSchema() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("type", "object");
        item.put("properties", Map.of(
                "topic", Map.of("type", "string"),
                "difficulty", Map.of("type", "string"),
                "type", Map.of("type", "string"),
                "prompt", Map.of("type", "string"),
                "choices", Map.of("type", "array", "items", Map.of("type", "string")),
                "correctChoiceIndex", Map.of("type", "integer"),
                "referenceAnswer", Map.of("type", "string"),
                "rubricKeywords", Map.of("type", "array", "items", Map.of("type", "string"))
        ));
        item.put("required", List.of("topic", "difficulty", "type", "prompt", "choices", "correctChoiceIndex", "referenceAnswer", "rubricKeywords"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "questions", Map.of(
                        "type", "array",
                        "items", item,
                        "minItems", 1
                )
        ));
        schema.put("required", List.of("questions"));
        return schema;
    }

    @Override
    public CsQuizAiPort.GeneratedFeedback generateMultipleChoiceFeedback(
            String systemInstruction,
            CsQuizTopic topic,
            CsQuizDifficulty difficulty,
            String question,
            List<String> choices,
            int correctChoiceIndex,
            int selectedChoiceIndex
    ) {
        requireApiKey();
        String prompt = AiPromptBuilder.buildCsMultipleChoiceFeedbackPrompt(topic, difficulty, question, choices, correctChoiceIndex, selectedChoiceIndex);
        JsonNode json = generateStructuredJsonWithRetry(systemInstruction, prompt, feedbackResponseSchema(), RetryProfile.FEEDBACK);
        FeedbackPayload payload = parseFeedbackPayload(json);
        return new CsQuizAiPort.GeneratedFeedback(payload.strengths(), payload.improvements(), payload.suggestedAnswer(), payload.followups());
    }

    @Override
    public CsQuizAiPort.GeneratedFeedback generateShortAnswerFeedback(
            String systemInstruction,
            CsQuizTopic topic,
            CsQuizDifficulty difficulty,
            String question,
            String referenceAnswer,
            List<String> rubricKeywords,
            String userAnswer
    ) {
        requireApiKey();
        String prompt = AiPromptBuilder.buildCsShortAnswerFeedbackPrompt(topic, difficulty, question, referenceAnswer, rubricKeywords, userAnswer);
        JsonNode json = generateStructuredJsonWithRetry(systemInstruction, prompt, feedbackResponseSchema(), RetryProfile.FEEDBACK);
        FeedbackPayload payload = parseFeedbackPayload(json);
        return new CsQuizAiPort.GeneratedFeedback(payload.strengths(), payload.improvements(), payload.suggestedAnswer(), payload.followups());
    }

    @Override
    public InterviewAiPort.GeneratedSessionReport generateSessionReport(String systemInstruction, String sessionData) {
        requireApiKey();

        String prompt = AiPromptBuilder.buildSessionReportPrompt(sessionData);
        Map<String, Object> schema = sessionReportResponseSchema();

        JsonNode json = generateStructuredJsonWithRetry(systemInstruction, prompt, schema, RetryProfile.SESSION_REPORT);
        try {
            return objectMapper.treeToValue(json, InterviewAiPort.GeneratedSessionReport.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gemini 세션 리포트 응답 파싱에 실패했습니다.", e);
        }
    }

    @Override
    public InterviewAiPort.GeneratedCoachingReport generateCoachingReport(String systemInstruction, String coachingData) {
        requireApiKey();

        String prompt = AiPromptBuilder.buildCoachingReportPrompt(coachingData);
        Map<String, Object> schema = coachingReportResponseSchema();

        JsonNode json = generateStructuredJsonWithRetry(systemInstruction, prompt, schema, RetryProfile.COACHING);
        try {
            return objectMapper.treeToValue(json, InterviewAiPort.GeneratedCoachingReport.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gemini 코칭 리포트 응답 파싱에 실패했습니다.", e);
        }
    }

    @Override
    public InterviewAiPort.GeneratedJdMatchAnalysis analyzeJdMatch(String systemInstruction, String resumeText, String portfolioText, String jdText) {
        requireApiKey();

        String prompt = AiPromptBuilder.buildJdMatchPrompt(resumeText, portfolioText, jdText);
        Map<String, Object> schema = jdMatchResponseSchema();

        JsonNode json = generateStructuredJsonWithRetry(systemInstruction, prompt, schema, RetryProfile.JD_MATCH);
        try {
            return objectMapper.treeToValue(json, InterviewAiPort.GeneratedJdMatchAnalysis.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gemini JD 매칭 분석 응답 파싱에 실패했습니다.", e);
        }
    }

    @Override
    public CoachAiPort.GeneratedCoachAnalysis analyzeReadiness(CoachAiPort.CoachContext context) {
        requireApiKey();
        String systemInstruction = """
                당신은 취업 준비 데이터를 기반으로 오늘 할 일을 정하는 AI 코치입니다.
                숫자 지표를 과장하지 말고, 목표 직무에 맞는 짧은 실행 계획만 제시하세요.
                """;
        JsonNode json = generateStructuredJsonWithRetry(
                systemInstruction,
                AiPromptBuilder.buildCoachAnalysisPrompt(context),
                coachAnalysisResponseSchema(),
                RetryProfile.COACH_ANALYSIS
        );
        try {
            return objectMapper.treeToValue(json, CoachAiPort.GeneratedCoachAnalysis.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gemini 코치 분석 응답 파싱에 실패했습니다.", e);
        }
    }

    @Override
    public InterviewAiPort.GeneratedInterviewerTurn conductInterview(
            String systemInstruction,
            String resumeContext,
            String positionType,
            List<InterviewAiPort.ChatMessage> history,
            int turnCount,
            int maxTurns
    ) {
        requireApiKey();

        // 시스템 프롬프트: 이력서 컨텍스트 + positionType 포함
        String enrichedSystem = AiPromptBuilder.buildConversationalInterviewSystemPrompt(positionType, resumeContext)
                + "\n현재 턴: " + turnCount + "/" + maxTurns
                + (turnCount >= maxTurns ? "\n[중요] 최대 턴에 도달했습니다. 반드시 isComplete=true를 반환하세요." : "");

        Map<String, Object> schema = conversationResponseSchema();

        // 히스토리를 멀티턴 contents 배열로 변환 (최근 6턴만)
        List<InterviewAiPort.ChatMessage> trimmedHistory = history.size() > 6
                ? history.subList(history.size() - 6, history.size())
                : history;

        List<Map<String, Object>> contents = new java.util.ArrayList<>(trimmedHistory.stream()
                .map(msg -> content(msg.role(), msg.content()))
                .collect(java.util.stream.Collectors.toList()));

        // Gemini API: contents는 반드시 user role로 시작해야 함
        // 히스토리가 비어있거나 첫 항목이 model이면 시작 트리거 추가
        if (contents.isEmpty() || "model".equals(contents.get(0).get("role"))) {
            contents.add(0, content("user", "면접을 시작해주세요."));
        }

        JsonNode json = generateStructuredJsonMultiTurn(enrichedSystem, contents, schema, RetryProfile.CONVERSATION);
        try {
            return objectMapper.treeToValue(json, InterviewAiPort.GeneratedInterviewerTurn.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gemini 대화형 면접 응답 파싱에 실패했습니다.", e);
        }
    }

    private JsonNode generateStructuredJsonMultiTurn(
            String systemInstruction,
            List<Map<String, Object>> contents,
            Map<String, Object> responseSchema,
            RetryProfile profile
    ) {
        log.debug("Gemini 멀티턴 API 호출 시작: profile={}", profile);
        Timer.Sample timerSample = aiMetrics.startTimer();
        IllegalStateException last = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            String enrichedSystemInstruction = buildEnrichedSystemInstruction(systemInstruction, attempt, profile);

            try {
                JsonNode result = generateStructuredJsonWithContents(enrichedSystemInstruction, contents, responseSchema, tokensForProfile(profile));
                aiMetrics.recordSuccess(timerSample, "gemini", profile.name());
                log.info("Gemini 멀티턴 API 호출 완료: profile={}", profile);
                return result;
            } catch (IllegalStateException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                boolean isJsonFailure = msg.contains("structured JSON 파싱에 실패") || msg.contains("JSON 텍스트를 찾지 못했습니다");
                boolean isRetryableHttp = msg.contains("status=503") || msg.contains("status=502") || msg.contains("status=529");
                if (!isJsonFailure && !isRetryableHttp) throw e;
                aiMetrics.recordRetry("gemini", profile.name());
                if (isRetryableHttp) {
                    log.warn("Gemini 멀티턴 일시적 HTTP 오류, {}초 후 재시도: attempt={}", attempt * 2, attempt);
                    try { Thread.sleep(attempt * 2000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw e; }
                } else {
                    log.warn("Gemini 멀티턴 JSON 파싱 실패, 재시도: attempt={}", attempt);
                }
                last = e;
            }
        }

        throw last == null ? new IllegalStateException("Gemini 멀티턴 structured JSON 생성에 실패했습니다.") : last;
    }

    private JsonNode generateStructuredJsonWithContents(
            String systemInstruction,
            List<Map<String, Object>> contents,
            Map<String, Object> responseSchema,
            int maxOutputTokensForRequest
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", systemInstruction(systemInstruction));
        body.put("contents", contents);
        body.put("generationConfig", Map.of(
                "temperature", 0.7,
                "maxOutputTokens", Math.max(256, maxOutputTokensForRequest),
                "responseMimeType", "application/json",
                "responseSchema", responseSchema
        ));

        String endpoint = baseUrl.replaceAll("/+$", "")
                + "/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        HttpResponse<byte[]> resp = postJson(endpoint, body);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String msg = new String(resp.body() == null ? new byte[0] : resp.body(), StandardCharsets.UTF_8);
            if (resp.statusCode() == 429) {
                int retryAfter = extractRetryAfterSeconds(msg);
                aiMetrics.recordRateLimit("gemini");
                throw new UpstreamRateLimitException("Gemini 호출 제한(429). body=" + truncate(msg, 2000), retryAfter);
            }
            throw new IllegalStateException("Gemini 멀티턴 호출 실패. status=" + resp.statusCode() + " body=" + truncate(msg, 2000));
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(resp.body());
        } catch (IOException e) {
            throw new IllegalStateException("Gemini 멀티턴 응답 JSON 파싱에 실패했습니다.", e);
        }

        String finishReason = root.at("/candidates/0/finishReason").asText(null);
        JsonNode partsNode = root.at("/candidates/0/content/parts");
        String raw;
        if (partsNode != null && partsNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode p : partsNode) {
                JsonNode t = p.get("text");
                if (t != null && !t.isNull()) sb.append(t.asText());
            }
            raw = sb.toString();
        } else {
            JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
            if (textNode.isMissingNode() || textNode.isNull()) {
                throw new IllegalStateException("Gemini 멀티턴 응답에서 JSON 텍스트를 찾지 못했습니다.");
            }
            raw = textNode.asText();
        }

        String normalized = extractJsonObject(raw);
        try {
            return objectMapper.readTree(normalized);
        } catch (IOException e) {
            String reason = (finishReason == null || finishReason.isBlank()) ? "unknown" : finishReason;
            throw new IllegalStateException("Gemini 멀티턴 structured JSON 파싱에 실패했습니다. finishReason=" + reason + " raw=" + truncate(raw, 2000), e);
        }
    }

    private static Map<String, Object> conversationResponseSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "message", Map.of("type", "string", "maxLength", 400),
                "badge", Map.of("type", "string", "nullable", true, "maxLength", 60),
                "isComplete", Map.of("type", "boolean"),
                "intention", Map.of("type", "string", "nullable", true, "maxLength", 300),
                "keywords", Map.of("type", "string", "nullable", true, "maxLength", 200)
        ));
        schema.put("required", List.of("message", "isComplete"));
        return schema;
    }

    private enum RetryProfile {
        QUESTIONS,
        QUIZ_QUESTIONS,
        FEEDBACK,
        SESSION_REPORT,
        COACHING,
        JD_MATCH,
        CONVERSATION,
        COACH_ANALYSIS
    }

    private JsonNode generateStructuredJsonWithRetry(
            String systemInstruction,
            String userPrompt,
            Map<String, Object> responseSchema,
            RetryProfile profile
    ) {
        log.debug("Gemini API 호출 시작: profile={}", profile);
        Timer.Sample timerSample = aiMetrics.startTimer();
        IllegalStateException last = null;

        for (int attempt = 1; attempt <= 3; attempt++) {
            String enrichedSystemInstruction = buildEnrichedSystemInstruction(systemInstruction, attempt, profile);

            try {
                JsonNode result = generateStructuredJson(enrichedSystemInstruction, userPrompt, responseSchema, tokensForProfile(profile));
                aiMetrics.recordSuccess(timerSample, "gemini", profile.name());
                log.info("Gemini API 호출 완료: profile={}", profile);
                return result;
            } catch (IllegalStateException e) {
                String msg = e.getMessage() == null ? "" : e.getMessage();
                boolean isJsonFailure = msg.contains("structured JSON 파싱에 실패") || msg.contains("JSON 텍스트를 찾지 못했습니다");
                boolean isRetryableHttp = msg.contains("status=503") || msg.contains("status=502") || msg.contains("status=529");
                if (!isJsonFailure && !isRetryableHttp) throw e;
                aiMetrics.recordRetry("gemini", profile.name());
                if (isRetryableHttp) {
                    log.warn("Gemini 일시적 HTTP 오류, {}초 후 재시도: attempt={}", attempt * 2, attempt);
                    try { Thread.sleep(attempt * 2000L); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); throw e; }
                } else {
                    log.warn("Gemini JSON 파싱 실패, 재시도: attempt={}", attempt);
                }
                last = e;
            }
        }

        throw last == null ? new IllegalStateException("Gemini structured JSON 생성에 실패했습니다.") : last;
    }

    private int tokensForProfile(RetryProfile profile) {
        int profileLimit = switch (profile) {
            case FEEDBACK -> 2048;
            case QUESTIONS -> maxOutputTokens; // 5문항 × modelAnswer 등 출력량이 크므로 상한 해제
            case JD_MATCH -> 4096;
            case CONVERSATION -> 1024;
            case COACH_ANALYSIS -> 1024;
            default -> 4096;
        };
        return Math.min(maxOutputTokens, profileLimit);
    }

    private String buildEnrichedSystemInstruction(String base, int attempt, RetryProfile profile) {
        String enriched = (base == null ? "" : base) + "\n" + AiPromptBuilder.JSON_FORMAT_RULES;
        return switch (attempt) {
            case 1 -> enriched;
            case 2 -> enriched + retryRulesAttempt2(profile);
            default -> enriched + retryRulesAttempt3(profile);
        };
    }

    private static String retryRulesAttempt2(RetryProfile profile) {
        return switch (profile) {
            case QUESTIONS -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - 질문은 정확히 4개만 출력하세요.
                    - modelAnswer는 350자 이내로 매우 짧게 작성하세요.
                    - intention은 200자 이내로 짧게 작성하세요.
                    - keywords는 120자 이내로 짧게 작성하세요.
                    """;
            case QUIZ_QUESTIONS -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - referenceAnswer는 더 짧게 작성하세요.
                    - rubricKeywords 개수를 줄이세요.
                    """;
            case FEEDBACK -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - strengths/improvements는 최대 3개로 제한하세요.
                    - suggestedAnswer는 500자 이내로 짧게 작성하세요.
                    - followups는 최대 2개로 제한하세요.
                    """;
            case SESSION_REPORT -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - executiveSummary는 300자 이내로 짧게 작성하세요.
                    - topImprovements는 정확히 3개로 유지하세요.
                    - closingAdvice는 200자 이내로 짧게 작성하세요.
                    """;
            case COACHING -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - overallAssessment는 400자 이내로 짧게 작성하세요.
                    - growthTrajectory는 400자 이내로 짧게 작성하세요.
                    - learningPlan은 정확히 3개로 제한하세요.
                    - nextSteps는 300자 이내로 짧게 작성하세요.
                    """;
            case JD_MATCH -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - matchedKeywords는 최대 10개로 제한하세요.
                    - missingKeywords는 최대 8개로 제한하세요.
                    - summary는 200자 이내로 짧게 작성하세요.
                    - recommendations는 최대 3개로 제한하세요.
                    """;
            case CONVERSATION -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - message는 200자 이내로 짧게 작성하세요.
                    """;
            case COACH_ANALYSIS -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - strengths/gaps는 각각 최대 2개로 제한하세요.
                    - plan은 정확히 3개, do는 20자 이내로 짧게 작성하세요.
                    - today는 30자 이내로 짧게 작성하세요.
                    """;
        };
    }

    private static String retryRulesAttempt3(RetryProfile profile) {
        return switch (profile) {
            case QUESTIONS -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - 질문은 정확히 3개만 출력하세요.
                    - modelAnswer는 반드시 빈 문자열("")로 출력하세요. (출력 길이 최우선)
                    - intention은 140자 이내로 아주 짧게 작성하세요.
                    - keywords는 90자 이내로 아주 짧게 작성하세요.
                    """;
            case QUIZ_QUESTIONS -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - prompt/referenceAnswer를 아주 짧게 작성하세요.
                    - rubricKeywords는 3개 이하로 작성하세요.
                    """;
            case FEEDBACK -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - strengths/improvements는 최대 2개로 제한하세요.
                    - suggestedAnswer는 250자 이내로 아주 짧게 작성하세요.
                    - followups는 최대 1개로 제한하세요.
                    """;
            case SESSION_REPORT -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - executiveSummary는 200자 이내로 아주 짧게 작성하세요.
                    - badgeSummary의 strengths/weaknesses는 최대 2개로 제한하세요.
                    - topImprovements는 정확히 3개, description은 150자 이내로 아주 짧게 작성하세요.
                    - closingAdvice는 150자 이내로 아주 짧게 작성하세요.
                    """;
            case COACHING -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - overallAssessment는 300자 이내로 아주 짧게 작성하세요.
                    - growthTrajectory는 300자 이내로 아주 짧게 작성하세요.
                    - persistentStrengths/persistentWeaknesses는 각 최대 3개로 제한하세요.
                    - learningPlan은 정확히 3개, action은 150자 이내로 아주 짧게 작성하세요.
                    - nextSteps는 200자 이내로 아주 짧게 작성하세요.
                    """;
            case JD_MATCH -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - matchedKeywords는 최대 5개로 제한하세요.
                    - missingKeywords는 최대 5개로 제한하세요.
                    - suggestion은 1문장으로만 작성하세요.
                    - summary는 150자 이내로 아주 짧게 작성하세요.
                    - recommendations는 최대 2개로 제한하세요.
                    """;
            case CONVERSATION -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - message는 150자 이내로 아주 짧게 작성하세요.
                    - badge는 20자 이내로 작성하세요.
                    """;
            case COACH_ANALYSIS -> """

                    [RETRY_RULES]
                    - 반드시 유효한 JSON만 출력하세요(중간에 끊기면 안 됩니다).
                    - 모든 배열 항목은 2개 이하로 줄이세요. plan만 3개입니다.
                    - plan.do와 today는 명사형 짧은 행동으로만 작성하세요.
                    """;
        };
    }

    private record FeedbackPayload(
            List<String> strengths,
            List<String> improvements,
            String suggestedAnswer,
            List<String> followups
    ) {
    }

    private record FeedbackWithDeliveryPayload(
            List<String> strengths,
            List<String> improvements,
            String suggestedAnswer,
            List<String> followups,
            List<String> deliveryStrengths,
            List<String> deliveryImprovements
    ) {
    }

    private FeedbackPayload parseFeedbackPayload(JsonNode json) {
        try {
            return objectMapper.treeToValue(json, FeedbackPayload.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Gemini 피드백 응답 파싱에 실패했습니다.", e);
        }
    }

    private JsonNode generateStructuredJson(String systemInstruction, String userPrompt, Map<String, Object> responseSchema, int maxOutputTokensForRequest) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", systemInstruction(systemInstruction));
        body.put("contents", List.of(content("user", userPrompt)));
        body.put("generationConfig", Map.of(
                "temperature", 0.2,
                "maxOutputTokens", Math.max(256, maxOutputTokensForRequest),
                "responseMimeType", "application/json",
                "responseSchema", responseSchema
        ));

        String endpoint = baseUrl.replaceAll("/+$", "")
                + "/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        HttpResponse<byte[]> resp = postJson(endpoint, body);
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            String msg = new String(resp.body() == null ? new byte[0] : resp.body(), StandardCharsets.UTF_8);
            if (resp.statusCode() == 429) {
                int retryAfter = extractRetryAfterSeconds(msg);
                aiMetrics.recordRateLimit("gemini");
                log.warn("Gemini rate limit: retryAfter={}s", retryAfter);
                throw new UpstreamRateLimitException("Gemini 호출 제한(429). 잠시 후 다시 시도하거나, 문항 수를 줄이거나, 무료티어 쿼터를 확인해 주세요. body=" + truncate(msg, 2000), retryAfter);
            }
            throw new IllegalStateException("Gemini 호출 실패. status=" + resp.statusCode() + " body=" + truncate(msg, 2000));
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(resp.body());
        } catch (IOException e) {
            throw new IllegalStateException("Gemini 응답 JSON 파싱에 실패했습니다.", e);
        }

        // Gemini는 출력이 길면 parts를 여러 조각으로 나눠 응답할 수 있다.
        String finishReason = root.at("/candidates/0/finishReason").asText(null);

        JsonNode partsNode = root.at("/candidates/0/content/parts");
        String raw;
        if (partsNode != null && partsNode.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode p : partsNode) {
                JsonNode t = p.get("text");
                if (t != null && !t.isNull()) sb.append(t.asText());
            }
            raw = sb.toString();
        } else {
            JsonNode textNode = root.at("/candidates/0/content/parts/0/text");
            if (textNode.isMissingNode() || textNode.isNull()) {
                throw new IllegalStateException("Gemini 응답에서 JSON 텍스트를 찾지 못했습니다.");
            }
            raw = textNode.asText();
        }

        String normalized = extractJsonObject(raw);
        try {
            return objectMapper.readTree(normalized);
        } catch (IOException e) {
            String reason = (finishReason == null || finishReason.isBlank()) ? "unknown" : finishReason;
            throw new IllegalStateException("Gemini structured JSON 파싱에 실패했습니다. finishReason=" + reason + " raw=" + truncate(raw, 2000), e);
        }
    }

    private HttpResponse<byte[]> postJson(String url, Map<String, Object> body) {
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("요청 JSON 직렬화에 실패했습니다.", e);
        }

        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(Math.max(10, requestTimeoutSeconds)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();

        try {
            return httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini 요청이 중단되었습니다.", e);
        } catch (IOException e) {
            throw new IllegalStateException("Gemini 요청 전송에 실패했습니다.", e);
        }
    }

    private static Map<String, Object> content(String role, String text) {
        return Map.of(
                "role", role,
                "parts", List.of(Map.of("text", text == null ? "" : text))
        );
    }

    private static Map<String, Object> systemInstruction(String text) {
        return Map.of(
                "parts", List.of(Map.of("text", text == null ? "" : text))
        );
    }

    private static Map<String, Object> questionResponseSchema() {
        Map<String, Object> questionItem = new LinkedHashMap<>();
        questionItem.put("type", "object");
        questionItem.put("properties", Map.of(
                "badge", Map.of("type", "string", "maxLength", 100),
                "likelihood", Map.of("type", "integer"),
                "question", Map.of("type", "string", "maxLength", 400),
                "intention", Map.of("type", "string", "nullable", true, "maxLength", 600),
                "keywords", Map.of("type", "string", "nullable", true, "maxLength", 300),
                "modelAnswer", Map.of("type", "string", "nullable", true, "maxLength", 500)
        ));
        questionItem.put("required", List.of("badge", "likelihood", "question", "intention", "keywords", "modelAnswer"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "questions", Map.of(
                        "type", "array",
                        "items", questionItem,
                        "minItems", 1,
                        "maxItems", 8
                )
        ));
        schema.put("required", List.of("questions"));
        return schema;
    }

    private static Map<String, Object> feedbackResponseSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "strengths", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 0, "maxItems", 10),
                "improvements", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 0, "maxItems", 10),
                "suggestedAnswer", Map.of("type", "string", "nullable", true),
                "followups", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 0, "maxItems", 10)
        ));
        schema.put("required", List.of("strengths", "improvements", "suggestedAnswer", "followups"));
        return schema;
    }

    private static Map<String, Object> cultureFitFeedbackResponseSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "strengths", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 0, "maxItems", 10),
                "improvements", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 0, "maxItems", 10),
                "suggestedAnswer", Map.of("type", "string", "nullable", true),
                "followups", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 0, "maxItems", 10),
                "alignmentNote", Map.of("type", "string")
        ));
        schema.put("required", List.of("strengths", "improvements", "suggestedAnswer", "followups", "alignmentNote"));
        return schema;
    }

    private static Map<String, Object> sessionReportResponseSchema() {
        Map<String, Object> improvementItem = new LinkedHashMap<>();
        improvementItem.put("type", "object");
        improvementItem.put("properties", Map.of(
                "title", Map.of("type", "string", "maxLength", 100),
                "description", Map.of("type", "string", "maxLength", 500)
        ));
        improvementItem.put("required", List.of("title", "description"));

        Map<String, Object> badgeSummaryItem = new LinkedHashMap<>();
        badgeSummaryItem.put("type", "object");
        badgeSummaryItem.put("properties", Map.of(
                "badge", Map.of("type", "string", "maxLength", 100),
                "summary", Map.of("type", "string", "maxLength", 500),
                "strengths", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 0, "maxItems", 5),
                "weaknesses", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 0, "maxItems", 5)
        ));
        badgeSummaryItem.put("required", List.of("badge", "summary", "strengths", "weaknesses"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "executiveSummary", Map.of("type", "string", "maxLength", 800),
                "badgeSummaries", Map.of("type", "array", "items", badgeSummaryItem, "minItems", 1, "maxItems", 10),
                "repeatedGaps", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 1, "maxItems", 5),
                "topImprovements", Map.of("type", "array", "items", improvementItem, "minItems", 1, "maxItems", 3),
                "overallScore", Map.of("type", "integer"),
                "closingAdvice", Map.of("type", "string", "maxLength", 500)
        ));
        schema.put("required", List.of("executiveSummary", "badgeSummaries", "repeatedGaps", "topImprovements", "overallScore", "closingAdvice"));
        return schema;
    }

    private static Map<String, Object> coachingReportResponseSchema() {
        Map<String, Object> learningPlanItem = new LinkedHashMap<>();
        learningPlanItem.put("type", "object");
        learningPlanItem.put("properties", Map.of(
                "priority", Map.of("type", "integer"),
                "area", Map.of("type", "string", "maxLength", 100),
                "action", Map.of("type", "string", "maxLength", 500),
                "reason", Map.of("type", "string", "maxLength", 300)
        ));
        learningPlanItem.put("required", List.of("priority", "area", "action", "reason"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "overallAssessment", Map.of("type", "string", "maxLength", 800),
                "growthTrajectory", Map.of("type", "string", "maxLength", 800),
                "persistentStrengths", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 1, "maxItems", 5),
                "persistentWeaknesses", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 1, "maxItems", 5),
                "learningPlan", Map.of("type", "array", "items", learningPlanItem, "minItems", 1, "maxItems", 5),
                "readinessScore", Map.of("type", "integer"),
                "nextSteps", Map.of("type", "string", "maxLength", 600)
        ));
        schema.put("required", List.of("overallAssessment", "growthTrajectory", "persistentStrengths", "persistentWeaknesses", "learningPlan", "readinessScore", "nextSteps"));
        return schema;
    }

    private static Map<String, Object> jdMatchResponseSchema() {
        Map<String, Object> matchedKeywordItem = new LinkedHashMap<>();
        matchedKeywordItem.put("type", "object");
        matchedKeywordItem.put("properties", Map.of(
                "keyword", Map.of("type", "string", "maxLength", 100),
                "category", Map.of("type", "string", "maxLength", 60)
        ));
        matchedKeywordItem.put("required", List.of("keyword", "category"));

        Map<String, Object> missingKeywordItem = new LinkedHashMap<>();
        missingKeywordItem.put("type", "object");
        missingKeywordItem.put("properties", Map.of(
                "keyword", Map.of("type", "string", "maxLength", 100),
                "importance", Map.of("type", "string", "maxLength", 10),
                "suggestion", Map.of("type", "string", "maxLength", 300)
        ));
        missingKeywordItem.put("required", List.of("keyword", "importance", "suggestion"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "matchRate", Map.of("type", "integer"),
                "matchedKeywords", Map.of("type", "array", "items", matchedKeywordItem, "minItems", 0, "maxItems", 20),
                "missingKeywords", Map.of("type", "array", "items", missingKeywordItem, "minItems", 0, "maxItems", 15),
                "summary", Map.of("type", "string", "maxLength", 500),
                "recommendations", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 0, "maxItems", 5)
        ));
        schema.put("required", List.of("matchRate", "matchedKeywords", "missingKeywords", "summary", "recommendations"));
        return schema;
    }

    private static Map<String, Object> coachAnalysisResponseSchema() {
        Map<String, Object> planItem = new LinkedHashMap<>();
        planItem.put("type", "object");
        planItem.put("properties", Map.of(
                "d", Map.of("type", "integer"),
                "do", Map.of("type", "string", "maxLength", 60)
        ));
        planItem.put("required", List.of("d", "do"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of(
                "score", Map.of("type", "integer"),
                "primary", Map.of("type", "string", "maxLength", 100),
                "strengths", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 0, "maxItems", 2),
                "gaps", Map.of("type", "array", "items", Map.of("type", "string"), "minItems", 0, "maxItems", 2),
                "plan", Map.of("type", "array", "items", planItem, "minItems", 3, "maxItems", 3),
                "today", Map.of("type", "string", "maxLength", 100)
        ));
        schema.put("required", List.of("score", "primary", "strengths", "gaps", "plan", "today"));
        return schema;
    }

    private void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key가 설정되지 않았습니다. env 또는 application.yml에 bluehour.gemini.api-key를 설정하세요.");
        }
    }

    private static String extractJsonObject(String raw) {
        if (raw == null) return "{}";
        String s = raw.trim();
        if (s.startsWith("{") && s.endsWith("}")) return s;

        int first = s.indexOf('{');
        int last = s.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return s.substring(first, last + 1);
        }
        // Fallback: Gemini가 순수 JSON을 주도록 설정되어 있으므로 원문 반환
        return s;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }

    private static int extractRetryAfterSeconds(String body) {
        if (body == null) return -1;
        // 1) "Please retry in 1.857s."
        java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("retry in\\s+([0-9]+(?:\\.[0-9]+)?)s", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(body);
        if (m1.find()) {
            try {
                double v = Double.parseDouble(m1.group(1));
                return (int) Math.max(1, Math.ceil(v));
            } catch (NumberFormatException ignored) {
            }
        }
        // 2) JSON field: "retryDelay": "1s"
        java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("\"retryDelay\"\\s*:\\s*\"([0-9]+)s\"")
                .matcher(body);
        if (m2.find()) {
            try {
                return Integer.parseInt(m2.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return -1;
    }
}
