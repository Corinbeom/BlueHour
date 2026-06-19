package com.bluehour.api.resume.session;

import com.bluehour.common.ResourceNotFoundException;
import com.bluehour.common.ForbiddenException;
import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.member.port.MemberRepository;
import com.bluehour.domain.resume.model.Resume;
import com.bluehour.domain.resume.model.ResumeExtractStatus;
import com.bluehour.domain.resume.port.ResumeRepository;
import com.bluehour.domain.resume.session.model.ResumeQuestion;
import com.bluehour.domain.resume.session.model.ResumeSession;
import com.bluehour.domain.resume.session.model.StoredFileRef;
import com.bluehour.domain.resume.session.port.FileStoragePort;
import com.bluehour.domain.resume.session.port.ResumeSessionRepository;
import com.bluehour.domain.resume.session.port.TextExtractorPort;
import com.bluehour.domain.resume.session.port.UrlTextFetcherPort;
import com.bluehour.domain.resume.session.port.InterviewAiPort;
import com.bluehour.domain.resume.session.service.QuestionGenerator;
import com.bluehour.api.resume.session.dto.JdMatchAnalysisResponse;
import com.bluehour.api.resume.session.dto.JdMatchRequest;
import com.bluehour.api.resume.session.dto.ResumeInterviewStatsResponse;
import com.bluehour.api.resume.session.dto.ResumeInterviewStatsResponse.BadgeStats;
import com.bluehour.api.resume.session.dto.ResumeInterviewStatsResponse.FrequentItem;
import com.bluehour.api.resume.session.dto.ResumeInterviewStatsResponse.WeeklyTrend;
import com.bluehour.api.resume.session.dto.ResumeSessionResponse;
import com.bluehour.api.resume.session.dto.SessionReportResponse;
import com.bluehour.api.resume.session.dto.CoachingReportResponse;
import com.bluehour.domain.resume.model.InterviewQuestion;
import com.bluehour.domain.resume.session.model.ResumeAnswerAttempt;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.event.EventListener;
import com.bluehour.domain.member.event.MemberDeletedEvent;

import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Transactional
public class ResumeSessionService {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024;

    private final ResumeSessionRepository sessionRepository;
    private final MemberRepository memberRepository;
    private final ResumeRepository resumeRepository;
    private final FileStoragePort fileStorage;
    private final TextExtractorPort textExtractor;
    private final UrlTextFetcherPort urlTextFetcher;
    private final QuestionGenerator questionGenerator;
    private final InterviewAiPort interviewAiPort;
    private final ObjectMapper objectMapper;

    public ResumeSessionService(
            ResumeSessionRepository sessionRepository,
            MemberRepository memberRepository,
            ResumeRepository resumeRepository,
            FileStoragePort fileStorage,
            TextExtractorPort textExtractor,
            UrlTextFetcherPort urlTextFetcher,
            QuestionGenerator questionGenerator,
            InterviewAiPort interviewAiPort,
            ObjectMapper objectMapper
    ) {
        this.sessionRepository = sessionRepository;
        this.memberRepository = memberRepository;
        this.resumeRepository = resumeRepository;
        this.fileStorage = fileStorage;
        this.textExtractor = textExtractor;
        this.urlTextFetcher = urlTextFetcher;
        this.questionGenerator = questionGenerator;
        this.interviewAiPort = interviewAiPort;
        this.objectMapper = objectMapper;
    }

    @CacheEvict(value = {"resumeSessions", "resumeInterviewStats"}, allEntries = true)
    public ResumeSession create(
            Long memberId,
            String positionTypeRaw,
            String title,
            MultipartFile resumeFile,
            MultipartFile portfolioFile,
            String portfolioUrl
    ) {
        if (resumeFile == null || resumeFile.isEmpty()) throw new IllegalArgumentException("resumeFile은 필수입니다.");
        validateSize(resumeFile);
        if (portfolioFile != null && !portfolioFile.isEmpty()) validateSize(portfolioFile);

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member를 찾을 수 없습니다. id=" + memberId));

        String positionType = positionTypeRaw.trim().toUpperCase();
        String resolvedTitle = (title == null || title.isBlank())
                ? (resumeFile.getOriginalFilename() == null ? "resume" : resumeFile.getOriginalFilename())
                : title;

        ResumeSession session = new ResumeSession(member, positionType, resolvedTitle, portfolioUrl);

        byte[] resumeBytes = readBytes(resumeFile);
        StoredFileRef resumeRef = fileStorage.save(resumeBytes, resumeFile.getOriginalFilename(), resumeFile.getContentType());

        byte[] portfolioBytes = null;
        StoredFileRef portfolioRef = null;
        if (portfolioFile != null && !portfolioFile.isEmpty()) {
            portfolioBytes = readBytes(portfolioFile);
            portfolioRef = fileStorage.save(portfolioBytes, portfolioFile.getOriginalFilename(), portfolioFile.getContentType());
        }

        session.attachFiles(resumeRef, portfolioRef);

        String resumeText = textExtractor.extract(resumeBytes, resumeFile.getContentType());
        String portfolioText = buildPortfolioText(portfolioBytes, portfolioFile, portfolioUrl);

        session.markExtracted(resumeText, portfolioText);

        List<ResumeQuestion> questions = questionGenerator.generate(positionType, resumeText, portfolioText, portfolioUrl, List.of());
        session.markQuestionsReady(questions);

        return sessionRepository.save(session);
    }

    @CacheEvict(value = {"resumeSessions", "resumeInterviewStats"}, allEntries = true)
    public ResumeSession createFromResume(
            Long memberId,
            String positionTypeRaw,
            String title,
            Long resumeId,
            Long portfolioResumeId,
            String portfolioUrl,
            List<String> targetTechnologies
    ) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member를 찾을 수 없습니다. id=" + memberId));

        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume를 찾을 수 없습니다. id=" + resumeId));
        ensureResumeOwner(resume, memberId);
        if (resume.getExtractStatus() != ResumeExtractStatus.EXTRACTED) {
            throw new IllegalArgumentException("텍스트 추출이 완료되지 않은 이력서입니다.");
        }

        String positionType = positionTypeRaw.trim().toUpperCase();
        String resolvedTitle = (title == null || title.isBlank()) ? resume.getTitle() : title;

        ResumeSession session = new ResumeSession(member, positionType, resolvedTitle, portfolioUrl);

        StoredFileRef resumeRef = resume.getStoredFile();
        StoredFileRef portfolioRef = null;
        String resumeText = resume.getExtractedText();
        String portfolioText = null;

        if (portfolioResumeId != null) {
            Resume portfolio = resumeRepository.findById(portfolioResumeId)
                    .orElseThrow(() -> new ResourceNotFoundException("Portfolio Resume를 찾을 수 없습니다. id=" + portfolioResumeId));
            ensureResumeOwner(portfolio, memberId);
            if (portfolio.getExtractStatus() == ResumeExtractStatus.EXTRACTED) {
                portfolioRef = portfolio.getStoredFile();
                portfolioText = portfolio.getExtractedText();
            }
        }

        if (portfolioUrl != null && !portfolioUrl.isBlank()) {
            String fromUrl = urlTextFetcher.fetch(portfolioUrl);
            if (fromUrl != null && !fromUrl.isBlank()) {
                portfolioText = portfolioText != null
                        ? portfolioText + "\n\n---\n\n" + fromUrl
                        : fromUrl;
            }
        }

        session.attachFiles(resumeRef, portfolioRef);
        session.markExtracted(resumeText, portfolioText);

        List<String> safeTechs = targetTechnologies != null ? targetTechnologies : List.of();
        List<ResumeQuestion> questions = questionGenerator.generate(positionType, resumeText, portfolioText, portfolioUrl, safeTechs);
        session.markQuestionsReady(questions);

        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public ResumeSession get(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ResumeSession을 찾을 수 없습니다. id=" + id));
    }

    @Transactional(readOnly = true)
    public ResumeSessionResponse getResponse(Long id) {
        ResumeSession session = sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ResumeSession을 찾을 수 없습니다. id=" + id));
        return ResumeSessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public ResumeSessionResponse getResponse(Long id, Long memberId) {
        ResumeSession session = findAndAuthorize(id, memberId);
        return ResumeSessionResponse.from(session);
    }

    @Transactional(readOnly = true)
    public List<ResumeSession> listByMember(Long memberId) {
        return sessionRepository.findAllByMemberId(memberId);
    }

    @Cacheable(value = "resumeSessions", key = "#memberId")
    @Transactional(readOnly = true)
    public List<ResumeSessionResponse> listByMemberCached(Long memberId) {
        return new java.util.ArrayList<>(sessionRepository.findAllByMemberId(memberId).stream()
                .map(ResumeSessionResponse::from)
                .toList());
    }

    @Cacheable(value = "resumeInterviewStats", key = "#memberId")
    @Transactional(readOnly = true)
    public ResumeInterviewStatsResponse getInterviewStats(Long memberId) {
        List<Object[]> rows = sessionRepository.findInterviewStatsGroupedByBadge(memberId);
        Map<String, Long> strengthsMap = toCountMap(sessionRepository.countStrengthsByBadge(memberId));
        Map<String, Long> improvementsMap = toCountMap(sessionRepository.countImprovementsByBadge(memberId));

        Map<String, List<FrequentItem>> topStrengthsMap = toTopItemsMap(sessionRepository.findTopStrengthsByBadge(memberId));
        Map<String, List<FrequentItem>> topImprovementsMap = toTopItemsMap(sessionRepository.findTopImprovementsByBadge(memberId));

        int totalAll = 0;
        int attemptedAll = 0;
        List<BadgeStats> badgeStatsList = new ArrayList<>();

        for (Object[] row : rows) {
            String badge = (String) row[0];
            int total = ((Number) row[1]).intValue();
            int attempted = ((Number) row[2]).intValue();
            long attemptCount = ((Number) row[3]).longValue();

            double practiceRate = total == 0 ? 0.0 : (double) attempted / total;
            double avgStr = attemptCount == 0 ? 0.0 : (double) strengthsMap.getOrDefault(badge, 0L) / attemptCount;
            double avgImp = attemptCount == 0 ? 0.0 : (double) improvementsMap.getOrDefault(badge, 0L) / attemptCount;

            badgeStatsList.add(new BadgeStats(badge, total, attempted, practiceRate, avgStr, avgImp,
                    topStrengthsMap.getOrDefault(badge, List.of()),
                    topImprovementsMap.getOrDefault(badge, List.of())));
            totalAll += total;
            attemptedAll += attempted;
        }

        List<WeeklyTrend> weeklyTrends = buildWeeklyTrends(memberId);

        double overallPracticeRate = totalAll == 0 ? 0.0 : (double) attemptedAll / totalAll;
        return new ResumeInterviewStatsResponse(totalAll, attemptedAll, overallPracticeRate, badgeStatsList, weeklyTrends);
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }

    /**
     * SQL 결과가 badge, text, freq DESC 순 정렬이므로 badge별 첫 3개만 수집.
     */
    private static Map<String, List<FrequentItem>> toTopItemsMap(List<Object[]> rows) {
        Map<String, List<FrequentItem>> map = new LinkedHashMap<>();
        for (Object[] row : rows) {
            String badge = (String) row[0];
            String text = (String) row[1];
            int freq = ((Number) row[2]).intValue();
            List<FrequentItem> items = map.computeIfAbsent(badge, k -> new ArrayList<>());
            if (items.size() < 3) {
                items.add(new FrequentItem(text, freq));
            }
        }
        return map;
    }

    private List<WeeklyTrend> buildWeeklyTrends(Long memberId) {
        Map<LocalDate, Long> dailyAttempts = toDailyMap(sessionRepository.findDailyAttemptCounts(memberId));
        Map<LocalDate, Long> dailyStrengths = toDailyMap(sessionRepository.findDailyStrengthCounts(memberId));
        Map<LocalDate, Long> dailyImprovements = toDailyMap(sessionRepository.findDailyImprovementCounts(memberId));

        // 모든 날짜를 모아서 주간(ISO Monday)으로 그룹
        TreeMap<LocalDate, long[]> weeklyAgg = new TreeMap<>(); // weekStart → [attempts, strengths, improvements]

        for (LocalDate date : dailyAttempts.keySet()) {
            collectWeek(weeklyAgg, date);
        }
        for (LocalDate date : dailyStrengths.keySet()) {
            collectWeek(weeklyAgg, date);
        }
        for (LocalDate date : dailyImprovements.keySet()) {
            collectWeek(weeklyAgg, date);
        }

        for (var entry : dailyAttempts.entrySet()) {
            LocalDate monday = entry.getKey().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weeklyAgg.get(monday)[0] += entry.getValue();
        }
        for (var entry : dailyStrengths.entrySet()) {
            LocalDate monday = entry.getKey().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weeklyAgg.get(monday)[1] += entry.getValue();
        }
        for (var entry : dailyImprovements.entrySet()) {
            LocalDate monday = entry.getKey().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            weeklyAgg.get(monday)[2] += entry.getValue();
        }

        DateTimeFormatter fmt = DateTimeFormatter.ISO_LOCAL_DATE;
        List<WeeklyTrend> trends = new ArrayList<>();
        for (var entry : weeklyAgg.entrySet()) {
            long[] vals = entry.getValue();
            long attempts = vals[0];
            double avgStr = attempts == 0 ? 0.0 : (double) vals[1] / attempts;
            double avgImp = attempts == 0 ? 0.0 : (double) vals[2] / attempts;
            trends.add(new WeeklyTrend(entry.getKey().format(fmt), (int) attempts, avgStr, avgImp));
        }
        return trends;
    }

    private static void collectWeek(TreeMap<LocalDate, long[]> weeklyAgg, LocalDate date) {
        LocalDate monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        weeklyAgg.computeIfAbsent(monday, k -> new long[3]);
    }

    private static Map<LocalDate, Long> toDailyMap(List<Object[]> rows) {
        Map<LocalDate, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            LocalDate date;
            if (row[0] instanceof java.sql.Date sqlDate) {
                date = sqlDate.toLocalDate();
            } else {
                date = LocalDate.parse(row[0].toString());
            }
            map.put(date, ((Number) row[1]).longValue());
        }
        return map;
    }

    @CacheEvict(value = "resumeSessions", allEntries = true)
    public SessionReportResponse generateReport(Long sessionId, Long memberId) {
        ResumeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ResumeSession을 찾을 수 없습니다. id=" + sessionId));
        if (!session.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("세션에 접근할 권한이 없습니다.");
        }
        if (session.getReportJson() != null && !session.getReportJson().isBlank()) {
            return parseReport(session.getReportJson());
        }

        String sessionData = buildSessionDataForReport(session);
        String systemInstruction = "당신은 면접 코치 전문가입니다. 지원자의 면접 연습 데이터를 분석하여 전문적이고 구체적인 회고 리포트를 작성합니다.";

        InterviewAiPort.GeneratedSessionReport generated = interviewAiPort.generateSessionReport(systemInstruction, sessionData);

        try {
            String reportJson = objectMapper.writeValueAsString(generated);
            session.setReportJson(reportJson);
            sessionRepository.save(session);
            return parseReport(reportJson);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("리포트 JSON 직렬화에 실패했습니다.", e);
        }
    }

    @Transactional(readOnly = true)
    public SessionReportResponse getReport(Long sessionId, Long memberId) {
        ResumeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ResumeSession을 찾을 수 없습니다. id=" + sessionId));
        if (!session.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("세션에 접근할 권한이 없습니다.");
        }
        if (session.getReportJson() == null || session.getReportJson().isBlank()) {
            throw new ResourceNotFoundException("아직 생성된 리포트가 없습니다. sessionId=" + sessionId);
        }
        return parseReport(session.getReportJson());
    }

    @Transactional(readOnly = true)
    public CoachingReportResponse getCoachingReport(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member를 찾을 수 없습니다. id=" + memberId));
        if (member.getCoachingReportJson() == null || member.getCoachingReportJson().isBlank()) {
            return null;
        }
        String generatedAt = member.getCoachingReportGeneratedAt() != null
                ? member.getCoachingReportGeneratedAt().withNano(0).toString()
                : null;
        return parseCoachingReport(member.getCoachingReportJson(), generatedAt);
    }

    public CoachingReportResponse generateCoachingReport(Long memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member를 찾을 수 없습니다. id=" + memberId));

        if (!member.canRegenerateCoachingReport()) {
            throw new IllegalArgumentException("코칭 리포트는 24시간에 1회만 재생성할 수 있습니다.");
        }

        List<ResumeSession> sessions = sessionRepository.findAllByMemberId(memberId);
        List<ResumeSession> completedWithReport = sessions.stream()
                .filter(s -> s.getReportJson() != null && !s.getReportJson().isBlank())
                .toList();

        if (completedWithReport.isEmpty()) {
            throw new IllegalArgumentException("AI 코칭 분석을 위해서는 완료된 세션의 AI 리포트가 1개 이상 필요합니다.");
        }

        String coachingData = buildCoachingData(completedWithReport);
        String systemInstruction = "당신은 장기 면접 코칭 전문가입니다. 다수의 면접 연습 세션 데이터를 분석하여 지원자의 성장 궤적을 파악하고, 맞춤형 학습 계획을 제시합니다.";

        InterviewAiPort.GeneratedCoachingReport generated = interviewAiPort.generateCoachingReport(systemInstruction, coachingData);

        try {
            String json = objectMapper.writeValueAsString(generated);
            LocalDateTime now = LocalDateTime.now();
            member.setCoachingReportJson(json);
            member.setCoachingReportGeneratedAt(now);
            memberRepository.save(member);
            return parseCoachingReport(json, now.withNano(0).toString());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("코칭 리포트 JSON 직렬화에 실패했습니다.", e);
        }
    }

    private CoachingReportResponse parseCoachingReport(String json, String generatedAt) {
        try {
            CoachingReportResponse base = objectMapper.readValue(json, CoachingReportResponse.class);
            if (generatedAt == null) return base;
            return new CoachingReportResponse(
                    base.overallAssessment(), base.growthTrajectory(),
                    base.persistentStrengths(), base.persistentWeaknesses(),
                    base.learningPlan(), base.readinessScore(), base.nextSteps(),
                    generatedAt
            );
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("코칭 리포트 JSON 파싱에 실패했습니다.", e);
        }
    }

    private String buildCoachingData(List<ResumeSession> sessions) {
        StringBuilder sb = new StringBuilder();
        sb.append("=== 세션 수: ").append(sessions.size()).append(" ===\n\n");

        for (int i = 0; i < sessions.size(); i++) {
            ResumeSession session = sessions.get(i);
            sb.append("--- 세션 ").append(i + 1).append(" ---\n");
            sb.append("제목: ").append(session.getTitle()).append("\n");
            sb.append("직무: ").append(session.getPositionType()).append("\n");
            sb.append("생성일: ").append(session.getCreatedAt()).append("\n");

            // Parse cached report
            SessionReportResponse report = parseReport(session.getReportJson());
            sb.append("종합 점수: ").append(report.overallScore()).append("/10\n");
            sb.append("종합 평가: ").append(report.executiveSummary()).append("\n");

            if (report.badgeSummaries() != null) {
                for (SessionReportResponse.BadgeSummary badge : report.badgeSummaries()) {
                    sb.append("  [").append(badge.badge()).append("] ").append(badge.summary()).append("\n");
                }
            }
            if (report.repeatedGaps() != null && !report.repeatedGaps().isEmpty()) {
                sb.append("반복 갭: ").append(String.join(", ", report.repeatedGaps())).append("\n");
            }
            if (report.topImprovements() != null) {
                for (SessionReportResponse.Improvement imp : report.topImprovements()) {
                    sb.append("  개선: ").append(imp.title()).append(" - ").append(imp.description()).append("\n");
                }
            }
            sb.append("마무리 조언: ").append(report.closingAdvice()).append("\n\n");
        }

        return sb.toString();
    }

    private SessionReportResponse parseReport(String reportJson) {
        try {
            return objectMapper.readValue(reportJson, SessionReportResponse.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("리포트 JSON 파싱에 실패했습니다.", e);
        }
    }

    private String buildSessionDataForReport(ResumeSession session) {
        StringBuilder sb = new StringBuilder();
        sb.append("세션 제목: ").append(session.getTitle()).append("\n");
        sb.append("지원 직무: ").append(session.getPositionType()).append("\n\n");

        for (ResumeQuestion q : session.getQuestions()) {
            InterviewQuestion vo = q.getInterviewQuestion();
            sb.append("--- 질문 ").append(q.getOrderIndex() + 1).append(" ---\n");
            sb.append("분류(badge): ").append(q.getBadge()).append("\n");
            sb.append("질문: ").append(vo != null ? vo.getQuestion() : "").append("\n");
            sb.append("출제 의도: ").append(vo != null ? vo.getIntention() : "").append("\n");

            List<ResumeAnswerAttempt> attempts = q.getAttempts();
            if (attempts == null || attempts.isEmpty()) {
                sb.append("답변: (미답변)\n\n");
            } else {
                ResumeAnswerAttempt latest = attempts.get(attempts.size() - 1);
                sb.append("답변: ").append(latest.getAnswerText()).append("\n");
                if (latest.getFeedback() != null) {
                    sb.append("강점: ").append(String.join(", ", latest.getFeedback().getStrengths())).append("\n");
                    sb.append("개선점: ").append(String.join(", ", latest.getFeedback().getImprovements())).append("\n");
                }
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    @Transactional(readOnly = true)
    public JdMatchAnalysisResponse analyzeJdMatch(Long memberId, JdMatchRequest request) {
        Resume resume = resumeRepository.findById(request.resumeId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume를 찾을 수 없습니다. id=" + request.resumeId()));
        if (!resume.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("해당 이력서에 접근할 권한이 없습니다.");
        }
        if (resume.getExtractStatus() != ResumeExtractStatus.EXTRACTED || resume.getExtractedText() == null) {
            throw new IllegalArgumentException("텍스트 추출이 완료되지 않은 이력서입니다. id=" + request.resumeId());
        }

        String resumeText = resume.getExtractedText();
        String portfolioText = null;

        if (request.portfolioResumeId() != null) {
            Resume portfolio = resumeRepository.findById(request.portfolioResumeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Portfolio Resume를 찾을 수 없습니다. id=" + request.portfolioResumeId()));
            ensureResumeOwner(portfolio, memberId);
            if (portfolio.getExtractStatus() == ResumeExtractStatus.EXTRACTED && portfolio.getExtractedText() != null) {
                portfolioText = portfolio.getExtractedText();
            }
        }

        String systemInstruction = "당신은 채용 전문 컨설턴트입니다. 이력서와 채용공고를 정밀 분석하여 객관적인 매칭률과 구체적인 개선 방향을 제시합니다.";
        InterviewAiPort.GeneratedJdMatchAnalysis generated = interviewAiPort.analyzeJdMatch(systemInstruction, resumeText, portfolioText, request.jdText());

        List<JdMatchAnalysisResponse.MatchedKeyword> matched = generated.matchedKeywords().stream()
                .map(k -> new JdMatchAnalysisResponse.MatchedKeyword(k.keyword(), k.category()))
                .toList();
        List<JdMatchAnalysisResponse.MissingKeyword> missing = generated.missingKeywords().stream()
                .map(k -> new JdMatchAnalysisResponse.MissingKeyword(k.keyword(), k.importance(), k.suggestion()))
                .toList();

        return new JdMatchAnalysisResponse(generated.matchRate(), matched, missing, generated.summary(), generated.recommendations());
    }

    @CacheEvict(value = {"resumeSessions", "resumeInterviewStats"}, allEntries = true)
    public void delete(Long id) {
        get(id);
        sessionRepository.deleteById(id);
    }

    @CacheEvict(value = {"resumeSessions", "resumeInterviewStats"}, allEntries = true)
    public void delete(Long id, Long memberId) {
        findAndAuthorize(id, memberId);
        sessionRepository.deleteById(id);
    }

    @CacheEvict(value = {"resumeSessions", "resumeInterviewStats"}, allEntries = true)
    public ResumeSessionResponse complete(Long sessionId, Long memberId) {
        ResumeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ResumeSession을 찾을 수 없습니다. id=" + sessionId));
        if (!session.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("세션에 접근할 권한이 없습니다.");
        }
        session.markCompleted();
        sessionRepository.save(session);
        return ResumeSessionResponse.from(session);
    }

    private static void validateSize(MultipartFile file) {
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("파일 크기는 최대 10MB 입니다. filename=" + file.getOriginalFilename());
        }
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalArgumentException("파일을 읽을 수 없습니다. filename=" + file.getOriginalFilename(), e);
        }
    }

    private ResumeSession findAndAuthorize(Long sessionId, Long memberId) {
        ResumeSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("ResumeSession을 찾을 수 없습니다. id=" + sessionId));
        if (!session.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("세션에 접근할 권한이 없습니다.");
        }
        return session;
    }

    private void ensureResumeOwner(Resume resume, Long memberId) {
        if (!resume.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("해당 이력서에 접근할 권한이 없습니다.");
        }
    }

    private String buildPortfolioText(byte[] portfolioBytes, MultipartFile portfolioFile, String portfolioUrl) {
        List<String> parts = new ArrayList<>();

        if (portfolioBytes != null && portfolioFile != null && !portfolioFile.isEmpty()) {
            parts.add(textExtractor.extract(portfolioBytes, portfolioFile.getContentType()));
        }

        if (portfolioUrl != null && !portfolioUrl.isBlank()) {
            String fromUrl = urlTextFetcher.fetch(portfolioUrl);
            if (fromUrl != null && !fromUrl.isBlank()) parts.add(fromUrl);
        }

        if (parts.isEmpty()) return null;
        return String.join("\n\n---\n\n", parts);
    }

    @EventListener
    public void onMemberDeleted(MemberDeletedEvent event) {
        listByMember(event.memberId()).forEach(session -> delete(session.getId()));
    }
}
