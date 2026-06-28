package com.bluehour.api.culturefitinterview.dto;

import com.bluehour.domain.culturefitinterview.model.CultureFitSession;

import java.util.List;

public record CultureFitSessionResponse(
        Long id,
        String companyName,
        String status,
        String positionType,
        int questionCount,
        String createdAt,
        String completedAt,
        List<CultureFitQuestionResponse> questions
) {
    public static CultureFitSessionResponse from(CultureFitSession session) {
        return new CultureFitSessionResponse(
                session.getId(),
                session.getCompanyName(),
                session.getStatus().name(),
                session.getPositionType(),
                session.getQuestions().size(),
                session.getCreatedAt() == null ? null : session.getCreatedAt().toString(),
                session.getCompletedAt() == null ? null : session.getCompletedAt().toString(),
                session.getQuestions().stream().map(CultureFitQuestionResponse::from).toList()
        );
    }
}
