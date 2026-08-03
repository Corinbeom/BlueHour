package com.bluehour.api.culturefitinterview.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotBlank;

public record CultureFitSessionCreateRequest(
        @Size(max = 120, message = "회사명은 120자 이하여야 합니다.")
        String companyName,

        @NotBlank(message = "기업 문화 텍스트는 필수입니다.")
        @Size(min = 100, max = 5000, message = "기업 문화 텍스트는 100자 이상 5000자 이하여야 합니다.")
        String companyCultureText,

        @Size(max = 3000, message = "채용공고는 3000자 이하여야 합니다.")
        String jobDescriptionText,

        @Size(max = 50, message = "포지션 타입은 50자 이하여야 합니다.")
        String positionType
) {
}
