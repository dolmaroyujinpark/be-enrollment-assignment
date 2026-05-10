package com.liveklass.enrollment.lecture.presentation.dto;

import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.domain.LectureStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record LectureResponse(
    Long id,
    Long creatorId,
    String title,
    String description,
    BigDecimal price,
    int capacity,
    int enrolledCount,
    int availableSeats,
    LocalDate startDate,
    LocalDate endDate,
    LectureStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static LectureResponse from(Lecture lecture) {
        return new LectureResponse(
            lecture.getId(),
            lecture.getCreatorId(),
            lecture.getTitle(),
            lecture.getDescription(),
            lecture.getPrice(),
            lecture.getCapacity(),
            lecture.getEnrolledCount(),
            Math.max(0, lecture.getCapacity() - lecture.getEnrolledCount()),
            lecture.getStartDate(),
            lecture.getEndDate(),
            lecture.getStatus(),
            lecture.getCreatedAt(),
            lecture.getUpdatedAt()
        );
    }
}
