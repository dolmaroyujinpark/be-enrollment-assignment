# 테스트 전략

단위 = 빠른 도메인/서비스 검증, 통합 = 실 PG 위에서 락·제약, 부하 = 동시 경합.

## 레이어

| 레이어 | 도구 | 검증 내용 | 대상 |
|---|---|---|---|
| 도메인 단위 | JUnit 5 | 상태 머신 전이, 생성 불변식, 정원 카운터 가드, 7일 취소 기간 경계 | `LectureTest`, `EnrollmentTest` |
| 서비스 단위 | JUnit 5 + Mockito | 비즈니스 규칙(BR-1~11), 예외별 `ErrorCode` 매핑, 권한 검사, 대기열 자동 승급 호출 | `LectureServiceTest`, `EnrollmentServiceTest`, `PaymentConfirmServiceTest`, `WaitlistServiceTest` |
| 필터 단위 | JUnit 5 + Mockito | 요청별 traceId 생성/재사용, MDC put·clear, 응답 헤더 세팅 | `TraceIdFilterTest` |
| 통합 (동시성) | `@SpringBootTest` + Testcontainers (PostgreSQL 16) | 비관 락·낙관 락·부분 UNIQUE 인덱스가 실 DB 위에서 정원·이중 감소·중복 신청을 정확히 막는지, `enrolled_count == COUNT(active)` 정합 | `ConcurrencyTest` |
| 부하 | K6 | 동시 부하에서도 정원 수만 성공 | `load-test/enrollment-burst.k6.js` |

단위·통합 테스트는 74개 (단위 71 + `ConcurrencyTest` 3) 입니다. `ConcurrencyTest` 는 Docker 가 없으면 skip 하며 빌드는 통과합니다(`@Testcontainers(disabledWithoutDocker = true)`).

## 검증 포인트

| 영역 | 케이스 |
|---|---|
| FSM | 강의 `DRAFT→OPEN→CLOSED` 단방향 · 신청 `PENDING→CONFIRMED→CANCELLED` · 잘못된 전이 → 409 |
| 정원·동시성 | 정원 N + M명 동시 신청 → N명만 성공 · 같은 사용자 동시 신청 → 1건 · 같은 사용자 동시 cancel → 1건 (`@Version`) · `enrolled_count == COUNT(active)` |
| 멱등성·정보 노출 | 같은 키 재호출 → 동일 응답 · 다른 신청에 같은 키 → `IDEMPOTENCY_KEY_CONFLICT` · 같은 키로 다른 사용자 리플레이 → `NOT_ENROLLMENT_OWNER` · 빈/공백 키 → 400 |
| 7일 취소 제한 | `confirmedAt + 7d` 이내 성공 · 경과 → `REFUND_WINDOW_PASSED` · PENDING 무제한 |
| 권한 | `NOT_LECTURE_OWNER` · `NOT_ENROLLMENT_OWNER` · `NOT_CREATOR` |
| 대기열 | 만석에서만 등록 · 자리 남으면 `WAITLIST_NOT_NEEDED` · 자동 승급은 OPEN·자리·head 있을 때만 |

## 시드 데이터 격리
- 단위 테스트는 데모 시드를 사용하지 않습니다.
- 통합/동시성 테스트는 로컬 데모 DB 와 분리된 테스트 환경에서 실행됩니다 (`@ActiveProfiles("test")` + Testcontainers 가 띄운 별도 PostgreSQL 컨테이너).
- `ConcurrencyTest` 는 `@BeforeEach` 에서 필요한 사용자/강의를 직접 생성하고, 다음 테스트 시작 전에 `deleteAll` 로 정리합니다.

## 의도적으로 안 한 것
- 컨트롤러 MockMvc 테스트 — 컨트롤러가 헤더/바디 → 서비스 호출 → DTO 변환만 하는 얇은 pass-through 라, 서비스·도메인 단위 + 통합 테스트로 핵심이 커버됩니다. 여력이 있으면 `@WebMvcTest` 로 에러 응답 포맷(ProblemDetail) 검증을 추가할 수 있습니다.

## 실행 방법
```bash
# 전체 (단위 + Testcontainers 통합)
./gradlew test

# 동시성 통합 테스트만 (Docker 필요 — Testcontainers 가 PostgreSQL 컨테이너를 띄움)
./gradlew test --tests "com.liveklass.enrollment.concurrency.ConcurrencyTest"

# 부하 테스트 (앱이 실행 중이어야 함: docker compose --profile app up  또는  ./gradlew bootRun)
k6 run load-test/enrollment-burst.k6.js
#   k6 run -e BASE_URL=http://localhost:8080 load-test/enrollment-burst.k6.js
```
테스트 리포트는 `build/reports/tests/test/index.html` 에 생성됩니다.

> macOS Docker Desktop 에서 Testcontainers 가 CLI 프록시 소켓·API 버전 때문에 연결 못 하는 경우가 있어, `build.gradle.kts` 의 테스트 태스크에 `api.version`·`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`·(macOS 한정)`DOCKER_HOST=…/docker.raw.sock` 설정을 넣어 두었습니다. 별도 환경변수/설정 없이 `./gradlew test` 만으로 동작하며, Linux/CI 에서는 영향이 없습니다.

## CI
GitHub Actions(`.github/workflows/ci.yml`)가 `main` push / PR 마다 ubuntu-latest 에서 `./gradlew --no-daemon build` 를 실행합니다 — 컴파일 + 전체 테스트(러너의 기본 Docker 로 Testcontainers 통합 테스트 포함). 실패 시 테스트 리포트를 아티팩트로 업로드합니다.
