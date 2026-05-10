package com.liveklass.enrollment.lecture.presentation.dto;

import com.liveklass.enrollment.lecture.domain.LectureStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateLectureStatusRequest(
    @NotNull LectureStatus status
) {
}
