# API 명세

수강 신청 시스템 REST API. 인터랙티브 문서는 앱 실행 후 **Swagger UI** (`http://localhost:8080/swagger-ui.html`), OpenAPI 스펙은 `http://localhost:8080/v3/api-docs`.

---

## 공통 규약

### 인증
상태를 바꾸는 요청(강의 등록·상태 전이, 신청·결제·취소, 대기열 등록)과 권한이 필요한 조회(크리에이터 전용)는 **헤더 `X-User-Id: <userId>`** 필수. (명세에서 허용한 간이 방식 — JWT/세션 미구현.)
시드 데이터 기준: 크리에이터 `id 1~5`, 클래스메이트 `id 6~35`. 강의 `id 1~3` DRAFT / `4~17` OPEN / `18~20` CLOSED (결정론적 시드).

### 에러 응답 — RFC 7807 `application/problem+json`
```json
{
  "type": "about:blank",
  "title": "OPEN 상태의 강의에만 신청할 수 있습니다.",
  "status": 422,
  "detail": "현재 강의 상태: DRAFT",
  "code": "LECTURE_NOT_OPEN"
}
```
`code` 로 케이스 식별. 주요 코드:

| code | HTTP | 의미 |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 요청 body 검증 실패 (`detail` 에 필드별 메시지) |
| `BAD_REQUEST` / `ILLEGAL_ARGUMENT` | 400 | 헤더 누락·타입 불일치 등 |
| `USER_NOT_FOUND` / `LECTURE_NOT_FOUND` / `ENROLLMENT_NOT_FOUND` | 404 | 대상 없음 |
| `NOT_CREATOR` | 403 | CREATOR 역할 아님 (강의 등록) |
| `NOT_LECTURE_OWNER` | 403 | 강의 작성 크리에이터 아님 (상태 전이·수강생/대기열 조회) |
| `NOT_ENROLLMENT_OWNER` | 403 | 본인 신청 아님 (결제·취소) |
| `LECTURE_NOT_OPEN` | 422 | OPEN 이 아닌 강의에 신청·대기열 등록 |
| `INVALID_LECTURE_STATUS_TRANSITION` | 409 | 허용되지 않는 강의 상태 전이 |
| `INVALID_ENROLLMENT_STATUS_TRANSITION` | 409 | 허용되지 않는 신청 상태 전이 (이미 확정/취소된 신청에 결제·취소) |
| `CAPACITY_EXCEEDED` | 409 | 정원 초과 |
| `DUPLICATE_ENROLLMENT` | 409 | 동일 강의에 active 신청 이미 있음 |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | 같은 Idempotency-Key 를 다른 신청에 사용 |
| `REFUND_WINDOW_PASSED` | 409 | CONFIRMED 신청을 결제 후 7일 경과 후 취소 |
| `ALREADY_IN_WAITLIST` | 409 | 이미 대기열에 등록됨 |
| `DATA_INTEGRITY_VIOLATION` | 409 | DB 제약 위반 (경합 상황의 최종 방어선) |
| `OPTIMISTIC_LOCK_CONFLICT` | 409 | 낙관 락(@Version) 충돌 |
| `INTERNAL_ERROR` | 500 | 처리되지 않은 예외 |

### 페이지네이션
목록 조회는 `?page=0&size=20` (0-base). 응답:
```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 14, "totalPages": 1, "hasNext": false }
```

### 응답 헤더 `X-Trace-Id`
모든 응답에 요청별 trace id 가 붙음 (구조화 로그의 `traceId` 와 동일). 요청에 `X-Trace-Id` 를 보내면 그 값을 그대로 사용.

---

## 엔드포인트

### `GET /health` — 헬스체크
```bash
curl http://localhost:8080/health
```
```json
{ "status": "UP" }
```
> DB 연결 등 상세 헬스는 `GET /actuator/health` (Actuator). 메트릭 `GET /actuator/metrics`, `GET /actuator/prometheus`.

---

### `POST /api/lectures` — 강의 등록 (CREATOR)
헤더: `X-User-Id` (CREATOR 역할). Body:
```bash
curl -X POST http://localhost:8080/api/lectures \
  -H 'X-User-Id: 1' -H 'Content-Type: application/json' \
  -d '{"title":"Spring Boot 백엔드","description":"JPA·트랜잭션·동시성","price":199000,"capacity":20,"startDate":"2026-06-01","endDate":"2026-07-01"}'
```
`201 Created`, `Location: /api/lectures/{id}`:
```json
{
  "id": 21, "creatorId": 1, "title": "Spring Boot 백엔드", "description": "JPA·트랜잭션·동시성",
  "price": 199000.00, "capacity": 20, "enrolledCount": 0, "availableSeats": 20,
  "startDate": "2026-06-01", "endDate": "2026-07-01", "status": "DRAFT",
  "createdAt": "2026-06-01T00:00:00Z", "updatedAt": "2026-06-01T00:00:00Z"
}
```
에러: `403 NOT_CREATOR` (CLASSMATE 가 호출), `404 USER_NOT_FOUND`, `400 VALIDATION_FAILED` (제목 누락·정원 0 이하·날짜 누락 등).

---

### `GET /api/lectures` — 강의 목록 (status 필터·페이지네이션)
```bash
curl 'http://localhost:8080/api/lectures?status=OPEN&page=0&size=5'
```
`200 OK`:
```json
{
  "content": [
    { "id": 17, "creatorId": 3, "title": "기술 블로그 글쓰기", "price": 79000.00,
      "capacity": 8, "enrolledCount": 2, "availableSeats": 6, "status": "OPEN",
      "startDate": "2026-06-10", "endDate": "2026-07-08", "createdAt": "...", "updatedAt": "..." }
  ],
  "page": 0, "size": 5, "totalElements": 14, "totalPages": 3, "hasNext": true
}
```
`status` 생략 시 전체. 잘못된 status 값 → `400`.

---

### `GET /api/lectures/{id}` — 강의 상세 (현재 신청 인원 포함)
```bash
curl http://localhost:8080/api/lectures/4
```
`200 OK` — `LectureResponse` (위 등록 응답과 동일 형식, `enrolledCount`/`availableSeats` 포함). 없으면 `404 LECTURE_NOT_FOUND`.

---

### `PATCH /api/lectures/{id}/status` — 강의 상태 전이 (작성 크리에이터)
헤더: `X-User-Id` (해당 강의 작성자). DRAFT→OPEN→CLOSED 단방향만.
```bash
curl -X PATCH http://localhost:8080/api/lectures/1/status \
  -H 'X-User-Id: 1' -H 'Content-Type: application/json' -d '{"status":"OPEN"}'
```
`200 OK` — 갱신된 `LectureResponse`. 에러: `403 NOT_LECTURE_OWNER`, `409 INVALID_LECTURE_STATUS_TRANSITION` (예: DRAFT→CLOSED, OPEN→DRAFT, CLOSED→*), `404 LECTURE_NOT_FOUND`.

---

### `GET /api/lectures/{id}/enrollments` — 강의별 수강생 목록 (크리에이터 전용)
헤더: `X-User-Id` (해당 강의 작성자). 신청 순서(id asc)·페이지네이션.
```bash
curl 'http://localhost:8080/api/lectures/4/enrollments?page=0&size=20' -H 'X-User-Id: 1'
```
`200 OK` — `PageResponse<EnrollmentResponse>` (CANCELLED 포함 전체). 에러: `403 NOT_LECTURE_OWNER`, `404 LECTURE_NOT_FOUND`.

---

### `POST /api/lectures/{id}/waitlist` — 대기열 등록
헤더: `X-User-Id`. OPEN 강의에 한해, 이미 active 신청이 없고 대기열에도 없을 때.
```bash
curl -X POST http://localhost:8080/api/lectures/4/waitlist -H 'X-User-Id: 7'
```
`201 Created`:
```json
{ "id": 1, "userId": 7, "lectureId": 4, "createdAt": "2026-06-01T00:00:00Z" }
```
에러: `422 LECTURE_NOT_OPEN`, `409 DUPLICATE_ENROLLMENT` (이미 신청함), `409 ALREADY_IN_WAITLIST`, `404 USER_NOT_FOUND`/`LECTURE_NOT_FOUND`.

---

### `GET /api/lectures/{id}/waitlist` — 대기열 조회 (크리에이터 전용)
헤더: `X-User-Id` (해당 강의 작성자). 등록 순서(createdAt asc, FIFO)·페이지네이션.
```bash
curl 'http://localhost:8080/api/lectures/4/waitlist' -H 'X-User-Id: 1'
```
`200 OK` — `PageResponse<WaitlistResponse>`. 에러: `403 NOT_LECTURE_OWNER`, `404 LECTURE_NOT_FOUND`.

---

### `POST /api/enrollments` — 수강 신청
헤더: `X-User-Id`. OPEN 강의에 신청 → PENDING 생성.
```bash
curl -X POST http://localhost:8080/api/enrollments \
  -H 'X-User-Id: 6' -H 'Content-Type: application/json' -d '{"lectureId":4}'
```
`201 Created`, `Location: /api/enrollments/{id}`:
```json
{ "id": 101, "userId": 6, "lectureId": 4, "status": "PENDING",
  "appliedAt": "2026-06-01T00:00:00Z", "confirmedAt": null, "cancelledAt": null, "paymentIntentId": null }
```
에러: `422 LECTURE_NOT_OPEN` (DRAFT/CLOSED 강의), `409 CAPACITY_EXCEEDED` (정원 초과 — 동시 신청 시 정원 수만 성공, 나머지 이 에러), `409 DUPLICATE_ENROLLMENT` (동일 강의 active 신청 이미 있음), `404 USER_NOT_FOUND`/`LECTURE_NOT_FOUND`.

---

### `POST /api/enrollments/{id}/payment` — 결제 확정
헤더: `X-User-Id` (신청 본인), `Idempotency-Key` (필수). PENDING→CONFIRMED. 같은 키로 재호출 시 상태 변경 없이 동일 응답.
```bash
curl -X POST http://localhost:8080/api/enrollments/101/payment \
  -H 'X-User-Id: 6' -H 'Idempotency-Key: pay-101-abc'
```
`200 OK`:
```json
{ "id": 101, "userId": 6, "lectureId": 4, "status": "CONFIRMED",
  "appliedAt": "2026-06-01T00:00:00Z", "confirmedAt": "2026-06-01T00:05:00Z", "cancelledAt": null, "paymentIntentId": 1 }
```
에러: `409 INVALID_ENROLLMENT_STATUS_TRANSITION` (PENDING 아닌 신청에 결제), `409 IDEMPOTENCY_KEY_CONFLICT` (같은 키를 다른 신청에 사용), `403 NOT_ENROLLMENT_OWNER` (본인 신청 아님 — 정상 호출 및 같은 키 리플레이 양쪽에서 검사), `404 ENROLLMENT_NOT_FOUND`, `400` (`Idempotency-Key` 헤더 누락).

---

### `DELETE /api/enrollments/{id}` — 수강 취소
헤더: `X-User-Id` (신청 본인). →CANCELLED. CONFIRMED 신청은 결제 후 7일 이내만. 취소로 자리가 비면 대기열 다음 사람이 자동 PENDING 승급.
```bash
curl -X DELETE http://localhost:8080/api/enrollments/101 -H 'X-User-Id: 6'
```
`200 OK`:
```json
{ "id": 101, "userId": 6, "lectureId": 4, "status": "CANCELLED",
  "appliedAt": "...", "confirmedAt": "...", "cancelledAt": "2026-06-02T00:00:00Z", "paymentIntentId": 1 }
```
에러: `403 NOT_ENROLLMENT_OWNER`, `409 REFUND_WINDOW_PASSED` (결제 후 7일 경과), `409 INVALID_ENROLLMENT_STATUS_TRANSITION` (이미 CANCELLED), `404 ENROLLMENT_NOT_FOUND`.

---

### `GET /api/enrollments/me` — 내 수강 신청 목록
헤더: `X-User-Id`. 페이지네이션, 최신순(id desc).
```bash
curl 'http://localhost:8080/api/enrollments/me?page=0&size=20' -H 'X-User-Id: 6'
```
`200 OK` — `PageResponse<EnrollmentResponse>`.

---

## 동시성 동작 (요약)
- **마지막 자리 동시 신청**: `POST /api/enrollments` 가 `Lecture` row 에 `PESSIMISTIC_WRITE` 락 → 차례로 직렬화 → 정원 수만 `201`, 나머지 `409 CAPACITY_EXCEEDED`. 부분 UNIQUE 인덱스가 동일 강의 중복 신청을, `payment_intents.idempotency_key` UNIQUE 가 결제 경합을 최종 방어. 상세는 `docs/CONCURRENCY.md`.
- 검증: `./gradlew test --tests ConcurrencyTest` (Testcontainers), 부하: `k6 run load-test/enrollment-burst.k6.js` (앱 실행 후).
