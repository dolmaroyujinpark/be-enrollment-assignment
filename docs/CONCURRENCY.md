# 동시성 제어

명세 §3 "마지막 자리 동시 신청" 을 정확히 처리하기 위한 4-Layer 방어.

## 문제

락 없이 구현하면 "읽고 → 검사하고 → 쓰는" 사이에 다른 트랜잭션이 끼어들어 정원 초과 발생. `enrolled_count <= capacity` CHECK 만으로는 부분만 막힘.

## 4-Layer 방어

| Layer | 수단 | 막는 것 | 코드 |
|---|---|---|---|
| 1. 비관 락 | `Lecture` row `SELECT … FOR UPDATE` | 정원 갱신 직렬화 | `LectureRepository.findByIdForUpdate` |
| 2. 낙관 락 | `Lecture.@Version` · `Enrollment.@Version` | 락 밖 경로의 stale write · 동일 enrollment 동시 cancel race | → `OPTIMISTIC_LOCK_CONFLICT 409` |
| 3. 부분 UNIQUE | `uq_enrollments_active` | 동일 사용자 active 신청 중복 | → `DATA_INTEGRITY_VIOLATION 409` |
| 4. 멱등성 | `payment_intents.idempotency_key UNIQUE` + 리플레이 경로 본인 검증 | 결제 중복 호출 · 키 추측 시 정보 노출 | `PaymentConfirmService.confirm` |

1번이 정상 동작하면 2~3 은 발동할 일이 없으나, 락 범위 밖 경로·코드 버그를 DB 제약이 끝까지 막음.

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

`lectures.enrolled_count` 는 활성 신청 수 캐시. `Lecture` 비관 락 안에서 enrollment INSERT/상태변경과 같이 ±1, `@Version` 으로 stale write 차단. `ConcurrencyTest` 가 `enrolled_count == COUNT(active enrollments)` sanity check.

## 검증

- `./gradlew test --tests ConcurrencyTest` — Testcontainers 실 PG 위에서 3 시나리오: 정원 N + M명 동시 신청·같은 사용자 동시 중복 신청·같은 사용자 동시 cancel
- `k6 run load-test/enrollment-burst.k6.js` — 부하 상황에서 정원 정확성

## 확장

다중 인스턴스 스케일 아웃 시 분산 락 (Redis Redisson) 검토 가능. 본 과제 범위에선 DB row 락만으로도 정합성 보장.
