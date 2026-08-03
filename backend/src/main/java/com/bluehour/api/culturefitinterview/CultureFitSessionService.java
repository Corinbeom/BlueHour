package com.bluehour.api.culturefitinterview;

import com.bluehour.api.culturefitinterview.dto.CultureFitSessionCreateRequest;
import com.bluehour.common.ConflictException;
import com.bluehour.common.ForbiddenException;
import com.bluehour.common.ResourceNotFoundException;
import com.bluehour.domain.culturefitinterview.model.CultureFitFeedback;
import com.bluehour.domain.culturefitinterview.model.CultureFitFeedbackStatus;
import com.bluehour.domain.culturefitinterview.model.CultureFitQuestion;
import com.bluehour.domain.culturefitinterview.model.CultureFitSession;
import com.bluehour.domain.culturefitinterview.model.CultureFitSessionStatus;
import com.bluehour.domain.culturefitinterview.port.CultureFitSessionRepository;
import com.bluehour.domain.member.model.Member;
import com.bluehour.domain.member.port.MemberRepository;
import com.bluehour.domain.resume.session.port.InterviewAiPort;
import com.bluehour.domain.resume.session.port.UrlTextFetcherPort;
import com.bluehour.domain.resume.session.service.PositionPromptRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CultureFitSessionService {

    private static final String CULTURE_FIT_POSITION = "CULTURE_FIT";
    private static final int MAX_SCRAPED_TEXT_CHARS = 5000;

    private final CultureFitSessionRepository sessionRepo;
    private final MemberRepository memberRepo;
    private final InterviewAiPort aiPort;
    private final PositionPromptRegistry promptRegistry;
    private final UrlTextFetcherPort urlTextFetcher;

    public CultureFitSessionService(
            CultureFitSessionRepository sessionRepo,
            MemberRepository memberRepo,
            InterviewAiPort aiPort,
            PositionPromptRegistry promptRegistry,
            UrlTextFetcherPort urlTextFetcher
    ) {
        this.sessionRepo = sessionRepo;
        this.memberRepo = memberRepo;
        this.aiPort = aiPort;
        this.promptRegistry = promptRegistry;
        this.urlTextFetcher = urlTextFetcher;
    }

    public String scrape(String url) {
        String text = urlTextFetcher.fetch(url);
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("URL에서 텍스트를 찾을 수 없습니다.");
        }
        return truncate(text, MAX_SCRAPED_TEXT_CHARS);
    }

    public CultureFitSession createSession(Long memberId, CultureFitSessionCreateRequest request) {
        Member member = memberRepo.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Member를 찾을 수 없습니다. id=" + memberId));

        CultureFitSession session = new CultureFitSession(
                member,
                request.companyName(),
                request.companyCultureText(),
                request.jobDescriptionText(),
                request.positionType()
        );

        String systemInstruction = promptRegistry.systemInstructionFor(CULTURE_FIT_POSITION);
        List<InterviewAiPort.GeneratedQuestion> generatedQuestions = aiPort.generateCultureFitQuestions(
                systemInstruction,
                request.companyCultureText(),
                request.jobDescriptionText()
        );

        int index = 0;
        for (InterviewAiPort.GeneratedQuestion generated : generatedQuestions) {
            session.addQuestion(new CultureFitQuestion(
                    index++,
                    generated.badge(),
                    generated.likelihood(),
                    generated.question(),
                    generated.intention(),
                    generated.keywords(),
                    generated.modelAnswer()
            ));
        }

        return sessionRepo.save(session);
    }

    @Transactional(readOnly = true)
    public List<CultureFitSession> listSessions(Long memberId) {
        return sessionRepo.findByMemberIdOrderByCreatedAtDesc(memberId);
    }

    @Transactional(readOnly = true)
    public CultureFitSession getSession(Long memberId, Long sessionId) {
        return findAndAuthorize(memberId, sessionId);
    }

    @Transactional(readOnly = true)
    public List<CultureFitQuestion> getQuestions(Long memberId, Long sessionId) {
        return findAndAuthorize(memberId, sessionId).getQuestions();
    }

    public CultureFitQuestion submitAnswer(Long memberId, Long sessionId, Long questionId, String answerText) {
        CultureFitSession session = findAndAuthorize(memberId, sessionId);
        ensureEditable(session);
        CultureFitQuestion question = findQuestion(session, questionId);
        question.submitAnswer(answerText);
        session.markInProgress();
        sessionRepo.save(session);
        return question;
    }

    public CultureFitQuestion generateFeedback(Long memberId, Long sessionId, Long questionId) {
        CultureFitSession session = findAndAuthorize(memberId, sessionId);
        ensureEditable(session);
        CultureFitQuestion question = findQuestion(session, questionId);
        if (question.getAnswerText() == null || question.getAnswerText().isBlank()) {
            throw new IllegalArgumentException("피드백 생성 전에 답변을 먼저 제출해야 합니다.");
        }

        String systemInstruction = promptRegistry.systemInstructionFor(CULTURE_FIT_POSITION);
        try {
            InterviewAiPort.GeneratedCultureFitFeedback generated = aiPort.generateCultureFitFeedback(
                    systemInstruction,
                    session.getCompanyCultureText(),
                    session.getJobDescriptionText(),
                    question.getQuestionText(),
                    question.getAnswerText()
            );
            question.completeFeedback(new CultureFitFeedback(
                    generated.strengths(),
                    generated.improvements(),
                    generated.suggestedAnswer(),
                    generated.followups(),
                    generated.alignmentNote()
            ));
        } catch (RuntimeException e) {
            question.failFeedback();
            sessionRepo.save(session);
            throw e;
        }

        session.markInProgress();
        sessionRepo.save(session);
        return question;
    }

    public CultureFitSession completeSession(Long memberId, Long sessionId) {
        CultureFitSession session = findAndAuthorize(memberId, sessionId);
        if (session.getStatus() == CultureFitSessionStatus.COMPLETED) {
            return session;
        }
        boolean hasIncomplete = session.getQuestions().stream()
                .anyMatch(question -> question.getFeedbackStatus() != CultureFitFeedbackStatus.COMPLETED);
        if (hasIncomplete) {
            throw new ConflictException("아직 완료되지 않은 피드백이 있습니다.");
        }
        session.complete();
        return sessionRepo.save(session);
    }

    private CultureFitSession findAndAuthorize(Long memberId, Long sessionId) {
        CultureFitSession session = sessionRepo.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("CultureFitSession을 찾을 수 없습니다. id=" + sessionId));
        if (!session.getMember().getId().equals(memberId)) {
            throw new ForbiddenException("해당 세션에 접근할 권한이 없습니다.");
        }
        return session;
    }

    private CultureFitQuestion findQuestion(CultureFitSession session, Long questionId) {
        return session.getQuestions().stream()
                .filter(question -> question.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("CultureFitQuestion을 찾을 수 없습니다. id=" + questionId));
    }

    private void ensureEditable(CultureFitSession session) {
        if (session.getStatus() == CultureFitSessionStatus.COMPLETED) {
            throw new ConflictException("완료된 세션은 수정할 수 없습니다.");
        }
    }

    private static String truncate(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) return text;
        return text.substring(0, maxChars);
    }
}
