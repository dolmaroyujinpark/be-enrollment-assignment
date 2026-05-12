package com.liveklass.enrollment.lecture.presentation.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateLectureRequest(
    @Schema(description = "강의 제목", example = "Spring Boot 백엔드 부트캠프") @NotBlank @Size(max = 200) String title,
    @Schema(description = "강의 설명") @Size(max = 5000) String description,
    @Schema(description = "수강료(원)", example = "199000") @NotNull @PositiveOrZero BigDecimal price,
    @Schema(description = "최대 수강 인원", example = "20") @Positive int capacity,
    @Schema(description = "수강 시작일", example = "2026-06-01") @NotNull LocalDate startDate,
    @Schema(description = "수강 종료일(시작일 이후)", example = "2026-07-01") @NotNull LocalDate endDate
) {
}
