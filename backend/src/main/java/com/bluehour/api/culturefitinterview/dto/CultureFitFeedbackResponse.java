package com.bluehour.api.culturefitinterview.dto;

import com.bluehour.domain.culturefitinterview.model.CultureFitFeedback;

import java.util.List;

public record CultureFitFeedbackResponse(
        List<String> strengths,
        List<String> improvements,
        String suggestedAnswer,
        List<String> followups,
        String alignmentNote
) {
    public static CultureFitFeedbackResponse from(CultureFitFeedback feedback) {
        if (feedback == null) return null;
        return new CultureFitFeedbackResponse(
                feedback.getStrengths(),
                feedback.getImprovements(),
                feedback.getSuggestedAnswer(),
                feedback.getFollowups(),
                feedback.getAlignmentNote()
        );
    }
}
