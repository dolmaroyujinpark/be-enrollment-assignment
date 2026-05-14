package com.liveklass.enrollment.lecture.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "lectures")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Lecture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private int capacity;

    @Column(name = "enrolled_count", nullable = false)
    private int enrolledCount;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LectureStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Lecture(Long creatorId, String title, String description, BigDecimal price,
                   int capacity, LocalDate startDate, LocalDate endDate) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (price.signum() < 0) {
            throw new IllegalArgumentException("price must be non-negative");
        }
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate must be before endDate");
        }
        this.creatorId = creatorId;
        this.title = title;
        this.description = description;
        this.price = price;
        this.capacity = capacity;
        this.enrolledCount = 0;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = LectureStatus.DRAFT;
    }

    public void changeStatus(LectureStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalStateException(
                "Invalid status transition: " + status + " -> " + target);
        }
        this.status = target;
    }

    public void incrementEnrolled() {
        if (enrolledCount >= capacity) {
            throw new IllegalStateException("capacity exceeded");
        }
        this.enrolledCount += 1;
    }

    public void decrementEnrolled() {
        if (enrolledCount <= 0) {
            throw new IllegalStateException("enrolledCount cannot go below zero");
        }
        this.enrolledCount -= 1;
    }

    public boolean hasAvailableSeat() {
        return enrolledCount < capacity;
    }
}
