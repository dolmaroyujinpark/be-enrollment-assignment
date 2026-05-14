package com.liveklass.enrollment.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * RFC 7807 Problem Details(application/problem+json) 형식으로 에러 응답을 통일한다.
 * ResponseEntityExceptionHandler 를 상속해 Spring MVC 표준 예외(404/405/타입 불일치 등)는 기본 처리를 따른다.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return problem(code.getStatus(), code.getDefaultMessage(), code.name(), e.getMessage());
    }

    /** DB 제약(부분 UNIQUE 인덱스 등) 위반 — 동일 강의 중복 신청 같은 경합 상황. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("데이터 무결성 제약 위반: {}", e.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "이미 처리되었거나 충돌하는 요청입니다.", "DATA_INTEGRITY_VIOLATION", null);
    }

    /** 낙관 락(@Version) 충돌 — 비관 락 우회 경로에서 stale write 가 감지된 경우. */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException e) {
        log.warn("낙관 락 충돌: {}", e.getMessage());
        return problem(HttpStatus.CONFLICT, "동시 수정 충돌이 발생했습니다. 다시 시도해 주세요.", "OPTIMISTIC_LOCK_CONFLICT", null);
    }

    /**
     * Pageable 의 sort 파라미터에 엔티티가 모르는 필드가 들어왔을 때 (예: Swagger UI 가 자동으로 채우는
     * `?sort=["string"]` placeholder). Spring Data 가 ORDER BY 절을 빌드할 때 throw 하므로 500 으로
     * 흘러나가지 않도록 400 으로 매핑한다.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleInvalidSortProperty(PropertyReferenceException e) {
        return problem(HttpStatus.BAD_REQUEST, "정렬 기준이 올바르지 않습니다.",
            "INVALID_SORT_PROPERTY", "허용되지 않는 정렬 필드: " + e.getPropertyName());
    }

    /** 도메인 불변식 위반(서비스 선검증을 통과하지 못한 경우) — 안전망. */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException e) {
        return problem(HttpStatus.CONFLICT, "현재 상태에서는 처리할 수 없는 요청입니다.", "ILLEGAL_STATE", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.", "ILLEGAL_ARGUMENT", e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        log.error("처리되지 않은 예외", e);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.", "INTERNAL_ERROR", null);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
            .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
            .collect(Collectors.joining(", "));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "요청 값 검증에 실패했습니다.", "VALIDATION_FAILED", detail);
        return ResponseEntity.status(status).body(pd);
    }

    /** 필수 헤더 누락 (예: X-User-Id, Idempotency-Key) — 평가자가 자주 부딪히는 400. */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ProblemDetail handleMissingRequestHeader(MissingRequestHeaderException ex) {
        return problem(HttpStatus.BAD_REQUEST, "필수 헤더가 누락되었습니다.", "MISSING_HEADER",
            "헤더 누락: " + ex.getHeaderName());
    }

    /** 쿼리/패스 파라미터의 타입 불일치 (예: ?status=INVALID, /api/lectures/abc). */
    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
        TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String detail = "잘못된 파라미터 값: %s".formatted(ex.getValue());
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "파라미터 타입이 올바르지 않습니다.", "TYPE_MISMATCH", detail);
        return ResponseEntity.status(status).body(pd);
    }

    /** 요청 body 가 JSON 으로 파싱 안 되거나 비어있을 때. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다.", "MALFORMED_REQUEST",
            "요청 body 가 비어있거나 JSON 형식이 아닙니다.");
        return ResponseEntity.status(status).body(pd);
    }

    private ProblemDetail problem(HttpStatus status, String title, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatus(status);
        pd.setTitle(title);
        pd.setProperty("code", code);
        if (detail != null && !detail.isBlank()) {
            pd.setDetail(detail);
        }
        return pd;
    }
}
