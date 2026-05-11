package com.liveklass.enrollment.common.exception;

import lombok.Getter;

/**
 * 비즈니스 규칙 위반을 표현하는 공통 예외.
 * 서비스 계층에서 던지고, GlobalExceptionHandler 가 ErrorCode 에 매핑된 HTTP 상태로 변환한다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        super(detail != null ? detail : errorCode.getDefaultMessage());
        this.errorCode = errorCode;
    }
}
