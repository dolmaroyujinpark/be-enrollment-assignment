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
import jakarta.persistence.Version;
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

    // 동시 cancel 두 번 같은 stale-write race 를 OptimisticLockingFailure 로 잡는 낙관 락.
    @Version
    private Long version;

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

    /** 취소 기간 제한 없이 취소 (필수 동작). */
    public void cancel(Instant now) {
        cancel(now, null);
    }

    /**
     * 취소. refundWindow 가 주어지면 CONFIRMED 신청은 confirmedAt + refundWindow 이내에만 취소 가능 (선택 구현).
     * refundWindow 가 null 이면 시간 제한 없음.
     */
    public void cancel(Instant now, Duration refundWindow) {
        if (!status.canTransitionTo(EnrollmentStatus.CANCELLED)) {
            throw new IllegalStateException(
                "Cannot cancel enrollment in status " + status);
        }
        if (refundWindow != null && status == EnrollmentStatus.CONFIRMED && confirmedAt != null) {
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
