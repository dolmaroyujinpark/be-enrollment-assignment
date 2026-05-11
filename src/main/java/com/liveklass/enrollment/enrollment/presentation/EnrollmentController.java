package com.liveklass.enrollment.enrollment.presentation;

import com.liveklass.enrollment.enrollment.application.EnrollmentService;
import com.liveklass.enrollment.enrollment.domain.Enrollment;
import com.liveklass.enrollment.enrollment.presentation.dto.CreateEnrollmentRequest;
import com.liveklass.enrollment.enrollment.presentation.dto.EnrollmentResponse;
import com.liveklass.enrollment.payment.application.PaymentConfirmService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final PaymentConfirmService paymentConfirmService;

    @PostMapping
    public ResponseEntity<EnrollmentResponse> apply(
        @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody CreateEnrollmentRequest request
    ) {
        Enrollment enrollment = enrollmentService.apply(userId, request.lectureId());
        return ResponseEntity
            .created(URI.create("/api/enrollments/" + enrollment.getId()))
            .body(EnrollmentResponse.from(enrollment));
    }

    @PostMapping("/{id}/payment")
    public EnrollmentResponse confirmPayment(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        Enrollment enrollment = paymentConfirmService.confirm(userId, id, idempotencyKey);
        return EnrollmentResponse.from(enrollment);
    }

    @DeleteMapping("/{id}")
    public EnrollmentResponse cancel(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId
    ) {
        Enrollment enrollment = enrollmentService.cancel(userId, id);
        return EnrollmentResponse.from(enrollment);
    }
}
