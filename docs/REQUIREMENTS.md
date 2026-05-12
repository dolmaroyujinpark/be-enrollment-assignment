# 요구사항 분석 및 설계 결정

## 1. 명세 요약

라이브 클래스의 BE-A 수강 신청 시스템은 다음 도메인 흐름을 다룹니다:

```
크리에이터 ──[강의 개설]──> Lecture (DRAFT)
                              │
                              └──[OPEN 전이]──> 수강 신청 가능
                                                  │
클래스메이트 ──[수강 신청]──> Enrollment (PENDING)
                                  │
                                  └──[결제 확정]──> CONFIRMED
                                                       │
                                                       └──[7일 내 취소]──> CANCELLED
```

자세한 분류는 [docs/SCOPE.md](SCOPE.md) 참조.

---

## 2. 비즈니스 규칙 결정사항

명세에 명시되지 않았으나 시스템 구현에 필수적인 결정 11건. 각 결정에는 근거와 대안 검토 결과가 포함됩니다.

### BR-1. 강의 신청 가능 상태 = OPEN 전용  `[필수 F5]`

**결정**: 강의가 `OPEN` 상태일 때만 신청 가능. `DRAFT` / `CLOSED` 는 모두 거부 (HTTP 422).

**근거**: 명세에서 "DRAFT: 신청 불가, CLOSED: 모집 마감(신청 불가)" 명시.

### BR-2. 강의 상태 단방향 전이  `[필수 F2]`

**결정**:
- `DRAFT → OPEN` 만 허용
- `OPEN → CLOSED` 만 허용
- `CLOSED → ?` 모두 불가 (재오픈 불가)
- `OPEN → DRAFT` 불가

**근거**: 명세의 화살표 표기(`DRAFT → OPEN → CLOSED`) 가 단방향임을 시사. 재오픈을 허용하면 신청자가 이미 있는 강의가 다시 DRAFT 가 되는 비정상 상태가 가능.

**구현**: `LectureStatus#canTransitionTo` 메서드로 전이 가능 여부 명시. 잘못된 전이 시 `IllegalStateException`.

### BR-3. 동일 강의 동일 사용자 active 신청 1개  `[추가 P4]`

**결정**: 같은 사용자가 같은 강의에 대해 `PENDING` 또는 `CONFIRMED` 상태인 신청을 동시에 2개 이상 가질 수 없음. `CANCELLED` 상태는 이력으로 남으므로 재신청 가능.

**근거**: 수강 신청의 일반적 상식. 취소 후 재신청은 허용되어야 사용자 경험이 자연스러움.

**구현**: PostgreSQL 부분 UNIQUE 인덱스 — `CREATE UNIQUE INDEX ON enrollments(user_id, lecture_id) WHERE status <> 'CANCELLED'`.

### BR-4. 결제 확정은 PENDING 상태에서만  `[필수 F6]`

**결정**: PENDING → CONFIRMED 만 허용. CONFIRMED 또는 CANCELLED 에서 결제 확정 시도하면 거부.

**근거**: 명세 상태 전이도. 멱등성 보장(P1)을 위해 동일 idempotency_key 로 두 번째 호출 시는 동일 응답 반환 (실제 상태 변경은 안 일어남).

### BR-5. 취소는 PENDING 또는 CONFIRMED 에서만  `[필수 F7]`

**결정**: 두 상태에서 모두 취소 가능. 단, `CONFIRMED` 의 경우 BR-6 시간 제한 추가.

### BR-6. CONFIRMED 후 7일 이내만 취소 가능  `[선택 O1]`

**결정**: `CONFIRMED` 상태에서 `confirmed_at` 이후 7일이 지나면 취소 불가. `PENDING` 은 시간 제한 없음.

**근거**: 명세 선택 구현 예시 ("결제 후 7일 이내"). 7일은 settings 로 변경 가능하게 추상화.

**구현**: `Enrollment#cancel(now, refundWindow)` 메서드에 Duration 주입.

### BR-7. 정원 = 활성(PENDING + CONFIRMED) 신청 수 기준  `[필수 F9]`

**결정**: 정원 카운터는 `PENDING + CONFIRMED` 합계. `PENDING` 도 자리를 점유한다고 봄.

**근거**:
- 결제 직전 사용자도 자리 보유로 봐야 공정. 결제 시점에 자리가 사라지면 사용자 경험 최악
- 무한 PENDING 점유 방지는 별도 정책 필요 (예: 24시간 미결제 시 자동 취소). 본 과제 범위는 아님

**대안 검토**:
- (대안) `CONFIRMED` 만 카운트 → 동시에 100명이 PENDING 만들고 결제 안 하면 강의가 사실상 마비됨
- (선택안) Redis TTL 기반 임시 hold → 인프라 추가 부담. MVP 범위 외

### BR-8. 대기열(waitlist) — 명시적 등록 엔드포인트  `[선택 O2]`

**결정**: 정원이 찬 경우 신청 응답으로 자동 대기열 등록하는 대신, 별도 엔드포인트 `POST /api/lectures/{id}/waitlist` 로 명시적 등록한다 (OPEN 강의에 한해, 이미 active 신청이 없고 대기열에도 없을 때). 강의별 대기열 조회는 `GET /api/lectures/{id}/waitlist` (작성 크리에이터 전용).

**근거**: "신청 시 정원 초과면 자동으로 대기열 등록 + `waitlist=false` 옵트아웃"도 검토했으나, `POST /api/enrollments` 의 응답 형태가 (신청됨 / 대기 등록됨) 다형성이 되는 게 API 계약상 부담스러워, 대기열을 독립 리소스로 분리하는 편이 명확하다고 판단. 취소 발생 시 head 1명 자동 PENDING 승급은 BR-9 참조.

### BR-9. 취소 발생 시 waitlist 첫 사람에게 자동 PENDING 생성  `[추가 P3]`

**결정**: 어떤 사용자가 신청을 취소하면, 동일 강의의 waitlist 에서 가장 오래된 항목(FIFO)을 찾아 자동으로 PENDING 신청 생성. 해당 사용자에게는 별도 결제 시한 부여 (현재 구현은 무기한, 운영에서는 알림 + 24시간 결제 마감 권장).

**구현**: `WaitlistService#promoteNext` + PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED` — 다중 인스턴스에서도 안전하게 한 명만 승급.

### BR-10. 신청자 본인만 자기 신청 취소 가능  `[추가 P11]`

**결정**: 헤더 `X-User-Id` 와 `enrollments.user_id` 가 일치하지 않으면 403 Forbidden.

### BR-11. 강의별 수강생 목록은 강의 작성 크리에이터만  `[선택 O3]`

**결정**: `GET /api/lectures/{id}/enrollments` 호출자(`X-User-Id`) 가 해당 강의의 `creator_id` 와 일치하지 않으면 403.

---

## 3. 인증 / 인가

명세에서 "userId 를 헤더나 파라미터로 전달하는 방식도 허용"이라 명시. 본 과제는 다음 정책:

- **헤더 `X-User-Id`** 으로 사용자 식별
- 모든 신청/취소/결제 API 가 헤더 필수
- 강의 등록은 `X-User-Id` 의 사용자가 `CREATOR` 역할이어야 함
- 헤더 누락/위조에 대한 방어는 본 과제 범위 외

**프로덕션 전환 시**: JWT 인증 + Spring Security. README "미구현 / 제약사항" 참조.

---

## 4. 동시성 제어 전략 요약

(상세는 [docs/CONCURRENCY.md](CONCURRENCY.md))

4-Layer Defense:
1. **Layer 1 — DB row-level lock**: `@Lock(PESSIMISTIC_WRITE)` on Lecture
2. **Layer 2 — 낙관 락**: `@Version` on Lecture
3. **Layer 3 — 부분 UNIQUE 인덱스**: 동일 사용자 active enrollment 1개만
4. **Layer 4 — 멱등성**: `Idempotency-Key` 헤더로 결제 확정 재시도 안전

**이전 유사 과제 대비 진화**: 이전에 수행한 유사 과제(Python/FastAPI)는 `threading.Lock` 기반이라 단일 프로세스 한계. 이번엔 DB row lock 기반이라 다중 인스턴스에서도 정합성 유지.

---

## 5. 향후 확장 (의도적 미구현)

| 우선순위 | 항목 | 사유 |
|---|---|---|
| 높음 | JWT 인증 + 권한 분리 | 프로덕션 필수 |
| 높음 | PENDING 자동 만료 (24h) | BR-7 정책 보완 |
| 중간 | 알림 발송 (이메일/푸시) | BE-C 과제 영역 |
| 중간 | 결제 PG 연동 (토스페이먼츠 등) | 명세 범위 외 |
| 낮음 | 분산 락(Redis Redisson) | 다중 인스턴스 스케일업 시 |
| 낮음 | 환불 처리 | 본 과제 범위 외 |
