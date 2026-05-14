# API 명세

REST API. 인터랙티브 문서는 앱 실행 후 **Swagger UI** (`/swagger-ui.html`), OpenAPI 스펙은 `/v3/api-docs`.

## 공통 규약

- **인증**: 상태를 바꾸거나 권한이 필요한 요청에 `X-User-Id: <userId>` 헤더 (명세 허용 간이 방식).
- **시드 ID 기준**: 크리에이터 `id 1~5` · 클래스메이트 `id 6~35` · 강의 `id 1~3` DRAFT / `4~17` OPEN / `18~20` CLOSED.
- **에러 응답**: RFC 7807 `application/problem+json` — `{ type, title, status, detail, code }`.
- **페이지네이션**: `?page=0&size=20` (0-base). 응답 `{ content, page, size, totalElements, totalPages, hasNext }`.
- **추적**: 모든 응답에 `X-Trace-Id` (요청에 같은 헤더 보내면 그 값 사용).

## ErrorCode

| code | HTTP | 의미 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 요청 body 검증 실패 |
| `MISSING_HEADER` | 400 | 필수 헤더 누락 (`X-User-Id`, `Idempotency-Key`) |
| `TYPE_MISMATCH` | 400 | 파라미터 타입 불일치 (`?status=INVALID` 등) |
| `INVALID_SORT_PROPERTY` | 400 | `?sort=` 의 필드가 엔티티에 없음 (예: Swagger UI 의 `["string"]` placeholder) |
| `MALFORMED_REQUEST` | 400 | body 비어있거나 JSON 파싱 불가 |
| `ILLEGAL_ARGUMENT` | 400 | 도메인 생성자 가드 등 |
| `USER_NOT_FOUND` / `LECTURE_NOT_FOUND` / `ENROLLMENT_NOT_FOUND` | 404 | 대상 없음 |
| `NOT_CREATOR` / `NOT_LECTURE_OWNER` / `NOT_ENROLLMENT_OWNER` | 403 | 권한 없음 |
| `LECTURE_NOT_OPEN` | 422 | OPEN 이 아닌 강의에 신청·대기열 등록 |
| `INVALID_LECTURE_STATUS_TRANSITION` / `INVALID_ENROLLMENT_STATUS_TRANSITION` | 409 | 허용되지 않는 상태 전이 |
| `CAPACITY_EXCEEDED` | 409 | 정원 초과 |
| `DUPLICATE_ENROLLMENT` | 409 | 동일 강의 active 신청 중복 |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | 같은 키를 다른 신청에 사용 |
| `REFUND_WINDOW_PASSED` | 409 | 결제 후 7일 경과 후 취소 시도 |
| `ALREADY_IN_WAITLIST` | 409 | 이미 대기열 등록됨 |
| `WAITLIST_NOT_NEEDED` | 409 | 자리 남아있음 — 바로 수강 신청 권장 |
| `DATA_INTEGRITY_VIOLATION` / `OPTIMISTIC_LOCK_CONFLICT` / `ILLEGAL_STATE` | 409 | 다층 방어선·도메인 invariant |
| `INTERNAL_ERROR` | 500 | 처리되지 않은 예외 |

## 엔드포인트

| 메서드 | 경로 | 권한 | 성공 | 주요 에러 |
|---|---|---|---|---|
| GET | `/health` | — | 200 | — |
| POST | `/api/lectures` | CREATOR | 201 + Location | `NOT_CREATOR` · `VALIDATION_FAILED` |
| GET | `/api/lectures?status=&page=&size=` | — | 200 (페이지) | `TYPE_MISMATCH` |
| GET | `/api/lectures/{id}` | — | 200 | `LECTURE_NOT_FOUND` |
| PATCH | `/api/lectures/{id}/status` | 작성 크리에이터 | 200 | `NOT_LECTURE_OWNER` · `INVALID_LECTURE_STATUS_TRANSITION` |
| GET | `/api/lectures/{id}/enrollments` | 작성 크리에이터 | 200 (페이지) | `NOT_LECTURE_OWNER` |
| POST | `/api/lectures/{id}/waitlist` | 본인 | 201 | `WAITLIST_NOT_NEEDED` · `LECTURE_NOT_OPEN` · `DUPLICATE_ENROLLMENT` · `ALREADY_IN_WAITLIST` |
| GET | `/api/lectures/{id}/waitlist` | 작성 크리에이터 | 200 (페이지) | `NOT_LECTURE_OWNER` |
| POST | `/api/enrollments` | 본인 | 201 + Location | `LECTURE_NOT_OPEN` · `CAPACITY_EXCEEDED` · `DUPLICATE_ENROLLMENT` |
| POST | `/api/enrollments/{id}/payment` | 본인 + `Idempotency-Key` | 200 | `INVALID_ENROLLMENT_STATUS_TRANSITION` · `IDEMPOTENCY_KEY_CONFLICT` · `NOT_ENROLLMENT_OWNER` |
| DELETE | `/api/enrollments/{id}` | 본인 | 200 | `REFUND_WINDOW_PASSED` · `NOT_ENROLLMENT_OWNER` |
| GET | `/api/enrollments/me?page=&size=` | 본인 | 200 (페이지) | — |

## 요청·응답 예시

핵심 흐름 (ID 는 fresh seed 기준 예시값):

```bash
# 강의 등록 → OPEN
curl -X POST localhost:8080/api/lectures -H 'X-User-Id: 1' -H 'Content-Type: application/json' \
  -d '{"title":"Spring Boot","description":"...","price":199000,"capacity":20,"startDate":"2026-06-01","endDate":"2026-07-01"}'
# → 201 { "id":21, "status":"DRAFT", "enrolledCount":0, "availableSeats":20, ... }
curl -X PATCH localhost:8080/api/lectures/21/status -H 'X-User-Id: 1' -H 'Content-Type: application/json' -d '{"status":"OPEN"}'

# 신청 → 결제 → 취소
curl -X POST localhost:8080/api/enrollments -H 'X-User-Id: 6' -H 'Content-Type: application/json' -d '{"lectureId":21}'
# → 201 { "id":1, "status":"PENDING", "appliedAt":"...", ... }
curl -X POST localhost:8080/api/enrollments/1/payment -H 'X-User-Id: 6' -H 'Idempotency-Key: pay-1-abc'
# → 200 { "status":"CONFIRMED", "confirmedAt":"...", "paymentIntentId":1, ... }
curl -X DELETE localhost:8080/api/enrollments/1 -H 'X-User-Id: 6'
# → 200 { "status":"CANCELLED", "cancelledAt":"...", ... }
```

응답 DTO 스키마는 Swagger UI 에서 확인. `LectureResponse` 는 `enrolledCount`/`availableSeats` 포함, `EnrollmentResponse` 는 `status`·`appliedAt`·`confirmedAt`·`cancelledAt`·`paymentIntentId` 포함.

## 동시성

마지막 자리 동시 신청은 `Lecture` row 에 `PESSIMISTIC_WRITE` 락으로 직렬화 → 정원 수만 `201`, 나머지 `409 CAPACITY_EXCEEDED`. 상세는 [`docs/CONCURRENCY.md`](CONCURRENCY.md), 검증은 `./gradlew test --tests ConcurrencyTest` (Testcontainers) · `k6 run load-test/enrollment-burst.k6.js`.

## Actuator

`GET /actuator/health` (DB 연결 포함) · `/metrics` · `/prometheus`.
