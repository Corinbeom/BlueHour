package com.bluehour.api.assistant;

import com.bluehour.api.assistant.dto.AssistantChatRequest;
import com.bluehour.api.assistant.dto.ChatTurn;
import com.bluehour.common.ResourceNotFoundException;
import com.bluehour.domain.assistant.AssistantStreamException;
import com.bluehour.domain.assistant.port.AssistantAiPort;
import com.bluehour.domain.assistant.port.AssistantChatTurn;
import com.bluehour.domain.assistant.port.AssistantContext;
import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.member.port.MemberRepository;
import com.bluehour.domain.recruitmenttracker.entry.model.RecruitmentEntry;
import com.bluehour.domain.recruitmenttracker.entry.model.RecruitmentStep;
import com.bluehour.domain.recruitmenttracker.entry.port.RecruitmentEntryRepository;
import com.bluehour.domain.resume.model.Resume;
import com.bluehour.domain.resume.model.ResumeExtractStatus;
import com.bluehour.domain.resume.port.ResumeRepository;
import com.bluehour.domain.resume.session.model.ResumeSession;
import com.bluehour.domain.resume.session.port.ResumeSessionRepository;
import com.bluehour.domain.speechinterview.model.SpeechInterviewSession;
import com.bluehour.domain.speechinterview.model.SpeechInterviewStatus;
import com.bluehour.domain.speechinterview.port.SpeechInterviewSessionRepository;
import com.bluehour.domain.studyquiz.session.model.CsQuizTopic;
import com.bluehour.domain.studyquiz.session.port.CsQuizSessionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Transactional(readOnly = true)
public class AssistantService {

    private static final long STREAM_TIMEOUT_MILLIS = 90_000L;
    private static final int MAX_HISTORY_TURNS = 6;
    private static final int MAX_HISTORY_CONTENT_LENGTH = 500;
    private static final int MAX_RESUME_SESSION_SUMMARIES = 3;

    private final MemberRepository memberRepository;
    private final RecruitmentEntryRepository recruitmentEntryRepository;
    private final ResumeRepository resumeRepository;
    private final ResumeSessionRepository resumeSessionRepository;
    private final SpeechInterviewSessionRepository speechInterviewSessionRepository;
    private final CsQuizSessionRepository csQuizSessionRepository;
    private final AssistantAiPort assistantAiPort;
    private final ObjectMapper objectMapper;

    public AssistantService(
            MemberRepository memberRepository,
            RecruitmentEntryRepository recruitmentEntryRepository,
            ResumeRepository resumeRepository,
            ResumeSessionRepository resumeSessionRepository,
            SpeechInterviewSessionRepository speechInterviewSessionRepository,
            CsQuizSessionRepository csQuizSessionRepository,
            AssistantAiPort assistantAiPort,
            ObjectMapper objectMapper
    ) {
        this.memberRepository = memberRepository;
        this.recruitmentEntryRepository = recruitmentEntryRepository;
        this.resumeRepository = resumeRepository;
        this.resumeSessionRepository = resumeSessionRepository;
        this.speechInterviewSessionRepository = speechInterviewSessionRepository;
        this.csQuizSessionRepository = csQuizSessionRepository;
        this.assistantAiPort = assistantAiPort;
        this.objectMapper = objectMapper;
    }

    public SseEmitter chat(Long memberId, AssistantChatRequest request) {
        String message = normalizeMessage(request.message());
        List<AssistantChatTurn> history = normalizeHistory(request.history());
        AssistantContext context = buildContext(memberId);

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<CompletableFuture<?>> streamTask = new AtomicReference<>();
        Runnable cancelStream = () -> {
            closed.set(true);
            CompletableFuture<?> task = streamTask.get();
            if (task != null) {
                task.cancel(true);
            }
        };
        emitter.onCompletion(cancelStream);
        emitter.onTimeout(() -> {
            closed.set(true);
            CompletableFuture<?> task = streamTask.get();
            if (task != null) {
                task.cancel(true);
            }
            sendEvent(emitter, Map.of("error", "timeout"));
            sendEvent(emitter, Map.of("done", true));
            emitter.complete();
        });
        emitter.onError(error -> cancelStream.run());

        CompletableFuture<?> task = CompletableFuture.runAsync(() -> {
            try {
                assistantAiPort.stream(context, message, history, token -> {
                    if (closed.get()) {
                        throw new AssistantStreamException("cancelled", "client disconnected");
                    }
                    sendEvent(emitter, Map.of("token", token));
                });
                if (!closed.get()) {
                    sendEvent(emitter, Map.of("done", true));
                    emitter.complete();
                }
            } catch (AssistantStreamException e) {
                if (!closed.get()) {
                    sendEvent(emitter, Map.of("error", e.getCode()));
                    sendEvent(emitter, Map.of("done", true));
                    emitter.complete();
                }
            } catch (RuntimeException e) {
                if (!closed.get()) {
                    sendEvent(emitter, Map.of("error", "upstream_error"));
                    sendEvent(emitter, Map.of("done", true));
                    emitter.complete();
                }
            }
        });
        streamTask.set(task);
        return emitter;
    }

    public AssistantContext buildContext(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member를 찾을 수 없습니다. id=" + memberId));
        List<RecruitmentEntry> entries = recruitmentEntryRepository.findAllByMemberId(memberId);
        List<Resume> resumes = resumeRepository.findAllByMemberId(memberId);
        List<ResumeSession> resumeSessions = resumeSessionRepository.findAllByMemberId(memberId);
        List<SpeechInterviewSession> interviews = speechInterviewSessionRepository.findByMemberIdOrderByCreatedAtDesc(memberId);
        AssistantContext.QuizSnapshot quiz = quizSnapshot(memberId);

        return new AssistantContext(
                cleanText(member.getDisplayName(), 80),
                cleanDistinct(member.getTargetRoles(), 3, 30),
                new AssistantContext.RecruitmentSnapshot(
                        entries.size(),
                        statusBreakdown(entries),
                        recentTitles(entries, 5)
                ),
                new AssistantContext.ResumeSnapshot(
                        resumes.size(),
                        daysSinceLastAnalysis(lastAnalyzedAt(resumes))
                ),
                new AssistantContext.InterviewSnapshot(
                        interviews.size(),
                        completedSessions(interviews),
                        averageTurns(interviews)
                ),
                resumeSessionSummaries(resumeSessions),
                quiz
        );
    }

    List<AssistantChatTurn> normalizeHistory(List<ChatTurn> turns) {
        if (turns == null || turns.isEmpty()) return List.of();
        int start = Math.max(0, turns.size() - MAX_HISTORY_TURNS);
        List<AssistantChatTurn> normalized = new ArrayList<>();
        for (ChatTurn turn : turns.subList(start, turns.size())) {
            if (turn == null) {
                throw new IllegalArgumentException("history turn은 null일 수 없습니다.");
            }
            AssistantChatTurn.Role role = parseRole(turn.role());
            String content = cleanText(turn.content(), MAX_HISTORY_CONTENT_LENGTH);
            normalized.add(new AssistantChatTurn(role, content));
        }
        return List.copyOf(normalized);
    }

    private static String normalizeMessage(String message) {
        String cleaned = cleanText(message, 2000);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("message는 비어 있을 수 없습니다.");
        }
        return cleaned;
    }

    private static AssistantChatTurn.Role parseRole(String role) {
        if ("user".equals(role)) return AssistantChatTurn.Role.USER;
        if ("assistant".equals(role)) return AssistantChatTurn.Role.ASSISTANT;
        throw new IllegalArgumentException("history role은 user 또는 assistant만 허용됩니다.");
    }

    private static Map<String, Integer> statusBreakdown(List<RecruitmentEntry> entries) {
        Map<RecruitmentStep, Integer> counts = new EnumMap<>(RecruitmentStep.class);
        for (RecruitmentEntry entry : entries) {
            counts.merge(entry.getStep(), 1, Integer::sum);
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        for (RecruitmentStep step : RecruitmentStep.values()) {
            int count = counts.getOrDefault(step, 0);
            if (count > 0) result.put(step.name(), count);
        }
        return result;
    }

    private static List<String> recentTitles(List<RecruitmentEntry> entries, int limit) {
        return entries.stream()
                .sorted(Comparator
                        .comparing(RecruitmentEntry::getAppliedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RecruitmentEntry::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(RecruitmentEntry::getPosition)
                .filter(Objects::nonNull)
                .map(title -> cleanText(title, 50))
                .filter(title -> !title.isBlank())
                .distinct()
                .limit(limit)
                .toList();
    }

    private static List<String> cleanDistinct(List<String> values, int limit, int maxLength) {
        if (values == null || values.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String cleaned = cleanText(value, maxLength);
            if (cleaned.isBlank() || result.contains(cleaned)) continue;
            result.add(cleaned);
            if (result.size() >= limit) break;
        }
        return List.copyOf(result);
    }

    private static LocalDateTime lastAnalyzedAt(List<Resume> resumes) {
        return resumes.stream()
                .filter(resume -> resume.getExtractStatus() == ResumeExtractStatus.EXTRACTED)
                .map(Resume::getCreatedAt)
                .filter(Objects::nonNull)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    private static int daysSinceLastAnalysis(LocalDateTime lastAnalyzedAt) {
        if (lastAnalyzedAt == null) return -1;
        return (int) ChronoUnit.DAYS.between(lastAnalyzedAt.toLocalDate(), LocalDate.now());
    }

    private static int completedSessions(List<SpeechInterviewSession> interviews) {
        return (int) interviews.stream()
                .filter(session -> session.getStatus() == SpeechInterviewStatus.COMPLETED)
                .count();
    }

    private static double averageTurns(List<SpeechInterviewSession> interviews) {
        if (interviews.isEmpty()) return 0.0;
        double average = interviews.stream()
                .mapToInt(session -> session.getQuestions().size())
                .average()
                .orElse(0.0);
        return Math.round(average * 10.0) / 10.0;
    }

    private static List<AssistantContext.ResumeSessionSummary> resumeSessionSummaries(List<ResumeSession> sessions) {
        if (sessions == null || sessions.isEmpty()) return List.of();
        return sessions.stream()
                .sorted(Comparator
                        .comparing(ResumeSession::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ResumeSession::getId, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_RESUME_SESSION_SUMMARIES)
                .map(session -> new AssistantContext.ResumeSessionSummary(
                        cleanText(session.getPositionType(), 50),
                        session.getStatus().name(),
                        session.getCompletedAt() == null ? "" : session.getCompletedAt().toLocalDate().toString(),
                        session.getQuestions().size()
                ))
                .toList();
    }

    private AssistantContext.QuizSnapshot quizSnapshot(Long memberId) {
        List<Object[]> rows = csQuizSessionRepository.findStatsGroupedByTopic(memberId);
        int totalAttempts = 0;
        List<AssistantContext.TopicAccuracy> topicAccuracy = new ArrayList<>();
        for (Object[] row : rows) {
            CsQuizTopic topic = (CsQuizTopic) row[0];
            int topicTotal = ((Long) row[1]).intValue();
            int topicCorrect = ((Long) row[2]).intValue();
            totalAttempts += topicTotal;
            topicAccuracy.add(new AssistantContext.TopicAccuracy(
                    topic.name(),
                    topicTotal,
                    topicTotal > 0 ? round2((double) topicCorrect / topicTotal) : 0.0
            ));
        }
        return new AssistantContext.QuizSnapshot(totalAttempts, List.copyOf(topicAccuracy));
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String cleanText(String value, int maxLength) {
        if (value == null) return "";
        String cleaned = value.trim().replaceAll("\\s+", " ");
        if (cleaned.length() <= maxLength) return cleaned;
        return cleaned.substring(0, maxLength);
    }

    private void sendEvent(SseEmitter emitter, Map<String, Object> payload) {
        try {
            emitter.send(SseEmitter.event().data(toJson(payload)));
        } catch (IOException e) {
            throw new AssistantStreamException("cancelled", "SSE 전송에 실패했습니다.", e);
        }
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("SSE 이벤트 JSON 생성에 실패했습니다.", e);
        }
    }
}
