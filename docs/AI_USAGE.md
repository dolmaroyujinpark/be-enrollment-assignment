# AI 활용 범위

## 개요
코드·문서·테스트 초안 작성에 AI 코딩 도구(Claude)를 사용했습니다. 산출물은 그대로 채택하지 않고 작은 단위로 읽으며 검증·수정했고, 커밋은 의미 단위로 직접 분리해 작성했습니다(공동 저자 표기 없음 — `git log` 에서 확인 가능). 설계 결정의 근거는 README "설계 결정과 이유" / `docs/CONCURRENCY.md` / `docs/REQUIREMENTS.md` BR-1~11 에 분산 기록되어 있습니다.

이전에 유사한 수강 신청 동시성 과제(Python/FastAPI, `threading.Lock` 기반 in-process 동기화)를 수행한 경험에서 단일 프로세스 한정·비표준 트릭 의존이라는 한계를 학습해, 이번에는 DB row-level 락·`@Version`·부분 UNIQUE 인덱스·`FOR UPDATE SKIP LOCKED` 로 설계 방향을 잡았습니다(`docs/CONCURRENCY.md` 비교 표).

## 어디에 어떻게 썼나

| 영역 | AI 의 역할 | 작성자의 검증·수정 |
|---|---|---|
| 도메인 모델 + 상태 전이 메서드 | 초안 생성 | FSM 전이 규칙·정원 카운터 가드·`cancel(now, refundWindow)` 시그니처 검토, 도메인 단위 테스트로 검증 |
| 동시성 전략 (비관 락 진입점, `@Version`, 부분 UNIQUE, SKIP LOCKED 자동 승급) | 설계 후보 제시 + 구현 초안 | 락 획득 순서·트랜잭션 경계·`enrolled_count` ±1 정합 검토, `ConcurrencyTest`(Testcontainers)로 검증 |
| 신청/결제/취소/대기열 서비스 | 구현 초안 | 비즈니스 규칙(BR-1~11) 매핑·예외 케이스 검토, Mockito 단위 테스트로 검증 |
| 공통 인프라 (`GlobalExceptionHandler`/`ErrorCode`, `TraceIdFilter`, `OpenApiConfig`, `logback-spring.xml`) | 구현 초안 | HTTP 상태 코드 매핑·MDC 정리·프로필별 로그 appender 검토 |
| Flyway 스키마, 시드 데이터 | 초안 생성 | CHECK 제약·부분 UNIQUE 인덱스·결정론적 시드 검토, 실 DB 에서 마이그레이션 확인 |
| 빌드/실행 (`build.gradle.kts`, Docker Compose, GitHub Actions, K6) | 초안 생성 | 실제 빌드·테스트·Docker 실행으로 검증. macOS Docker Desktop ↔ Testcontainers 연결 문제는 직접 로그 추적으로 원인을 좁힌 뒤 `build.gradle.kts` 테스트 태스크에 설정 반영(아래 "직접 조정한 부분" 참조) |
| 문서 (`README`, `docs/*`) | 초안 생성 | 구현과 일치 여부 대조, 명세 템플릿에 맞게 수정 |

## 직접 조정한 부분
- **페이지네이션 범위** — 처음엔 `GET /api/enrollments/me` 에만 두려 했으나 선택 구현을 빠짐없이 반영하기로 결정해 강의 목록(`GET /api/lectures`) 응답도 `PageResponse` 로 변경.
- **대기열 등록 방식** — "정원 초과 시 자동 등록 + `waitlist=false` 옵트아웃"도 검토했으나, `POST /api/enrollments` 응답이 다형성이 되는 게 부담스러워 별도 엔드포인트 `POST /api/lectures/{id}/waitlist` 로 명시적 등록하도록 변경. (자동 승급은 취소 시점에 동작.)
- **ConcurrencyTest 규모** — 시드 클래스메이트가 30명이라 100 VU 가 아니라 30 VU(서로 다른 사용자) / 정원 N 으로 조정.
- **로컬 Docker 호환** — Testcontainers 가 macOS Docker Desktop 에 연결 못 하는 증상을 만나, 실패 로그에서 ① docker-java 기본 API 버전 1.32 거부 ② `/var/run/docker.sock` 가 CLI 프록시 ③ Ryuk 의 소켓 마운트 경로 — 세 원인을 좁혔습니다. `build.gradle.kts` 테스트 태스크의 `api.version`·`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`·(macOS 한정)`DOCKER_HOST` 설정으로 해결했고 CI(Linux)에는 영향이 없습니다.

## AI 제안을 받아들이지 않은 사례
- 전역 예외 처리를 `@ExceptionHandler(Exception.class)` 하나로 두려던 초안 → `ResponseEntityExceptionHandler` 를 상속하도록 변경. (안 그러면 `NoResourceFoundException`(404)·`HttpRequestMethodNotSupportedException`(405) 등 Spring MVC 표준 예외가 전부 500 으로 잡힘.)
- `GET /api/lectures` 응답으로 Spring 의 `Page` 를 그대로 직렬화하려던 것 → JSON 구조가 불안정하다는 경고가 있어 `PageResponse<T>` 레코드로 감쌈.
- 커밋은 AI 가 자동 생성하지 않고 작성자가 의미 단위로 직접 작성·커밋(gitmoji, 공동 저자 표기 없음).

## 검증 방법

### 자동화된 검증
- `./gradlew test` — 도메인/서비스 단위 테스트(Mockito) + `ConcurrencyTest`(Testcontainers, 실 PostgreSQL). 마지막 푸시 기준 빌드 상태는 README 상단 CI 배지로 확인 가능합니다.
- 앱 실행(`docker compose --profile app up` / `./gradlew bootRun`) 후 Swagger UI 와 `curl` 로 주요 흐름(등록→OPEN→신청→결제→취소, 정원 초과, 7일 경과 취소, 결제 키 중복, 권한 위반) 점검.
- `k6 run load-test/enrollment-burst.k6.js` — 동시 신청 시 정원 수만 성공·`enrolled_count` 정합 확인.
- GitHub Actions 가 `main` push / PR 마다 `./gradlew build` 로 컴파일 + 전체 테스트 자동 실행 (`.github/workflows/ci.yml`).

### Race / 정보 노출 점검 사이클 (Adversarial 검증)

이전 비슷한 과제에서 "같은 사용자가 같은 action 을 동시에 두 번 보내는" race 를 놓친 적이 있어, 이번에는 다음 카테고리 매트릭스를 적용해 구현 후 별도 점검 사이클을 추가했습니다.

| 카테고리 | 점검 결과 |
|---|---|
| 같은 자원 다수 사용자 (capacity race) | `ConcurrencyTest` — 정원 N, 동시 M 명 신청 → 정확히 N 명만 성공, 나머지 `CAPACITY_EXCEEDED` |
| 같은 사용자 같은 action 동시 두 번 (double-click) | **수강 취소 race 발견** — `Enrollment` 에 `@Version` 이 없어 두 트랜잭션이 PENDING 캐시를 들고 모두 통과 → `enrolled_count` 이중 감소. `@Version` 추가 + 회귀 테스트(`ConcurrencyTest#sameUserConcurrentCancel_doesNotDoubleDecrement`) |
| 다른 entity 의 상태 변화 중 race | **`promoteNext` 가 CLOSED 강의도 자동 승급** 발견 — 강의가 CLOSED 로 전환된 직후 취소가 발생하면 대기열 head 가 "신청 불가" 상태 강의에 PENDING 으로 들어감. 상태 가드 추가 + 회귀 테스트(`WaitlistServiceTest#closedLecture_doesNotPromote`) |
| 멱등 키 리플레이 시 정보 노출 | **결제 멱등 리플레이 경로의 본인 검증 누락** 발견 — 정상 경로엔 `userId` 가드가 있으나 캐시 응답 반환 경로엔 없음. 멱등 키가 노출되면 타 사용자 `enrollment` 가 그대로 응답 body 로 유출. 가드 추가 + 회귀 테스트(`PaymentConfirmServiceTest#confirm_idempotentReplayByDifferentUser_isRejected`) |
| 권한 검사 우회 | 모든 endpoint 점검 — 본인 검사(`NOT_ENROLLMENT_OWNER`)와 크리에이터 검사(`NOT_LECTURE_OWNER`, `NOT_CREATOR`) 누락 없음 |

세 건 모두 회귀 테스트로 고정해 다시 들어와도 즉시 잡히도록 했습니다.

### 코드 ↔ 문서 정합성 검증

구현 변경이 있을 때마다 `README` · `docs/CONCURRENCY.md` · `docs/TEST.md` · `docs/API.md` · `docs/SCOPE.md` · `docs/REQUIREMENTS.md` · `docs/ERD.md` 가 코드와 어긋나지 않는지 별도 사이클로 점검합니다. (예: 위 race 수정으로 `enrollments.version` 컬럼이 추가됐을 때 ERD/스키마 표·동시성 4-Layer 표·테스트 개수가 모두 동기 갱신되었는지.)
