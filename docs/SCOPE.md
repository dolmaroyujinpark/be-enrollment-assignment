# 구현 범위 (Scope)

명세가 명시한 것(필수 / 선택), 명세에 직접 적히지 않았지만 구현에 필요한 것(암묵적 요구), 자발적으로 더한 것으로 나눠 정리합니다. 모든 항목에 코드 위치를 적어 1:1 추적이 가능하게 했습니다.

---

## 1. 명세 명시 — 필수 (10)

| # | 항목 | 출처 | 코드 |
|---|---|---|---|
| F1 | 강의 등록 (제목·설명·가격·정원·기간) | 명세 §1 | `LectureController#create`, `LectureService#register` |
| F2 | 강의 상태 전이 DRAFT → OPEN → CLOSED | 명세 §1 | `Lecture#changeStatus`, `LectureStatus#canTransitionTo` (FSM) |
| F3 | 강의 목록 조회 (status 필터) | 명세 §1 | `LectureController#list`, `LectureService#findAll` |
| F4 | 강의 상세 조회 (현재 신청 인원 포함) | 명세 §1 | `LectureController#detail`, `LectureResponse`(`enrolledCount`/`availableSeats`) |
| F5 | 수강 신청 (PENDING) | 명세 §2 | `EnrollmentController#apply`, `EnrollmentService#apply` |
| F6 | 결제 확정 (PENDING → CONFIRMED) | 명세 §2 | `EnrollmentController#confirmPayment`, `PaymentConfirmService#confirm` |
| F7 | 수강 취소 (→ CANCELLED) | 명세 §2 | `EnrollmentController#cancel`, `EnrollmentService#cancel` |
| F8 | 내 수강 신청 목록 조회 | 명세 §2 | `EnrollmentController#listMine`, `EnrollmentService#findMine` |
| F9 | 정원 초과 신청 거부 | 명세 §3 | `EnrollmentService#apply`(`CAPACITY_EXCEEDED`), `Lecture#incrementEnrolled` 가드 |
| F10 | 동시성 제어 (마지막 자리 동시 신청 시 정확히 정원 수만 성공) | 명세 §3 | `LectureRepository#findByIdForUpdate`(`@Lock(PESSIMISTIC_WRITE)`) — `docs/CONCURRENCY.md` |

---

## 2. 명세 명시 — 선택 / 추가 점수 (4)

| # | 항목 | 출처 | 코드 |
|---|---|---|---|
| O1 | 수강 취소 가능 기간 제한 (결제 후 7일, 설정값) | 명세 선택 | `EnrollmentService#ensureWithinRefundWindow` (`enrollment.refund-window`), `Enrollment#cancel(now, refundWindow)` |
| O2 | 대기열(waitlist) — 만석 시 등록 / 크리에이터 조회 | 명세 선택 | `WaitlistController`, `WaitlistService#join`(만석 가드)·`findByLecture`, `WaitlistEntry` |
| O3 | 강의별 수강생 목록 조회 (크리에이터 전용) | 명세 선택 | `LectureController#listEnrollments`, `EnrollmentService#findByLecture` (creator 검사) |
| O4 | 신청 내역 페이지네이션 | 명세 선택 | `EnrollmentController#listMine` (`Pageable`/`PageResponse`) |

---

## 3. 명세에 명시되진 않았지만 구현에 필요한 것 (암묵적 요구)

명세에 한 줄로만 적혔거나 직접 적히지 않았지만, 시스템이 올바르게 동작하려면 필요한 것들입니다.

| 항목 | 명세와의 관계 | 코드 |
|---|---|---|
| 동시성 다층 방어 (비관 락 + `Enrollment.@Version` + 부분 UNIQUE 인덱스) | "동시 신청을 고려" 를 실제로 보장. 정원 race 는 `Lecture` 비관 락 하나로, 동일 enrollment 동시 cancel race 는 `Enrollment.@Version` 으로 분리 매핑 | `findByIdForUpdate` (apply/cancel/changeStatus 공용), `Enrollment#version`, `uq_enrollments_active`, `GlobalExceptionHandler`(`OPTIMISTIC_LOCK_CONFLICT`/`DATA_INTEGRITY_VIOLATION`) |
| 결제 멱등성 (`Idempotency-Key` 헤더 + `payment_intents.idempotency_key` UNIQUE) | "결제 확정" 이 재시도에 안전해야 함 | `PaymentConfirmService#confirm`, `EnrollmentController#confirmPayment` |
| 명시적 상태 머신 — 잘못된 전이를 도메인에서 차단 | 명세의 상태 모델이 깨지지 않게 | `Lecture#changeStatus`, `Enrollment#confirm/cancel`, `*Status#canTransitionTo` → `INVALID_*_STATUS_TRANSITION` 409 |
| 동일 강의 중복 신청 방지 (취소 후 재신청은 허용) | 같은 사람이 같은 강의 두 번 신청 못 하게 | 부분 UNIQUE 인덱스 + `EnrollmentService#apply` 선검사(`DUPLICATE_ENROLLMENT`) |
| 권한 검사 — 본인만 자기 신청 취소, 강의 작성 크리에이터만 상태 전이·수강생/대기열 조회 | 인가 | `EnrollmentService#cancel`/`confirm`(`NOT_ENROLLMENT_OWNER`), `LectureService#changeStatus`·`findByLecture`(`NOT_LECTURE_OWNER`), `register`(`NOT_CREATOR`) |
| 대기열 자동 승급 — 취소 시 FIFO head 1명 자동 PENDING (`FOR UPDATE SKIP LOCKED`) — OPEN 강의에 한함 | [선택] 대기열의 자연스러운 동작 + CLOSED 강의는 "신청불가" 라 자동 승급도 차단 | `WaitlistService#promoteNext`(상태 가드 포함), `WaitlistRepository#findNextInQueueForUpdate` |
| 일관된 에러 응답 — RFC 7807 `application/problem+json` + `code` 식별자 | API 면 에러 포맷이 일관돼야 | `GlobalExceptionHandler`, `ErrorCode`, `BusinessException` |
| 강의 목록 페이지네이션 | 명세는 "신청 내역" 만 명시했으나 목록이 커지면 필요 | `LectureController#list` (`Pageable`/`PageResponse`) |

---

## 4. 자발적 차별화 — 검증·운영·문서

| 항목 | 산출물 |
|---|---|
| Testcontainers 동시성 통합 테스트 | `ConcurrencyTest` (`@SpringBootTest` + Testcontainers PostgreSQL 16) |
| K6 부하 테스트 스크립트 | `load-test/enrollment-burst.k6.js` |
| GitHub Actions CI (push/PR 마다 빌드 + 전체 테스트) | `.github/workflows/ci.yml` |
| Docker Compose 한 줄 실행 (앱 + PostgreSQL) | `docker-compose.yml`, `Dockerfile` |
| OpenAPI / Swagger UI 자동 문서화 | `OpenApiConfig`, 컨트롤러 `@Operation`/`@Tag`, DTO `@Schema` |
| 구조화 로깅 (JSON + 요청별 traceId/MDC) | `logback-spring.xml`, `TraceIdFilter` |
| Spring Boot Actuator (헬스·메트릭) | `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` |
| Mermaid ERD / 동시 신청 시퀀스 다이어그램 | `README.md`, `docs/ERD.md` |
| 설계 결정·근거 문서화 | README "설계 결정과 이유", `docs/CONCURRENCY.md` |
| 문서 일체 | `README.md`, `docs/API.md`·`ERD.md`·`REQUIREMENTS.md`·`SCOPE.md`·`CONCURRENCY.md`·`TEST.md`·`AI_USAGE.md` |

---

## 의도적으로 안 한 것

| 항목 | 이유 |
|---|---|
| JWT/세션 인증, 비밀번호·리프레시 토큰 | 명세가 "userId 헤더 전달도 허용" — 본질에 집중. 프로덕션이면 Spring Security + JWT |
| 실제 결제 PG 연동 | 명세가 "단순 상태 변경으로 대체" 허용. `PaymentIntent` 의 PENDING/FAILED 는 스키마에만 존재 |
| 분산 락 (Redis Redisson) | 단일 인스턴스 + DB row 락으로 정합 보장. 다중 인스턴스 스케일 아웃 시 검토 |
| PENDING 자동 만료 (24h TTL 등) | 운영 정책 미정 (REQUIREMENTS BR-7 참조) |
| 알림 발송 | BE-A 범위 밖 |
| 컨트롤러 MockMvc 테스트 | 컨트롤러가 얇은 pass-through — 서비스/도메인 단위 + 통합 테스트로 핵심 커버 |
