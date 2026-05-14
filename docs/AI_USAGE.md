# AI 활용 범위

코드·문서·테스트 초안 작성에 AI(Claude)를 사용했습니다. 산출물은 그대로 채택하지 않고 작은 단위로 검증·수정해 의미 단위로 직접 커밋했습니다(공동 저자 표기 없음, `git log` 로 확인 가능).

## 어디에 어떻게 썼나

| 영역 | AI 의 역할 | 검증·수정 |
|---|---|---|
| 도메인 모델 + 상태 전이 | 초안 생성 | FSM 전이·정원 가드 검토, 도메인 단위 테스트 |
| 동시성 (비관 락·`@Version`·부분 UNIQUE·SKIP LOCKED) | 설계 후보 + 초안 | 락 순서·트랜잭션 경계 검토, `ConcurrencyTest` 검증 |
| 신청/결제/취소/대기열 서비스 | 구현 초안 | BR-1~11 매핑·예외 케이스, Mockito 단위 테스트 |
| 공통 인프라 (`GlobalExceptionHandler`, `TraceIdFilter`, OpenAPI, logback) | 구현 초안 | HTTP 상태·MDC 정리 검토 |
| Flyway 스키마, 시드 | 초안 생성 | CHECK 제약·부분 UNIQUE 검토, 실 DB 마이그레이션 확인 |
| 빌드/실행 (Gradle, Docker, CI, K6) | 초안 생성 | 빌드·테스트 실행 검증, macOS Docker 호환은 직접 로그 추적으로 해결 |
| 문서 (`README`, `docs/*`) | 초안 생성 | 구현과 일치 여부 대조 |

## AI 제안을 받아들이지 않은 사례

- 전역 예외를 `@ExceptionHandler(Exception.class)` 단일로 두려던 초안 → `ResponseEntityExceptionHandler` 상속으로 변경 (그래야 `NoResourceFoundException`/`HttpRequestMethodNotSupportedException` 같은 Spring MVC 표준 예외가 500 으로 잡히지 않음).
- `GET /api/lectures` 응답을 Spring `Page` 그대로 직렬화 → JSON 구조 안정성을 위해 `PageResponse<T>` 레코드로 감쌈.
- 커밋은 AI 가 자동 생성하지 않고 의미 단위로 직접 작성 (gitmoji, 공동 저자 표기 없음).

## 직접 조정한 부분

- **페이지네이션 범위** — `GET /api/enrollments/me` 만 의도였으나 강의 목록(`GET /api/lectures`) 응답도 `PageResponse` 로 통일.
- **대기열 등록 방식** — `POST /api/enrollments` 응답 다형성을 피해 별도 엔드포인트 `POST /api/lectures/{id}/waitlist` 로 명시적 등록 (자동 승급은 취소 시점).
- **macOS Docker 호환** — Testcontainers 연결 실패 원인 3건(API 버전·CLI 프록시 소켓·Ryuk 소켓 마운트)을 좁혀 `build.gradle.kts` 테스트 태스크 설정으로 해결. CI(Linux)에 영향 없음.

## Race / 정보 노출 점검 사이클

이전 유사 과제(Python/FastAPI · `threading.Lock`)에서 "같은 사용자가 같은 action 을 동시에 두 번" race 를 놓친 적이 있어, 이번엔 카테고리 매트릭스로 점검해 3건을 발견·회귀 테스트로 고정했습니다.

| 카테고리 | 결과 |
|---|---|
| 같은 자원 다수 사용자 (capacity race) | `ConcurrencyTest` — 정원 N, 동시 M 명 → 정확히 N 명 성공 |
| 같은 사용자 동시 두 번 (double-click) | **수강 취소 race 발견** — `Enrollment.@Version` 누락. `ConcurrencyTest#sameUserConcurrentCancel_doesNotDoubleDecrement` 고정 |
| 다른 entity 상태 변화 중 race | **CLOSED 강의 자동 승급 발견** — `WaitlistService.promoteNext` 상태 가드 추가. `WaitlistServiceTest#closedLecture_doesNotPromote` 고정 |
| 멱등 키 리플레이 정보 노출 | **결제 멱등 리플레이의 본인 검증 누락 발견** — 캐시 응답 경로에 `NOT_ENROLLMENT_OWNER` 가드. `PaymentConfirmServiceTest#confirm_idempotentReplayByDifferentUser_isRejected` 고정 |
| 권한 검사 우회 | 본인/크리에이터 검사 누락 없음 |

자동화된 검증 절차(단위·통합·CI·부하)는 [`TEST.md`](TEST.md) 참조.
