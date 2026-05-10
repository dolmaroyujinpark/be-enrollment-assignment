package com.liveklass.enrollment.enrollment.domain;

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
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "enrollments")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Enrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "lecture_id", nullable = false)
    private Long lectureId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EnrollmentStatus status;

    @CreatedDate
    @Column(name = "applied_at", nullable = false, updatable = false)
    private Instant appliedAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "payment_intent_id")
    private Long paymentIntentId;

    public Enrollment(Long userId, Long lectureId) {
        this.userId = userId;
        this.lectureId = lectureId;
        this.status = EnrollmentStatus.PENDING;
    }

    public void confirm(Long paymentIntentId, Instant now) {
        if (!status.canTransitionTo(EnrollmentStatus.CONFIRMED)) {
            throw new IllegalStateException(
                "Cannot confirm enrollment in status " + status);
        }
        this.status = EnrollmentStatus.CONFIRMED;
        this.confirmedAt = now;
        this.paymentIntentId = paymentIntentId;
    }

    public void cancel(Instant now, Duration refundWindow) {
        if (!status.canTransitionTo(EnrollmentStatus.CANCELLED)) {
            throw new IllegalStateException(
                "Cannot cancel enrollment in status " + status);
        }
        if (status == EnrollmentStatus.CONFIRMED && confirmedAt != null) {
            Instant deadline = confirmedAt.plus(refundWindow);
            if (now.isAfter(deadline)) {
                throw new IllegalStateException(
                    "Refund window has passed. confirmed=" + confirmedAt + " deadline=" + deadline);
            }
        }
        this.status = EnrollmentStatus.CANCELLED;
        this.cancelledAt = now;
    }
}
