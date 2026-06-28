package com.bluehour.api.culturefitinterview.dto;

import jakarta.validation.constraints.NotBlank;

public record CultureFitScrapeRequest(
        @NotBlank(message = "url은 필수입니다.")
        String url
) {
}
