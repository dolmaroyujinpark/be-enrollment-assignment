package com.liveklass.enrollment.enrollment.presentation;

import com.liveklass.enrollment.common.dto.PageResponse;
import com.liveklass.enrollment.enrollment.application.EnrollmentService;
import com.liveklass.enrollment.enrollment.domain.Enrollment;
import com.liveklass.enrollment.enrollment.presentation.dto.CreateEnrollmentRequest;
import com.liveklass.enrollment.enrollment.presentation.dto.EnrollmentResponse;
import com.liveklass.enrollment.payment.application.PaymentConfirmService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Tag(name = "수강 신청", description = "수강 신청·결제 확정·취소·내 신청 목록")
@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;
    private final PaymentConfirmService paymentConfirmService;

    @Operation(summary = "수강 신청", description = "OPEN 강의에 신청한다(PENDING 생성). 정원 초과 시 거부, 동일 강의 중복 신청 불가. (헤더 X-User-Id 필요)")
    @ApiResponse(responseCode = "201", description = "수강 신청 생성 성공 (Location 헤더에 새 리소스 경로)")
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

    @Operation(summary = "결제 확정", description = "PENDING→CONFIRMED. 헤더 Idempotency-Key 필수 — 같은 키로 재호출하면 상태 변경 없이 동일 응답. 본인 신청만. (헤더 X-User-Id, Idempotency-Key 필요)")
    @PostMapping("/{id}/payment")
    public EnrollmentResponse confirmPayment(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("Idempotency-Key") String idempotencyKey
    ) {
        Enrollment enrollment = paymentConfirmService.confirm(userId, id, idempotencyKey);
        return EnrollmentResponse.from(enrollment);
    }

    @Operation(summary = "수강 취소", description = "→CANCELLED. 본인 신청만 취소 가능. CONFIRMED 신청은 결제 후 7일 이내만. 활성 신청이 취소되면 대기열 다음 사람이 자동 승급. (헤더 X-User-Id 필요)")
    @DeleteMapping("/{id}")
    public EnrollmentResponse cancel(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId
    ) {
        Enrollment enrollment = enrollmentService.cancel(userId, id);
        return EnrollmentResponse.from(enrollment);
    }

    @Operation(summary = "내 수강 신청 목록", description = "페이지네이션(page/size). 최신순(id desc). (헤더 X-User-Id 필요)")
    @GetMapping("/me")
    public PageResponse<EnrollmentResponse> listMine(
        @RequestHeader("X-User-Id") Long userId,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PageResponse.from(enrollmentService.findMine(userId, pageable).map(EnrollmentResponse::from));
    }
}
