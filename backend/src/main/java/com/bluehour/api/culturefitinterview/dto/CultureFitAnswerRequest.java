package com.bluehour.api.culturefitinterview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CultureFitAnswerRequest(
        @NotBlank(message = "답변은 필수입니다.")
        @Size(max = 5000, message = "답변은 5000자 이하여야 합니다.")
        String answerText
) {
}
