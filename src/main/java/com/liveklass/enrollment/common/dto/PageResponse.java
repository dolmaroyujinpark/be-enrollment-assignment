package com.liveklass.enrollment.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 페이지네이션 응답 공통 포맷. Spring Data 의 Page 를 직접 직렬화하면 JSON 구조가 불안정하다는 경고가 있어
 * 안정적인 필드 집합으로 감싼다.
 */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
            page.getContent(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.hasNext()
        );
    }
}
