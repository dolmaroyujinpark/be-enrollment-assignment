package com.liveklass.enrollment.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
