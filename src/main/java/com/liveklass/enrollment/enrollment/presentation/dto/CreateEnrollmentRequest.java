package com.liveklass.enrollment.enrollment.presentation.dto;

import jakarta.validation.constraints.NotNull;

public record CreateEnrollmentRequest(
    @NotNull Long lectureId
) {
}
