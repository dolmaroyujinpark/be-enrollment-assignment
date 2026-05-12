# 동시성 제어 전략

명세 §3 "동시에 여러 사람이 마지막 자리에 신청하는 경우"를 정확히 처리하기 위한 설계.

## 1. 무엇이 깨지는가 — race condition

순진하게 구현하면 (락 없이):
```
T1: SELECT enrolled_count FROM lectures WHERE id=L   -- 9 (정원 10)
T2: SELECT enrolled_count FROM lectures WHERE id=L   -- 9
T1: 9 < 10 → INSERT enrollment; UPDATE lectures SET enrolled_count=10
T2: 9 < 10 → INSERT enrollment; UPDATE lectures SET enrolled_count=10   -- 정원 1 초과! 11명
```
"읽고 → 검사하고 → 쓰는" 사이에 다른 트랜잭션이 끼어들 수 있어서, 마지막 자리에 N명이 동시에 오면 N명 다 통과해 버린다. 단순히 `enrolled_count` 컬럼에 CHECK 제약(`enrolled_count <= capacity`)만 걸어도 일부는 막히지만, "정확히 정원 수만 성공"을 보장하려면 신청 처리 자체를 직렬화해야 한다.

## 2. 4-Layer 방어

| Layer | 무엇 | 막는 것 | 코드 |
|---|---|---|---|
| **1. DB row-level 비관 락** | 신청/취소 시 `Lecture` row 에 `SELECT … FOR UPDATE` | 같은 강의에 대한 정원 갱신을 **직렬화** — 마지막 자리 동시 신청 시 정확히 정원 수만 성공 | `LectureRepository.findByIdForUpdate` (`@Lock(PESSIMISTIC_WRITE)`) |
| **2. 낙관 락 (`@Version`)** | `lectures.version` 컬럼, 갱신마다 +1 | 비관 락을 우회한 경로(예: 마이그레이션, 어드민 직접 SQL, 코드 버그)가 `enrolled_count` 를 stale 하게 덮어쓰는 것 → 커밋 시 `ObjectOptimisticLockingFailureException` | `Lecture.version` (`@Version`) → `OPTIMISTIC_LOCK_CONFLICT` 409 |
| **3. 부분 UNIQUE 인덱스** | `CREATE UNIQUE INDEX uq_enrollments_active ON enrollments(user_id, lecture_id) WHERE status <> 'CANCELLED'` | 동일 사용자가 동일 강의에 active(PENDING/CONFIRMED) 신청을 2개 갖는 것 — 서비스 선검사를 경합으로 통과해도 DB 가 최종 차단(`DataIntegrityViolationException` → 409) | `V1__init.sql`, 서비스의 `existsByUserIdAndLectureIdAndStatusNot(…, CANCELLED)` 선검사 |
| **4. 멱등성 (Idempotency-Key)** | `payment_intents.idempotency_key UNIQUE`. 결제 확정 시 같은 키면 기존 결과 반환 | 결제 재시도/중복 호출이 두 번 처리되는 것. 동일 키 동시 경합은 UNIQUE 제약이 최종 방어 | `PaymentConfirmService.confirm`, `payment_intents.idempotency_key UNIQUE` |

"한 겹이 막아도 다음 겹이 받친다" — 비관 락이 정상 동작하면 2~3은 발동할 일이 없지만, 락 범위를 벗어난 경로/버그를 DB 제약이 끝까지 막는다 (defense in depth).

## 3. 수강 신청 흐름 (`EnrollmentService.apply`)

```
@Transactional
1. userRepository.existsById(userId)                       // 없으면 USER_NOT_FOUND 404 (락 전, 빠른 실패)
2. lecture = lectureRepository.findByIdForUpdate(lectureId) // ← Lecture row 에 PESSIMISTIC_WRITE 락 (직렬화 시작)
3. lecture.status == OPEN ?                                 // 아니면 LECTURE_NOT_OPEN 422  (BR-1)
4. 이미 active 신청 있나? (existsBy…StatusNot CANCELLED)    // 있으면 DUPLICATE_ENROLLMENT 409  (BR-3) — 락 안에서 재검사
5. lecture.hasAvailableSeat() ?                             // 아니면 CAPACITY_EXCEEDED 409  (BR-7 / F9)
6. lecture.incrementEnrolled()                             // enrolled_count + 1 (도메인 가드도 한 번 더)
7. enrollmentRepository.save(new Enrollment(PENDING))
8. waitlistService.removeIfPresent(userId, lectureId)      // enrollment 와 waitlist 가 공존하지 않게
9. commit → 락 해제
```
- **3·4·5 가 락(2) 이후**인 게 핵심: 락을 잡은 뒤에 OPEN·중복·정원을 검사하므로, 검사 시점의 상태가 곧 커밋되는 상태다. 동시에 온 다른 트랜잭션은 6~9 가 끝나고 커밋될 때까지 2 에서 대기 → 차례로 처리.
- 정원 = **활성(PENDING + CONFIRMED) 수** 기준 (BR-7). 결제 직전(PENDING) 사용자도 자리를 점유 — 결제 시점에 자리가 사라지지 않게. (`enrolled_count` 가 그 합계를 캐시. §6 참조)

## 4. 취소 + 대기열 자동 승급 (`EnrollmentService.cancel` → `WaitlistService.promoteNext`)

```
@Transactional
1. enrollment 조회 → 없으면 ENROLLMENT_NOT_FOUND 404
2. enrollment.userId == 헤더 userId ?                      // 아니면 NOT_ENROLLMENT_OWNER 403  (BR-10)
3. CONFIRMED & confirmedAt + 7d < now ?                    // 그러면 REFUND_WINDOW_PASSED 409  (BR-6 / O1)
4. lecture = lectureRepository.findByIdForUpdate(lectureId) // ← 신청과 동일한 락 경로 (신청↔취소 직렬화)
5. enrollment.cancel(now)                                  // → CANCELLED  (이미 CANCELLED 면 INVALID_ENROLLMENT_STATUS_TRANSITION 409)
6. lecture.decrementEnrolled()                             // enrolled_count - 1
7. waitlistService.promoteNext(lecture):
   - lecture.hasAvailableSeat() 아니면 종료
   - waitlistRepository.findNextInQueueForUpdate(lectureId)   // ← SELECT … ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
   - 있으면: waitlist 항목 삭제 → lecture.incrementEnrolled() → 새 Enrollment(PENDING) 생성
8. commit
```
- **왜 `FOR UPDATE SKIP LOCKED`**: 같은 강의 취소는 4 의 `Lecture` 락으로 이미 직렬화되지만, 다중 인스턴스/다른 경로에서 동시에 같은 대기열 head 를 잡으려 할 때 — `SKIP LOCKED` 는 이미 잠긴 row 를 기다리지 않고 **건너뛰어 다음 unlocked 항목**을 본다. 그래서 두 트랜잭션이 같은 사람을 두 번 승급하는 일이 없고, 한 명도 막히지 않는다. (Hibernate: `@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))` = `LockOptions.SKIP_LOCKED`)
- 순효과: 활성 신청이 취소되면 `enrolled_count` 가 `-1` 됐다가, 대기열에 사람이 있으면 즉시 `+1` 되어 그 자리를 다음 사람에게 재배정 (대기열이 비었으면 그대로 `-1`).
- `apply` 가 신청 성공 시 그 사용자의 대기열 항목을 제거(`removeIfPresent`)하므로, head 의 사용자가 이미 active 신청을 갖고 있어 부분 UNIQUE 인덱스에 걸리는 상황은 발생하지 않는다 (걸려도 그게 최종 안전망).

## 5. 트랜잭션 격리 / 락 순서 / 데드락
- **격리 수준**: `READ_COMMITTED`(PostgreSQL 기본) + 비관 락. `REPEATABLE_READ`/`SERIALIZABLE` 은 정원 카운터 갱신 충돌 시 직렬화 실패(재시도)가 잦아 throughput 손해가 큼 — 핫스팟(`Lecture` row)에 명시적 행 락을 거는 편이 명확하고 빠르다.
- **락 획득 순서**: 신청·취소 모두 `Lecture` row 한 개만 잠그고, 대기열 승급은 그 안에서 `WaitlistEntry` 를 SKIP LOCKED 로 잡는다. 잠그는 순서가 항상 `Lecture → WaitlistEntry` 로 일관되고 단일 `Lecture` 단위라 락 순환(데드락) 여지가 없다.

## 6. `enrolled_count` 비정규화 카운터
- 강의 목록 조회 시 모든 강의의 현재 신청 인원을 보여줘야 하는데, 매번 `enrollments` 를 `COUNT(*)` JOIN 하면 N+1 또는 비싼 group by. 그래서 `lectures.enrolled_count` 컬럼에 **활성 신청 수를 캐시**한다.
- 정합성: 신청/취소 시 `Lecture` row 에 비관 락을 잡은 같은 트랜잭션에서 `enrolled_count` ±1 과 enrollment INSERT/상태변경을 함께 수행 → 둘이 어긋날 수 없음. `@Version` 이 stale write 를 추가로 차단. `ConcurrencyTest` 가 `enrolled_count == COUNT(active enrollments)` sanity check 로 검증.

## 7. 이전 과제(in-process 락) 대비 진화

이전에 수행한 유사 과제(다른 회사, Python/FastAPI)는 `threading.Lock` + SQLite `StaticPool` + `db.expire_all()` 트릭에 의존했다. 이번 과제는 그 한계를 다음과 같이 개선했다:

| | 이전 과제 (in-process) | 이번 과제 (DB 기반) |
|---|---|---|
| 정원 동시성 | `threading.Lock` — **단일 프로세스 한정**, 워커/인스턴스 늘면 깨짐 | DB row-level 락(`SELECT … FOR UPDATE`) — **여러 인스턴스에서도 정합** |
| 환경 | SQLite `StaticPool` + `expire_all()` 등 비표준 트릭 | PostgreSQL + Spring 표준 트랜잭션 |
| 상태 모델 | 단순 enroll/cancel, 상태 없음 | 명시적 FSM — 잘못된 전이는 도메인 메서드에서 차단 → ProblemDetail 409 |
| 결제·재시도 | 없음 | `Idempotency-Key` 헤더로 결제 확정 멱등성 |
| 대기열 | 없음 | `FOR UPDATE SKIP LOCKED` 기반 자동 승급 |
| 검증 | pytest 스레드 | `CountDownLatch` 동시성 테스트 + Testcontainers(실 PostgreSQL) + K6 부하 |
| 카운터 정합 | 보호는 했으나 입증 약함 | 비관 락 + `@Version` 이중 보호 + `enrolled_count == COUNT(active)` sanity check |

## 8. 검증
- `./gradlew test --tests ConcurrencyTest` — Testcontainers 로 실제 PostgreSQL 띄워, 정원 N 강의에 M명(M>N) 동시 신청 → 정확히 N명만 `201`, 나머지 `409 CAPACITY_EXCEEDED`, 종료 후 `enrolled_count == COUNT(active) == N`. + 같은 사용자 동시 중복 신청 → active 정확히 1개.
- `k6 run load-test/enrollment-burst.k6.js` (앱 실행 후) — 동시 부하 상황에서도 같은 성질 유지 확인.
- 향후 확장: 다중 인스턴스로 스케일 아웃 시 분산 락(Redis Redisson 등)을 고려할 수 있으나, DB row 락만으로도 정합은 보장됨 — README "미구현 / 제약사항" 참조.
