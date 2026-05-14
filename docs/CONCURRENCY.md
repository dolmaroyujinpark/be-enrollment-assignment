# 동시성 제어

명세 §3 "마지막 자리 동시 신청" 을 정확히 처리하기 위한 다층 방어. 각 layer 는 서로 다른 카테고리의 race 를 1:1 로 막습니다 — 감으로 쌓은 게 아니라 필요한 실패 모드만 매핑했습니다.

## 문제

락 없이 구현하면 "읽고 → 검사하고 → 쓰는" 사이에 다른 트랜잭션이 끼어들어 정원 초과 발생. `enrolled_count <= capacity` CHECK 만으로는 부분만 막힘.

## 다층 방어 (3 + 1)

| Layer | 수단 | 막는 카테고리 |
|---|---|---|
| 1. 비관 락 (`Lecture` row) | `SELECT … FOR UPDATE` — `LectureRepository.findByIdForUpdate` | **정원 race.** 신청·취소·`status` 변경 모두 같은 락 큐에서 직렬화. `Lecture` row 의 정합성은 이 락 하나로 처리 |
| 2. 낙관 락 (`Enrollment.@Version`) | Hibernate `@Version` | **동일 사용자 동시 cancel race.** 비관 락이 `Lecture` 단위라 enrollment 단위 stale write 는 잡지 못함 → `OPTIMISTIC_LOCK_CONFLICT 409` |
| 3. 부분 UNIQUE (`uq_enrollments_active`) | DB 인덱스 | **앱 외 경로(배치·수동 SQL) 의 중복 신청.** 락 안 선검사가 잡지만 DB 레벨 마지막 가드 → `DATA_INTEGRITY_VIOLATION 409` |
| 4. 결제 멱등성 | `payment_intents.idempotency_key UNIQUE` + 리플레이 경로 본인 검증 | **결제 재시도·이중 청구·키 추측 시 정보 노출.** 동시성과 다른 차원의 문제 |

> **왜 `Lecture.@Version` 은 없는가** — `Lecture` row 의 모든 변경(`enrolled_count` ±1, `status` 전이) 이 `findByIdForUpdate` 를 거치도록 정리해서, 락 밖 stale write 경로 자체가 없습니다. 낙관 락은 코드 낭비라 제거.

## 흐름 — 신청 (`EnrollmentService.apply`)

```
@Transactional
1. userRepository.existsById(userId)            // 락 전 USER_NOT_FOUND
2. findByIdForUpdate(lectureId)                 // ← Lecture row 비관 락
3. lecture.status == OPEN ?                      // LECTURE_NOT_OPEN
4. 이미 active 신청 있나?                         // DUPLICATE_ENROLLMENT (락 안에서 검사)
5. hasAvailableSeat() ?                          // CAPACITY_EXCEEDED
6. enrolledCount + 1, INSERT enrollment(PENDING)
7. waitlist 항목 제거
8. commit → 락 해제
```

3·4·5 검사가 락(2) **뒤**에 있어 검사 시점의 상태가 곧 커밋되는 상태.

## 흐름 — 취소 + 자동 승급 (`EnrollmentService.cancel`)

```
@Transactional
1. enrollment 조회 → ENROLLMENT_NOT_FOUND
2. 본인 검사 → NOT_ENROLLMENT_OWNER
3. CONFIRMED + 7일 경과 ? → REFUND_WINDOW_PASSED
4. findByIdForUpdate(lectureId)                 // 신청과 동일 락 경로
5. enrollment.cancel(now)                       // FSM 통과 → CANCELLED
6. enrolledCount - 1
7. waitlistService.promoteNext(lecture):
   - lecture.status == OPEN 아니면 종료
   - hasAvailableSeat() 아니면 종료
   - findNextInQueueForUpdate                   // FOR UPDATE SKIP LOCKED
   - 있으면: waitlist 삭제 → enrolledCount + 1 → 새 PENDING
8. commit
```

`FOR UPDATE SKIP LOCKED` — 다중 인스턴스에서 같은 head 를 두 번 승급하지 않으면서 wait 없이 다음 항목으로 넘어감. Hibernate hint: `jakarta.persistence.lock.timeout = -2`.

## 격리 / 락 순서

- 격리: PostgreSQL 기본 `READ_COMMITTED` + 비관 락
- 락 획득 순서: `Lecture → WaitlistEntry` 일관, 단일 `Lecture` 단위라 데드락 없음

## 비정규화 카운터 정합성

`lectures.enrolled_count` 는 활성 신청 수 캐시. `Lecture` 비관 락 안에서 enrollment INSERT/상태변경과 같이 ±1. `ConcurrencyTest` 가 `enrolled_count == COUNT(active enrollments)` sanity check.

## 검증

- `./gradlew test --tests ConcurrencyTest` — Testcontainers 실 PG 위에서 3 시나리오: 정원 N + M명 동시 신청·같은 사용자 동시 중복 신청·같은 사용자 동시 cancel
- `k6 run load-test/enrollment-burst.k6.js` — 부하 상황에서 정원 정확성

## 확장

다중 인스턴스 스케일 아웃 시 분산 락 (Redis Redisson) 검토 가능. 본 과제 범위에선 DB row 락만으로도 정합성 보장.
