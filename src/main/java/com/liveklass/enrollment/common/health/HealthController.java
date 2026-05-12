package com.liveklass.enrollment.common.health;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "헬스체크")
@RestController
public class HealthController {

    @Operation(summary = "헬스체크", description = "애플리케이션 기동 여부. 상세 헬스(DB 연결 등)는 /actuator/health 참조.")
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
