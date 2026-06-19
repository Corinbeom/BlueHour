package com.bluehour.api.studyquiz.session;

import com.bluehour.common.ResourceNotFoundException;
import com.bluehour.common.ForbiddenException;
import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.member.port.MemberRepository;
import com.bluehour.domain.studyquiz.bank.model.CsQuestionBankItem;
import com.bluehour.domain.studyquiz.bank.port.CsQuestionBankRepository;
import com.bluehour.domain.studyquiz.session.model.*;
import com.bluehour.domain.studyquiz.session.port.CsQuizAiPort;
import com.bluehour.domain.studyquiz.session.port.CsQuizSessionRepository;
import com.bluehour.infra.ai.AiTextSanitizer;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import com.bluehour.domain.member.event.MemberDeletedEvent;

import com.bluehour.api.studyquiz.session.dto.CsQuizSessionResponse;
import com.bluehour.api.studyquiz.session.dto.CsQuizStatsResponse;

import java.util.*;

@Service
@Transactional
public class CsQuizSessionService {

    private final CsQuizSessionRepository sessionRepository;
    private final CsQuestionBankRepository bankRepository;
    private final MemberRepository memberRepository;
    private final CsQuizAiPort aiPort;

    public CsQuizSessionService(
            CsQuizSessionRepository sessionRepository,
            CsQuestionBankRepository bankRepository,
            MemberRepository memberRepository,
            CsQuizAiPort aiPort
    ) {
        this.sessionRepository = sessionRepository;
        this.bankRepository = bankRepository;
        this.memberRepository = memberRepository;
        this.aiPort = aiPort;
    }

    @Caching(evict = {
            @CacheEvict(value = "stats", key = "#memberId"),
            @CacheEvict(value = "csQuizSessions", key = "#memberId")
    })
    public CsQuizSession create(Long memberId, String difficultyRaw, List<String> topicsRaw, Integer questionCount, String title) {
        if (memberId == null) throw new IllegalArgumentException("memberId는 필수입니다.");
        if (topicsRaw == null || topicsRaw.isEmpty()) throw new IllegalArgumentException("topics는 1개 이상 필요합니다.");

        int count = (questionCount == null) ? 10 : questionCount;
        if (count < 5 || count > 10) throw new IllegalArgumentException("questionCount는 5~10 입니다.");

        CsQuizDifficulty difficulty = CsQuizDifficulty.from(difficultyRaw);
        Set<CsQuizTopic> topics = new LinkedHashSet<>();
        for (String t : topicsRaw) topics.add(CsQuizTopic.from(t));

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member를 찾을 수 없습니다. id=" + memberId));

        int mcqCount = Math.max(1, (int) Math.floor(count * 0.6));
        int shortCount = count - mcqCount;
        if (shortCount <= 0) shortCount = 1;
        if (mcqCount + shortCount != count) mcqCount = count - shortCount;

        String resolvedTitle = (title == null || title.isBlank())
                ? "CS Quiz (" + difficulty.name() + ")"
                : title;

        CsQuizSession session = new CsQuizSession(member, resolvedTitle, difficulty, topics);

        List<CsQuizQuestion> questions = new ArrayList<>();
        List<CsQuizQuestion> mcqQuestions = pickMultipleChoiceQuestionsWithFallback(topics, difficulty, mcqCount, 0);
        questions.addAll(mcqQuestions);
        questions.addAll(pickShortAnswerQuestionsWithFallback(topics, difficulty, shortCount, questions.size(), mcqQuestions));

        session.markQuestionsReady(questions);
        return sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public CsQuizSession get(Long id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CsQuizSession을 찾을 수 없습니다. id=" + id));
    }

    @Transactional(readOnly = true)
    public CsQuizSession get(Long id, Long memberId) {
        CsQuizSession session = get(id);
        ensureOwner(session, memberId);
        return session;
    }

    @Transactional(readOnly = true)
    public List<CsQuizSession> listByMember(Long memberId) {
        return sessionRepository.findAllByMemberId(memberId);
    }

    @Cacheable(value = "csQuizSessions", key = "#memberId")
    @Transactional(readOnly = true)
    public List<CsQuizSessionResponse> listByMemberCached(Long memberId) {
        return new ArrayList<>(sessionRepository.findAllByMemberId(memberId).stream()
                .map(CsQuizSessionResponse::from)
                .toList());
    }

    @Caching(evict = {
            @CacheEvict(value = "stats", allEntries = true),
            @CacheEvict(value = "csQuizSessions", allEntries = true)
    })
    public void delete(Long id) {
        get(id);
        sessionRepository.deleteById(id);
    }

    @Caching(evict = {
            @CacheEvict(value = "stats", allEntries = true),
            @CacheEvict(value = "csQuizSessions", allEntries = true)
    })
    public void delete(Long id, Long memberId) {
        get(id, memberId);
        sessionRepository.deleteById(id);
    }

    @Cacheable(value = "stats", key = "#memberId")
    @Transactional(readOnly = true)
    public CsQuizStatsResponse getStats(Long memberId) {
        // JPQL 집계 쿼리 1회로 topic별 통계 산출 (엔티티 로딩 없음)
        List<Object[]> rows = sessionRepository.findStatsGroupedByTopic(memberId);

        int totalAttempts = 0;
        int correctCount = 0;
        List<CsQuizStatsResponse.TopicAccuracy> topicAccuracies = new ArrayList<>();

        for (Object[] row : rows) {
            String topic = ((CsQuizTopic) row[0]).name();
            int topicTotal = ((Long) row[1]).intValue();
            int topicCorrect = ((Long) row[2]).intValue();
            totalAttempts += topicTotal;
            correctCount += topicCorrect;
            topicAccuracies.add(new CsQuizStatsResponse.TopicAccuracy(
                    topic, topicTotal, topicCorrect,
                    topicTotal > 0 ? (double) topicCorrect / topicTotal : 0.0
            ));
        }

        double overallAccuracy = totalAttempts > 0 ? (double) correctCount / totalAttempts : 0.0;
        return new CsQuizStatsResponse(totalAttempts, correctCount, overallAccuracy, topicAccuracies);
    }

    private List<CsQuizQuestion> pickMultipleChoiceQuestionsWithFallback(Set<CsQuizTopic> topics, CsQuizDifficulty difficulty, int count, int startIndex) {
        List<CsQuestionBankItem> pool = new ArrayList<>();
        for (CsQuizTopic t : topics) {
            pool.addAll(bankRepository.findAllBy(t, difficulty, CsQuizQuestionType.MULTIPLE_CHOICE));
        }
        Collections.shuffle(pool);

        List<CsQuizQuestion> out = new ArrayList<>();
        int fromBank = Math.min(count, pool.size());
        for (int i = 0; i < fromBank; i++) {
            CsQuestionBankItem item = pool.get(i);
            out.add(CsQuizQuestion.multipleChoice(
                    startIndex + i,
                    item.getTopic(),
                    item.getDifficulty(),
                    item.getPrompt(),
                    item.getChoices(),
                    item.getCorrectChoiceIndex() == null ? 0 : item.getCorrectChoiceIndex(),
                    item.getReferenceAnswer()
            ));
        }
        int remaining = count - fromBank;
        if (remaining > 0) {
            List<CsQuizAiPort.GeneratedQuizQuestion> generated = aiPort.generateQuestions(
                    questionGenSystemInstruction(),
                    topics,
                    difficulty,
                    remaining,
                    0
            );
            List<CsQuestionBankItem> toBank = new ArrayList<>();
            int idx = out.size();
            for (CsQuizAiPort.GeneratedQuizQuestion g : generated) {
                if (g.type() != CsQuizQuestionType.MULTIPLE_CHOICE) continue;
                List<String> choices = g.choices() == null ? List.of() : AiTextSanitizer.sanitizeList(g.choices());
                CsQuizTopic topic = g.topic() == null ? topics.iterator().next() : g.topic();
                String prompt = AiTextSanitizer.sanitize(g.prompt());
                int correctIdx = g.correctChoiceIndex() == null ? 0 : g.correctChoiceIndex();
                String refAnswer = AiTextSanitizer.sanitize(g.referenceAnswer());
                out.add(CsQuizQuestion.multipleChoice(startIndex + idx++, topic, difficulty, prompt, choices, correctIdx, refAnswer));
                if (choices.size() >= 2 && correctIdx >= 0 && correctIdx < choices.size()) {
                    try {
                        toBank.add(CsQuestionBankItem.multipleChoice(topic, difficulty, prompt, choices, correctIdx, refAnswer));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
            if (!toBank.isEmpty()) bankRepository.saveAll(toBank);
            if (out.size() < count) {
                throw new IllegalStateException("객관식 문제 생성이 부족합니다. need=" + count + " got=" + out.size());
            }
        }
        return out;
    }

    private List<CsQuizQuestion> pickShortAnswerQuestionsWithFallback(Set<CsQuizTopic> topics, CsQuizDifficulty difficulty, int count, int startIndex, List<CsQuizQuestion> selectedMcq) {
        List<CsQuestionBankItem> pool = new ArrayList<>();
        for (CsQuizTopic t : topics) {
            pool.addAll(bankRepository.findAllBy(t, difficulty, CsQuizQuestionType.SHORT_ANSWER));
        }
        Collections.shuffle(pool);

        Set<String> mcqTokens = extractKeyTokens(selectedMcq.stream().map(CsQuizQuestion::getPrompt).toList());

        List<CsQuizQuestion> out = new ArrayList<>();
        List<CsQuizQuestion> fallback = new ArrayList<>();

        for (CsQuestionBankItem item : pool) {
            if (out.size() >= count) break;
            CsQuizQuestion q = CsQuizQuestion.shortAnswer(
                    startIndex + out.size() + fallback.size(),
                    item.getTopic(), item.getDifficulty(),
                    item.getPrompt(), item.getRubricKeywords(), item.getReferenceAnswer()
            );
            Set<String> saTokens = extractKeyTokens(List.of(item.getPrompt()));
            long overlap = mcqTokens.stream().filter(saTokens::contains).count();
            if (overlap >= 2) {
                fallback.add(q);
            } else {
                out.add(q);
            }
        }
        // 비중복으로 부족하면 fallback에서 보충
        for (CsQuizQuestion q : fallback) {
            if (out.size() >= count) break;
            out.add(q);
        }

        // orderIndex 재정렬
        List<CsQuizQuestion> result = new ArrayList<>();
        for (int i = 0; i < out.size(); i++) {
            CsQuizQuestion q = out.get(i);
            result.add(CsQuizQuestion.shortAnswer(
                    startIndex + i,
                    q.getTopic(), q.getDifficulty(),
                    q.getPrompt(), q.getRubricKeywords(), q.getReferenceAnswer()
            ));
        }
        int remaining = count - result.size();
        if (remaining > 0) {
            List<CsQuizAiPort.GeneratedQuizQuestion> generated = aiPort.generateQuestions(
                    questionGenSystemInstruction(),
                    topics,
                    difficulty,
                    0,
                    remaining
            );
            List<CsQuestionBankItem> toBank = new ArrayList<>();
            int idx = result.size();
            for (CsQuizAiPort.GeneratedQuizQuestion g : generated) {
                if (g.type() != CsQuizQuestionType.SHORT_ANSWER) continue;
                CsQuizTopic topic = g.topic() == null ? topics.iterator().next() : g.topic();
                String prompt = AiTextSanitizer.sanitize(g.prompt());
                List<String> keywords = AiTextSanitizer.sanitizeList(g.rubricKeywords());
                String refAnswer = AiTextSanitizer.sanitize(g.referenceAnswer());
                result.add(CsQuizQuestion.shortAnswer(startIndex + idx++, topic, difficulty, prompt, keywords, refAnswer));
                try {
                    toBank.add(CsQuestionBankItem.shortAnswer(topic, difficulty, prompt, keywords, refAnswer));
                } catch (IllegalArgumentException ignored) {}
            }
            if (!toBank.isEmpty()) bankRepository.saveAll(toBank);
            if (result.size() < count) {
                throw new IllegalStateException("주관식 문제 생성이 부족합니다. need=" + count + " got=" + result.size());
            }
        }
        return result;
    }

    /** 프롬프트에서 의미 있는 토큰(2자 이상) 추출 — MCQ/SA 중복 감지용 */
    private static Set<String> extractKeyTokens(List<String> prompts) {
        Set<String> tokens = new java.util.HashSet<>();
        for (String prompt : prompts) {
            if (prompt == null) continue;
            for (String word : prompt.split("[\\s,?!.·]+")) {
                String clean = word.replaceAll("[^가-힣a-zA-Z0-9]", "");
                if (clean.length() >= 2) tokens.add(clean);
            }
        }
        return tokens;
    }

    private static String questionGenSystemInstruction() {
        return """
                [언어 규칙] 모든 출력은 반드시 한국어로만 작성하세요. 영어, 중국어, 일본어 등 다른 언어는 절대 사용하지 마세요.
                당신은 CS 면접 대비 문제를 생성하는 출제자입니다.
                사실/개념 오류가 없도록 보수적으로 작성하고, 질문은 명확하고 애매하지 않게 만드세요.
                출력은 반드시 지정된 JSON 스키마만 따릅니다.
                """;
    }

    @EventListener
    public void onMemberDeleted(MemberDeletedEvent event) {
        listByMember(event.memberId()).forEach(session -> delete(session.getId()));
    }

    private void ensureOwner(CsQuizSession session, Long memberId) {
        if (!session.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("퀴즈 세션에 접근할 권한이 없습니다.");
        }
    }
}
