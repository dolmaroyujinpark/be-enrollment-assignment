# AI 활용 범위

## 개요
본 과제는 AI 코딩 도구(**Claude**)를 페어 프로그래밍 파트너로 사용했습니다. AI 가 코드·문서·테스트 초안을 생성하면 작성자가 **읽고 이해한 뒤 검증·수정·테스트**했고, 설계 선택은 작성자가 판단해 결정했습니다. 모든 커밋은 작성자가 직접 메시지를 작성해 커밋했습니다(공동 저자 표기 없음). 명세의 요구대로 "그대로 복사한 산출물"이 아니라 작성자가 통제하고 책임지는 결과물입니다.

배경: 작성자는 이전에 유사한 "수강 신청 동시성" 과제(다른 회사 2차 과제, Python/FastAPI, `threading.Lock` + in-process 동기화)를 직접 수행한 경험이 있습니다. 이번 과제는 그때의 한계(단일 프로세스 한정, 비표준 트릭 의존)를 의식적으로 개선하는 방향으로 설계했고 — DB row-level 락, `@Version` 낙관 락, 부분 UNIQUE 인덱스, `SELECT … FOR UPDATE SKIP LOCKED` — 그 진화 의도가 코드와 `docs/CONCURRENCY.md` 에 드러나 있습니다.

## 어디에, 어떻게 썼나

| 영역 | AI 의 역할 | 작성자의 검증·수정 |
|---|---|---|
| 도메인 모델 (`Lecture`, `Enrollment`, `PaymentIntent`, `WaitlistEntry` + 상태 전이 메서드) | 초안 생성 | FSM 전이 규칙(`canTransitionTo`)·정원 카운터 가드·`cancel(now, refundWindow)` 시그니처를 검토하고 도메인 단위 테스트(`LectureTest`, `EnrollmentTest`)로 검증 |
| 동시성 전략 (비관 락 진입점, `@Version`, 부분 UNIQUE 인덱스, SKIP LOCKED 자동 승급) | 설계 후보 제시 + 구현 초안 | 락 획득 순서·트랜잭션 경계·`enrolled_count` ±1 의 일관성을 검토하고 `ConcurrencyTest`(Testcontainers, 50명 동시 신청 → 정확히 정원만 성공 + `enrolled_count == COUNT(active)`)로 검증 |
| 신청/결제/취소/대기열 서비스 (`EnrollmentService`, `PaymentConfirmService`, `WaitlistService`) | 구현 초안 | 비즈니스 규칙(BR-1~11) 매핑·예외 케이스를 검토하고 Mockito 단위 테스트로 검증 |
| 공통 인프라 (`GlobalExceptionHandler`/`ErrorCode`/`BusinessException`, `TraceIdFilter`, `OpenApiConfig`, `logback-spring.xml`) | 구현 초안 | HTTP 상태 코드 매핑·MDC 정리(`finally` 블록)·프로필별 로그 appender 를 검토 |
| Flyway 스키마 (`V1__init.sql`), 시드 데이터 | 초안 생성 | CHECK 제약·부분 UNIQUE 인덱스·결정론적 시드(seed=42) 를 검토하고 실제 DB 에서 마이그레이션 적용 확인 |
| 빌드/실행 환경 (`build.gradle.kts`, `docker-compose.yml`, `Dockerfile`, GitHub Actions, K6 스크립트) | 초안 생성 | 실제 빌드·테스트·Docker 실행으로 검증 (특히 macOS Docker Desktop ↔ Testcontainers 연결 문제는 작성자가 증상을 확인하고 AI 와 함께 디버깅) |
| 문서 (`README`, `docs/*`) | 초안 생성 | 구현과 일치하는지 대조하고 명세 템플릿에 맞게 수정 |

## 직접 다시 짜거나 조정한 부분 (예시)
- **페이지네이션 범위**: 처음에는 `GET /api/enrollments/me` 에만 페이지네이션을 두려 했으나, 선택 구현(추가 점수)을 빠짐없이 반영하기로 결정해 강의 목록(`GET /api/lectures`) 응답도 `PageResponse` 로 변경.
- **대기열 등록 방식**: 초기 요구사항 분석(`docs/REQUIREMENTS.md` BR-8)에서는 "정원 초과 시 자동으로 대기열 등록 + `waitlist=false` 옵트아웃"을 검토했으나, `POST /api/enrollments` 응답 형태가 다형성이 되는 게 부담스러워 **별도 엔드포인트 `POST /api/lectures/{id}/waitlist`** 로 명시적 등록하도록 변경. (자동 승급 P3 는 취소 시점에 동작)
- **ConcurrencyTest 의 부하 규모**: 시드의 클래스메이트 사용자가 30명이라, 100 VU 가 아니라 30 VU(서로 다른 사용자) / 정원 N 으로 시나리오를 조정. (K6 스크립트는 별도 강의를 만들어 정원 10 / 30 VU 로 검증)
- **로컬 Docker 호환**: Testcontainers 가 macOS Docker Desktop 에 연결 못 하는 문제(① docker-java 기본 API 버전 1.32 거부 ② `/var/run/docker.sock` 가 CLI 프록시로 연결됨 ③ Ryuk 의 소켓 마운트 경로)를 작성자가 로그로 진단하고, `build.gradle.kts` 의 테스트 태스크에 `api.version`·`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE`·(macOS 한정) `DOCKER_HOST` 설정을 추가해 해결. CI(Linux)에는 영향 없게 작성.

## AI 제안을 거절하거나 바꾼 케이스
- AI 가 전역 예외 처리를 단순 `@ExceptionHandler(Exception.class)` 하나로 두려던 초안 → `ResponseEntityExceptionHandler` 를 상속하도록 변경. (안 그러면 Spring MVC 표준 예외인 `NoResourceFoundException`(404), `HttpRequestMethodNotSupportedException`(405) 등이 전부 500 으로 잡힘.)
- AI 가 `GET /api/lectures` 응답으로 Spring Data 의 `Page` 를 그대로 직렬화하려던 것 → JSON 구조가 불안정하다는 경고가 있어 `PageResponse<T>` 레코드로 감싸 안정적인 필드 집합으로 노출.
- 커밋은 AI 가 자동으로 만들지 않고 작성자가 의미 단위로 직접 작성·커밋 (gitmoji 컨벤션, 공동 저자 표기 없음). 평가 항목의 "의미 있는 단위 커밋 분리"에 맞춤.

## 검증 방법
- **자동 테스트**: `./gradlew test` — 도메인/서비스 단위 테스트(Mockito) + `ConcurrencyTest`(Testcontainers, 실제 PostgreSQL). 전부 통과.
- **수동 검증**: 앱 실행(`docker compose --profile app up` / `./gradlew bootRun`) 후 Swagger UI 와 `curl` 로 주요 흐름(등록→OPEN→신청→결제→취소, 정원 초과, 7일 경과 취소, 결제 키 중복, 권한 위반) 확인.
- **부하**: `k6 run load-test/enrollment-burst.k6.js` — 동시 신청 시 정원 수만 성공·`enrolled_count` 정합 확인.
- **CI**: GitHub Actions 가 push 마다 `./gradlew build` 로 컴파일 + 전체 테스트 자동 실행 (Linux 러너의 기본 Docker 로 Testcontainers 통합 테스트 포함).
