package com.bluehour.api.studyquiz.question;

import com.bluehour.api.studyquiz.question.dto.CreateCsQuizAttemptRequest;
import com.bluehour.common.ResourceNotFoundException;
import com.bluehour.domain.studyquiz.session.model.CsQuizAttempt;
import com.bluehour.domain.studyquiz.session.model.CsQuizFeedback;
import com.bluehour.domain.studyquiz.session.model.CsQuizQuestion;
import com.bluehour.domain.studyquiz.session.service.CsQuizFeedbackGenerator;
import com.bluehour.domain.studyquiz.session.port.CsQuizQuestionRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional
public class CsQuizQuestionService {

    private final CsQuizQuestionRepository questionRepository;
    private final CsQuizFeedbackGenerator feedbackGenerator;

    public CsQuizQuestionService(CsQuizQuestionRepository questionRepository, CsQuizFeedbackGenerator feedbackGenerator) {
        this.questionRepository = questionRepository;
        this.feedbackGenerator = feedbackGenerator;
    }

    @CacheEvict(value = "stats", allEntries = true)
    public CsQuizAttempt submitAttempt(Long questionId, CreateCsQuizAttemptRequest req) {
        CsQuizQuestion q = questionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("CsQuizQuestion을 찾을 수 없습니다. id=" + questionId));
        return submitAttempt(q, req);
    }

    @CacheEvict(value = "stats", allEntries = true)
    public CsQuizAttempt submitAttempt(Long memberId, Long questionId, CreateCsQuizAttemptRequest req) {
        CsQuizQuestion q = questionRepository.findByIdAndSessionMemberId(questionId, memberId)
                .orElseThrow(() -> new ResourceNotFoundException("CsQuizQuestion을 찾을 수 없습니다. id=" + questionId));
        return submitAttempt(q, req);
    }

    private CsQuizAttempt submitAttempt(CsQuizQuestion q, CreateCsQuizAttemptRequest req) {
        if (!q.canAttempt()) {
            throw new IllegalArgumentException("최대 답변 횟수(" + CsQuizQuestion.MAX_ATTEMPTS + "회)를 초과했습니다.");
        }

        if (q.isMultipleChoice()) {
            if (req.selectedChoiceIndex() == null) throw new IllegalArgumentException("selectedChoiceIndex는 필수입니다.");
            int selected = req.selectedChoiceIndex();
            boolean correct = q.isCorrectChoiceIndex(selected);

            CsQuizFeedback feedback;
            if (correct) {
                feedback = new CsQuizFeedback(
                        List.of("정답입니다. 핵심 개념을 잘 이해하고 있어요."),
                        List.of(),
                        q.getReferenceAnswer(),
                        List.of()
                );
            } else {
                feedback = feedbackGenerator.generateForMultipleChoice(
                        q.getTopic(),
                        q.getDifficulty(),
                        q.getPrompt(),
                        q.getChoices(),
                        q.getCorrectChoiceIndexForGrading(),
                        selected
                );
            }

            CsQuizAttempt attempt = q.addAttempt(null, selected, correct, feedback);
            questionRepository.save(q);
            return attempt;
        }

        // short answer
        String answer = req.answerText();
        if (answer == null || answer.isBlank()) throw new IllegalArgumentException("answerText는 필수입니다.");

        CsQuizFeedback feedback = feedbackGenerator.generateForShortAnswer(
                q.getTopic(),
                q.getDifficulty(),
                q.getPrompt(),
                q.getReferenceAnswer(),
                q.getRubricKeywords(),
                answer
        );
        CsQuizAttempt attempt = q.addAttempt(answer, null, null, feedback);
        questionRepository.save(q);
        return attempt;
    }
}
