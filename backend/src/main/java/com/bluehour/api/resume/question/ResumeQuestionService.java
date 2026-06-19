package com.bluehour.api.resume.question;

import com.bluehour.api.resume.question.dto.CreateResumeFeedbackRequest;
import com.bluehour.common.ResourceNotFoundException;
import com.bluehour.domain.resume.model.InterviewQuestion;
import com.bluehour.domain.resume.session.model.Feedback;
import com.bluehour.domain.resume.session.model.ResumeAnswerAttempt;
import com.bluehour.domain.resume.session.model.ResumeQuestion;
import com.bluehour.domain.resume.session.port.ResumeQuestionRepository;
import com.bluehour.domain.resume.session.service.AnswerFeedbackGenerator;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ResumeQuestionService {

    private final ResumeQuestionRepository questionRepository;
    private final AnswerFeedbackGenerator feedbackGenerator;

    public ResumeQuestionService(
            ResumeQuestionRepository questionRepository,
            AnswerFeedbackGenerator feedbackGenerator
    ) {
        this.questionRepository = questionRepository;
        this.feedbackGenerator = feedbackGenerator;
    }

    @CacheEvict(value = "resumeInterviewStats", allEntries = true)
    public ResumeAnswerAttempt createFeedback(Long questionId, String answerText) {
        return createFeedback(questionId, answerText, null);
    }

    @CacheEvict(value = "resumeInterviewStats", allEntries = true)
    public ResumeAnswerAttempt createFeedback(Long questionId, String answerText,
                                               CreateResumeFeedbackRequest.BehavioralMetricsDto metricsDto) {
        ResumeQuestion question = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("ResumeQuestion을 찾을 수 없습니다. id=" + questionId));
        return createFeedback(question, answerText);
    }

    @CacheEvict(value = "resumeInterviewStats", allEntries = true)
    public ResumeAnswerAttempt createFeedback(Long memberId, Long questionId, String answerText,
                                               CreateResumeFeedbackRequest.BehavioralMetricsDto metricsDto) {
        ResumeQuestion question = questionRepository.findByIdAndSessionMemberId(questionId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("ResumeQuestion을 찾을 수 없습니다. id=" + questionId));
        return createFeedback(question, answerText);
    }

    private ResumeAnswerAttempt createFeedback(ResumeQuestion question, String answerText) {
        if (!question.canAttempt()) {
            throw new IllegalArgumentException("최대 답변 횟수(" + ResumeQuestion.MAX_ATTEMPTS + "회)를 초과했습니다.");
        }

        String positionType = question.getSession().getPositionType();
        InterviewQuestion vo = question.getInterviewQuestion();

        Feedback feedback = feedbackGenerator.generate(
                positionType,
                vo == null ? null : vo.getQuestion(),
                vo == null ? null : vo.getIntention(),
                vo == null ? null : vo.getKeywords(),
                vo == null ? null : vo.getModelAnswer(),
                answerText
        );

        ResumeAnswerAttempt attempt = question.addAttempt(answerText, feedback);
        questionRepository.save(question);
        return attempt;
    }
}
