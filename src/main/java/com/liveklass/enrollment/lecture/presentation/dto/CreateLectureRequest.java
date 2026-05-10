package com.liveklass.enrollment.lecture.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateLectureRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 5000) String description,
    @NotNull @PositiveOrZero BigDecimal price,
    @Positive int capacity,
    @NotNull LocalDate startDate,
    @NotNull LocalDate endDate
) {
}
