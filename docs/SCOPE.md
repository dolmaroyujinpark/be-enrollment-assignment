# 구현 범위 (Scope)

본 문서는 라이브 클래스 BE-A 과제 구현 범위를 **명세 기준**으로 명확히 분류합니다. 평가자와 작성자 모두가 "어디까지가 명세 요구사항이고, 어디부터가 자발적 차별화인지" 한눈에 파악할 수 있도록 라벨을 일관되게 부여합니다.

## 라벨 컨벤션

| 라벨 | 의미 |
|---|---|
| `[필수]` | 명세에 명시된 핵심 요구사항. 반드시 구현해야 평가 통과. |
| `[선택]` | 명세에서 "선택 구현 (추가 점수)" 으로 분류된 항목. |
| `[추가]` | 명세에 없지만 자발적으로 구현한 차별화 요소. |

본 라벨은 다음 위치에 일관되게 사용됩니다:
- `README.md` 의 구현 범위 섹션
- `docs/REQUIREMENTS.md` 의 항목별 표기
- `docs/API.md` 의 엔드포인트별 표기
- 커밋 메시지 prefix (`feat([필수]): ...`)

---

## `[필수]` — 명세 핵심 구현 (10개)

| # | 항목 | 명세 출처 | 구현 위치 (예정) |
|---|---|---|---|
| F1 | 강의 등록 (제목/설명/가격/정원/기간) | 명세 §1 | `LectureController#create` |
| F2 | 강의 상태 전이 DRAFT → OPEN → CLOSED | 명세 §1 | `Lecture#changeStatus` (FSM) |
| F3 | 강의 목록 조회 (status 필터) | 명세 §1 | `LectureController#list` |
| F4 | 강의 상세 조회 (현재 신청 인원 포함) | 명세 §1 | `LectureController#detail` |
| F5 | 수강 신청 (PENDING) | 명세 §2 | `EnrollmentService#apply` |
| F6 | 결제 확정 (PENDING → CONFIRMED) | 명세 §2 | `PaymentConfirmService#confirm` |
| F7 | 수강 취소 (→ CANCELLED) | 명세 §2 | `EnrollmentService#cancel` |
| F8 | 내 수강 신청 목록 조회 | 명세 §2 | `EnrollmentController#listMine` |
| F9 | 정원 초과 신청 거부 | 명세 §3 | `Lecture#incrementEnrolled` (도메인 가드) |
| F10 | 동시성 제어 (마지막 자리 동시 신청 시 1명만 성공) | 명세 §3 | `LectureRepository#findByIdForUpdate` + 다층 방어 |

---

## `[선택]` — 명세 선택 구현, 추가 점수 (4개)

| # | 항목 | 명세 출처 | 구현 위치 (예정) |
|---|---|---|---|
| O1 | 결제 후 7일 이내 취소 가능 (시간 제한) | 명세 선택 | `Enrollment#cancel(now, refundWindow)` |
| O2 | 대기열(waitlist) 기능 | 명세 선택 | `WaitlistEntry` 엔티티 + `WaitlistService` |
| O3 | 강의별 수강생 목록 조회 (크리에이터 전용) | 명세 선택 | `LectureController#listEnrollments` (권한 검사 포함) |
| O4 | 신청 내역 페이지네이션 | 명세 선택 | `EnrollmentController` 에서 `Pageable` |

---

## `[추가]` — 차별화 자발 구현 (14개)

| # | 항목 | 가치 | 구현 방식 |
|---|---|---|---|
| P1 | 결제 확정 멱등성 (`Idempotency-Key` 헤더) | 결제 재시도 안전성 | `payment_intents.idempotency_key UNIQUE` + 동일 키 → 동일 응답 반환 |
| P2 | 명시적 FSM (도메인 메서드로 잘못된 전이 차단) | 상태 안전성 | `LectureStatus#canTransitionTo`, `EnrollmentStatus#canTransitionTo` |
| P3 | 대기열 자동 승급 (취소 발생 시 SKIP LOCKED 기반 다음 대기자 PENDING 자동 생성) | UX | `WaitlistService#promoteNext` + PostgreSQL `FOR UPDATE SKIP LOCKED` |
| P4 | 동시성 다층 방어 (PESSIMISTIC_WRITE + `@Version` + 부분 UNIQUE 인덱스) | 정합성 | `Lecture#version` + `uq_enrollments_active` |
| P5 | K6 부하 테스트 (100 VU 동시 신청 시나리오) | 검증 | `load-test/enrollment-burst.k6.js` |
| P6 | Testcontainers 기반 통합 테스트 (실 PostgreSQL) | 검증 | `@SpringBootTest` + `@Testcontainers` |
| P7 | OpenAPI(springdoc) + Swagger UI 자동 문서화 | 개발자 경험 | `springdoc-openapi-starter-webmvc-ui` |
| P8 | Docker Compose 한 방 실행 (앱 + PG) | 평가자 경험 | `docker-compose.yml` |
| P9 | GitHub Actions CI (build/test) | 운영 | `.github/workflows/ci.yml` |
| P10 | 구조화 로깅 (logback JSON + MDC traceId) | 운영 | `logstash-logback-encoder` |
| P11 | RFC 7807 ProblemDetail 표준 에러 응답 | 표준 준수 | `GlobalExceptionHandler` + `ProblemDetail` |
| P12 | Mermaid ERD + 시퀀스 다이어그램 README 임베드 | 문서 | `README.md`, `docs/ERD.md` |
| P13 | 설계 결정·근거 문서화 | 의사결정 추적 / 설명 가능성 | README "설계 결정과 이유" 섹션 |
| P14 | Spring Boot Actuator 메트릭/헬스 노출 | 운영 | `/actuator/health`, `/actuator/metrics` |

---

## 의도적으로 안 한 것

| 항목 | 이유 |
|---|---|
| 인증/인가 (JWT/세션) | 명세에서 "userId 헤더 전달도 허용". 본질에 집중 |
| 실제 결제 PG 연동 | 명세에서 "단순 상태 변경으로 대체" |
| 분산 락(Redis Redisson) | 단일 인스턴스 + DB row lock 으로 충분. README에 "스케일업 시 전환 경로"로만 언급 |
| 비밀번호 해싱/Refresh token | 인증 본격 구현 안 하므로 불필요 |
| 알림 발송 | 본 과제(BE-A) 범위 외. BE-C가 알림 과제 |

---

## 정리

- **`[필수]` 10 + `[선택]` 4 + `[추가]` 14 = 28개 구현 항목**
- 평가자가 README → SCOPE → REQUIREMENTS → 코드 순으로 추적 가능
- 모든 항목이 코드와 1:1 매핑되어, "이 라벨이 어디 코드에 있나?" 가 즉시 확인됨
