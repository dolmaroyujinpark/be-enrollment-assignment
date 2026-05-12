package com.liveklass.enrollment.enrollment.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record CreateEnrollmentRequest(
    @Schema(description = "신청할 강의 id", example = "4") @NotNull Long lectureId
) {
}
