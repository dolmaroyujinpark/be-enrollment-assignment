# 동시성 제어 전략

명세 §3 "동시에 여러 사람이 마지막 자리에 신청하는 경우"를 정확히 처리하기 위한 설계입니다.

## 1. 무엇이 깨지는가

락 없이 순진하게 구현하면:
```
T1: SELECT enrolled_count FROM lectures WHERE id=L   -- 9 (정원 10)
T2: SELECT enrolled_count FROM lectures WHERE id=L   -- 9
T1: 9 < 10 → INSERT enrollment; enrolled_count=10
T2: 9 < 10 → INSERT enrollment; enrolled_count=10   -- 정원 초과 (11명)
```
"읽고 → 검사하고 → 쓰는" 사이에 다른 트랜잭션이 끼어들어, 마지막 자리에 N명이 동시에 오면 N명 다 통과합니다. `enrolled_count <= capacity` CHECK 제약만으로는 일부만 막힙니다. "정확히 정원 수만 성공"을 보장하려면 신청 처리를 직렬화해야 합니다.

## 2. 4-Layer 방어

| Layer | 방법 | 막는 것 | 코드 |
|---|---|---|---|
| 1. 비관 락 | 신청/취소 시 `Lecture` row 에 `SELECT … FOR UPDATE` | 같은 강의의 정원 갱신을 직렬화 → 정확히 정원 수만 성공 | `LectureRepository.findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) |
| 2. 낙관 락 | `lectures.version` (`@Version`) | 비관 락 밖 경로(마이그레이션·어드민 SQL·버그)의 stale write → 커밋 시 충돌 | `Lecture.version` → `OPTIMISTIC_LOCK_CONFLICT` 409 |
| 3. 부분 UNIQUE 인덱스 | `UNIQUE (user_id, lecture_id) WHERE status <> 'CANCELLED'` | 동일 사용자·동일 강의에 active 신청 2개 → 서비스 선검사를 경합으로 통과해도 DB 가 차단 | `V1__init.sql`, 서비스의 `existsByUserIdAndLectureIdAndStatusNot(…, CANCELLED)` 선검사 → `DATA_INTEGRITY_VIOLATION` 409 |
| 4. 멱등성 | `payment_intents.idempotency_key UNIQUE` | 결제 중복 호출이 두 번 처리되는 것 | `PaymentConfirmService.confirm` |

비관 락이 정상 동작하면 2~3은 발동할 일이 없지만, 락 범위 밖 경로/버그를 DB 제약이 끝까지 막습니다(defense in depth).

## 3. 수강 신청 흐름 (`EnrollmentService.apply`)
```
@Transactional
1. userRepository.existsById(userId)            // 없으면 USER_NOT_FOUND 404 (락 전)
2. findByIdForUpdate(lectureId)                 // ← Lecture row 비관 락 (직렬화 시작)
3. lecture.status == OPEN ?                      // 아니면 LECTURE_NOT_OPEN 422 (BR-1)
4. 이미 active 신청 있나?                         // 있으면 DUPLICATE_ENROLLMENT 409 (BR-3) — 락 안에서 검사
5. lecture.hasAvailableSeat() ?                  // 아니면 CAPACITY_EXCEEDED 409 (BR-7 / F9)
6. lecture.incrementEnrolled()                  // enrolled_count + 1
7. enrollmentRepository.save(new Enrollment(PENDING))
8. waitlistService.removeIfPresent(userId, lectureId)
9. commit → 락 해제
```
3·4·5 검사가 락(2) **이후**인 것이 핵심입니다. 락을 잡은 뒤 검사하므로 검사 시점의 상태가 곧 커밋되는 상태이고, 동시에 온 다른 트랜잭션은 2 에서 대기하다 차례로 처리됩니다. 정원은 활성(PENDING + CONFIRMED) 수 기준이며(BR-7), `enrolled_count` 가 그 합계를 캐시합니다(§6).

## 4. 취소 + 대기열 자동 승급 (`EnrollmentService.cancel` → `WaitlistService.promoteNext`)
```
@Transactional
1. enrollment 조회 → 없으면 ENROLLMENT_NOT_FOUND 404
2. enrollment.userId == 헤더 userId ?           // 아니면 NOT_ENROLLMENT_OWNER 403 (BR-10)
3. CONFIRMED & confirmedAt + 7d < now ?         // 그러면 REFUND_WINDOW_PASSED 409 (BR-6 / O1)
4. findByIdForUpdate(lectureId)                 // ← 신청과 동일한 락 경로
5. enrollment.cancel(now)                       // → CANCELLED (이미 CANCELLED 면 INVALID_ENROLLMENT_STATUS_TRANSITION 409)
6. lecture.decrementEnrolled()
7. waitlistService.promoteNext(lecture):
   - hasAvailableSeat() 아니면 종료
   - findNextInQueueForUpdate(lectureId)        // ← ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
   - 있으면: waitlist 항목 삭제 → incrementEnrolled() → 새 Enrollment(PENDING) 생성
8. commit
```
`FOR UPDATE SKIP LOCKED` 를 쓰는 이유: 같은 강의 취소는 4 의 `Lecture` 락으로 이미 직렬화되지만, 다중 인스턴스/다른 경로에서 동시에 같은 대기열 head 를 잡으려 할 때 `SKIP LOCKED` 는 잠긴 row 를 기다리지 않고 건너뛰어 다음 unlocked 항목을 봅니다. 그래서 같은 사람을 두 번 승급하는 일이 없고, 한 명도 막히지 않습니다. (Hibernate: `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))` = `LockOptions.SKIP_LOCKED`.) 순효과는 취소 시 `enrolled_count` 가 `-1` 됐다가 대기열에 사람이 있으면 즉시 `+1` 되어 그 자리를 다음 사람에게 재배정하는 것입니다. `apply` 가 신청 성공 시 그 사용자의 대기열 항목을 제거하므로(`removeIfPresent`), head 사용자가 이미 active 신청을 가져 부분 UNIQUE 인덱스에 걸리는 상황은 발생하지 않습니다.

## 5. 트랜잭션 격리 / 락 순서
- 격리 수준은 `READ_COMMITTED`(PostgreSQL 기본) + 비관 락입니다. `REPEATABLE_READ`/`SERIALIZABLE` 은 정원 카운터 갱신 충돌 시 직렬화 실패(재시도)가 잦아 throughput 손해가 큽니다. 핫스팟(`Lecture` row)에 명시적 행 락을 거는 편이 명확하고 빠릅니다.
- 락 획득 순서는 항상 `Lecture → WaitlistEntry` 로 일관되고 단일 `Lecture` 단위라 데드락 여지가 없습니다.

## 6. `enrolled_count` 비정규화 카운터
강의 목록 조회 시 모든 강의의 신청 인원을 보여줘야 하는데 매번 `enrollments` 를 `COUNT(*)` JOIN 하면 비쌉니다. `lectures.enrolled_count` 에 활성 신청 수를 캐시합니다. 정합성은 신청/취소 시 `Lecture` row 비관 락을 잡은 같은 트랜잭션에서 `enrolled_count` ±1 과 enrollment INSERT/상태변경을 함께 수행해 보장하고, `@Version` 이 stale write 를 추가로 차단합니다. `ConcurrencyTest` 가 `enrolled_count == COUNT(active enrollments)` sanity check 로 검증합니다.

## 7. 이전 유사 과제(in-process 락) 대비

이전에 수행한 유사 과제(Python/FastAPI)는 `threading.Lock` + SQLite `StaticPool` + `db.expire_all()` 트릭에 의존했습니다. 이번에는 다음과 같이 개선했습니다.

| | 이전 과제 (in-process) | 이번 과제 (DB 기반) |
|---|---|---|
| 정원 동시성 | `threading.Lock` — 단일 프로세스 한정 | DB row-level 락 — 다중 인스턴스에서도 정합 |
| 환경 | SQLite + 비표준 트릭 | PostgreSQL + Spring 표준 트랜잭션 |
| 상태 모델 | 단순 enroll/cancel, 상태 없음 | 명시적 FSM, 잘못된 전이 차단 → ProblemDetail 409 |
| 결제·재시도 | 없음 | `Idempotency-Key` 헤더로 멱등성 |
| 대기열 | 없음 | `FOR UPDATE SKIP LOCKED` 기반 자동 승급 |
| 검증 | pytest 스레드 | `CountDownLatch` 동시성 테스트 + Testcontainers(실 PostgreSQL) + K6 부하 |
| 카운터 정합 | 보호는 했으나 입증 약함 | 비관 락 + `@Version` + `enrolled_count == COUNT(active)` sanity check |

## 8. 검증
- `./gradlew test --tests ConcurrencyTest` — Testcontainers 로 실 PostgreSQL 을 띄워, 정원 N 강의에 M명(M>N) 동시 신청 → 정확히 N명만 `201`, 나머지 `409 CAPACITY_EXCEEDED`, 종료 후 `enrolled_count == COUNT(active) == N`. 같은 사용자 동시 중복 신청 → active 정확히 1개. (Docker 가 없으면 이 테스트만 skip, 빌드는 통과.)
- `k6 run load-test/enrollment-burst.k6.js` (앱 실행 후) — 동시 부하에서도 같은 성질 유지 확인.
- 다중 인스턴스로 스케일 아웃 시 분산 락(Redis Redisson 등)을 검토할 수 있으나, DB row 락만으로도 정합은 보장됩니다(README "미구현 / 제약사항" 참조).
