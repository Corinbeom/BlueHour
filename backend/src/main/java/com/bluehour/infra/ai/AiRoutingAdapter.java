package com.bluehour.infra.ai;

import com.bluehour.domain.coach.port.CoachAiPort;
import com.bluehour.domain.resume.session.port.InterviewAiPort;
import com.bluehour.domain.studyquiz.session.model.CsQuizDifficulty;
import com.bluehour.domain.studyquiz.session.model.CsQuizTopic;
import com.bluehour.domain.studyquiz.session.port.CsQuizAiPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 기능별 AI 모델 라우팅 어댑터.
 *
 * <ul>
 *   <li>Heavy (긴 컨텍스트, 고추론): Gemini — 면접 질문 생성, JD 매칭, 세션/코칭 리포트, CS 문제 생성</li>
 *   <li>Light (입력 작음, 실시간 응답): Groq — 답변 피드백, CS 피드백</li>
 * </ul>
 */
@Component
@Primary
public class AiRoutingAdapter implements InterviewAiPort, CsQuizAiPort, CoachAiPort {

    private final InterviewAiPort gemini;
    private final InterviewAiPort groq;
    private final CsQuizAiPort geminiQuiz;
    private final CsQuizAiPort groqQuiz;
    private final CoachAiPort geminiCoach;
    private final CoachAiPort groqCoach;

    public AiRoutingAdapter(
            @Qualifier("geminiAi") InterviewAiPort gemini,
            @Qualifier("groqAi") InterviewAiPort groq,
            @Qualifier("geminiAi") CsQuizAiPort geminiQuiz,
            @Qualifier("groqAi") CsQuizAiPort groqQuiz,
            @Qualifier("geminiAi") CoachAiPort geminiCoach,
            @Qualifier("groqAi") CoachAiPort groqCoach
    ) {
        this.gemini = gemini;
        this.groq = groq;
        this.geminiQuiz = geminiQuiz;
        this.groqQuiz = groqQuiz;
        this.geminiCoach = geminiCoach;
        this.groqCoach = groqCoach;
    }

    // ── Heavy → Gemini ──

    @Override
    public List<GeneratedQuestion> generateQuestions(String systemInstruction, String positionType, String resumeText, String portfolioText, String portfolioUrl, List<String> targetTechnologies) {
        return gemini.generateQuestions(systemInstruction, positionType, resumeText, portfolioText, portfolioUrl, targetTechnologies);
    }

    @Override
    public List<GeneratedQuestion> generateQuestionsWithHistory(String systemInstruction, String positionType, String resumeText, String portfolioText, String portfolioUrl, List<String> targetTechnologies, List<String> previousQuestions) {
        return gemini.generateQuestionsWithHistory(systemInstruction, positionType, resumeText, portfolioText, portfolioUrl, targetTechnologies, previousQuestions);
    }

    @Override
    public GeneratedSessionReport generateSessionReport(String systemInstruction, String sessionData) {
        return gemini.generateSessionReport(systemInstruction, sessionData);
    }

    @Override
    public GeneratedCoachingReport generateCoachingReport(String systemInstruction, String coachingData) {
        return gemini.generateCoachingReport(systemInstruction, coachingData);
    }

    @Override
    public GeneratedJdMatchAnalysis analyzeJdMatch(String systemInstruction, String resumeText, String portfolioText, String jdText) {
        return gemini.analyzeJdMatch(systemInstruction, resumeText, portfolioText, jdText);
    }

    @Override
    public InterviewAiPort.GeneratedInterviewerTurn conductInterview(
            String systemInstruction,
            String resumeContext,
            String positionType,
            java.util.List<InterviewAiPort.ChatMessage> history,
            int turnCount,
            int maxTurns
    ) {
        return gemini.conductInterview(systemInstruction, resumeContext, positionType, history, turnCount, maxTurns);
    }

    @Override
    public List<GeneratedQuestion> generateCultureFitQuestions(String systemInstruction, String companyCultureText, String jobDescriptionText) {
        return gemini.generateCultureFitQuestions(systemInstruction, companyCultureText, jobDescriptionText);
    }

    @Override
    public GeneratedCultureFitFeedback generateCultureFitFeedback(String systemInstruction, String companyCultureText, String jobDescriptionText, String question, String answerText) {
        return gemini.generateCultureFitFeedback(systemInstruction, companyCultureText, jobDescriptionText, question, answerText);
    }

    // ── Light → Groq ──

    @Override
    public InterviewAiPort.GeneratedFeedback generateFeedback(String systemInstruction, String question, String intention, String keywords, String modelAnswer, String answerText) {
        return groq.generateFeedback(systemInstruction, question, intention, keywords, modelAnswer, answerText);
    }

    // ── CS Quiz: 문제 생성 → Gemini (지식 정확성), 피드백 → Groq (빠른 응답) ──

    @Override
    public List<CsQuizAiPort.GeneratedQuizQuestion> generateQuestions(String systemInstruction, Set<CsQuizTopic> topics, CsQuizDifficulty difficulty, int multipleChoiceCount, int shortAnswerCount) {
        return geminiQuiz.generateQuestions(systemInstruction, topics, difficulty, multipleChoiceCount, shortAnswerCount);
    }

    @Override
    public CsQuizAiPort.GeneratedFeedback generateMultipleChoiceFeedback(String systemInstruction, CsQuizTopic topic, CsQuizDifficulty difficulty, String question, List<String> choices, int correctChoiceIndex, int selectedChoiceIndex) {
        return groqQuiz.generateMultipleChoiceFeedback(systemInstruction, topic, difficulty, question, choices, correctChoiceIndex, selectedChoiceIndex);
    }

    @Override
    public CsQuizAiPort.GeneratedFeedback generateShortAnswerFeedback(String systemInstruction, CsQuizTopic topic, CsQuizDifficulty difficulty, String question, String referenceAnswer, List<String> rubricKeywords, String userAnswer) {
        return groqQuiz.generateShortAnswerFeedback(systemInstruction, topic, difficulty, question, referenceAnswer, rubricKeywords, userAnswer);
    }

    @Override
    public CoachAiPort.GeneratedCoachAnalysis analyzeReadiness(CoachAiPort.CoachContext context) {
        try {
            return groqCoach.analyzeReadiness(context);
        } catch (RuntimeException e) {
            return geminiCoach.analyzeReadiness(context);
        }
    }
}
