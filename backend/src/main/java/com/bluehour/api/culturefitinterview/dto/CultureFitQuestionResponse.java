package com.bluehour.api.culturefitinterview.dto;

import com.bluehour.domain.culturefitinterview.model.CultureFitQuestion;

public record CultureFitQuestionResponse(
        Long id,
        int orderIndex,
        String badge,
        int likelihood,
        String questionText,
        String intention,
        String keywords,
        String modelAnswer,
        String answerText,
        String feedbackStatus,
        CultureFitFeedbackResponse feedback
) {
    public static CultureFitQuestionResponse from(CultureFitQuestion question) {
        return new CultureFitQuestionResponse(
                question.getId(),
                question.getOrderIndex(),
                question.getBadge(),
                question.getLikelihood(),
                question.getQuestionText(),
                question.getIntention(),
                question.getKeywords(),
                question.getModelAnswer(),
                question.getAnswerText(),
                question.getFeedbackStatus().name(),
                CultureFitFeedbackResponse.from(question.getFeedback())
        );
    }
}
