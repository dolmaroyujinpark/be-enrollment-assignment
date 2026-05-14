package com.liveklass.enrollment.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {

    // 404 Not Found
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    LECTURE_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 강의입니다."),
    ENROLLMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 수강 신청입니다."),

    // 403 Forbidden
    NOT_CREATOR(HttpStatus.FORBIDDEN, "강의 등록 권한이 없습니다. CREATOR 역할이 필요합니다."),
    NOT_LECTURE_OWNER(HttpStatus.FORBIDDEN, "강의 작성자만 수행할 수 있습니다."),
    NOT_ENROLLMENT_OWNER(HttpStatus.FORBIDDEN, "본인의 수강 신청이 아닙니다."),

    // 422 Unprocessable Entity — 현재 상태에서 비즈니스 규칙상 불가
    LECTURE_NOT_OPEN(HttpStatus.UNPROCESSABLE_ENTITY, "OPEN 상태의 강의에만 신청할 수 있습니다."),

    // 409 Conflict — 상태 충돌 / 중복
    INVALID_LECTURE_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 강의 상태 전이입니다."),
    INVALID_ENROLLMENT_STATUS_TRANSITION(HttpStatus.CONFLICT, "허용되지 않는 수강 신청 상태 전이입니다."),
    CAPACITY_EXCEEDED(HttpStatus.CONFLICT, "강의 정원이 모두 찼습니다."),
    DUPLICATE_ENROLLMENT(HttpStatus.CONFLICT, "이미 신청한 강의입니다."),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "이미 다른 요청에 사용된 Idempotency-Key 입니다."),
    REFUND_WINDOW_PASSED(HttpStatus.CONFLICT, "취소 가능 기간이 지났습니다."),
    ALREADY_IN_WAITLIST(HttpStatus.CONFLICT, "이미 대기열에 등록되어 있습니다.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
