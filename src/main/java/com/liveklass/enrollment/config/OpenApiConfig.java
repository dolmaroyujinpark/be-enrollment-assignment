package com.liveklass.enrollment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI enrollmentOpenAPI() {
        return new OpenAPI().info(new Info()
            .title("LiveKlass 수강 신청 시스템 API")
            .description("""
                강의 개설·조회·상태 전이, 수강 신청·결제·취소·내 신청 목록, 대기열 API.

                - 인증: 상태를 바꾸는 요청은 헤더 `X-User-Id: <userId>` 필요 (간이 방식, 명세 허용).
                - 결제 확정: 헤더 `Idempotency-Key` 필수 — 같은 키로 재호출하면 상태 변경 없이 동일 응답.
                - 에러 응답: RFC 7807 `application/problem+json` 형식 — `{ type, title, status, detail, code }`.
                - 페이지네이션: `?page=0&size=20` (응답은 `{ content, page, size, totalElements, totalPages, hasNext }`).
                """)
            .version("0.1.0"));
    }
}
