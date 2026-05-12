package com.liveklass.enrollment.lecture.presentation.dto;

import com.liveklass.enrollment.lecture.domain.LectureStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateLectureStatusRequest(
    @Schema(description = "전이할 상태 (DRAFT→OPEN→CLOSED 단방향)", example = "OPEN") @NotNull LectureStatus status
) {
}
