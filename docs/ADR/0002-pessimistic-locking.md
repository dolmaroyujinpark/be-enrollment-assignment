# 0002. 정원 동시성에 비관 락 채택 (낙관 락 단독 대비)

- 상태: Accepted

## 맥락
마지막 자리에 여러 사람이 거의 동시에 신청할 때 정확히 정원 수만 성공시켜야 한다. `lectures.enrolled_count` 가 high-contention 핫스팟이고, 신청 처리는 "정원 검사 → `enrolled_count` +1 → enrollment INSERT" 가 한 덩어리로 원자적이어야 한다.

## 결정
신청·취소 시 `Lecture` row 에 **`PESSIMISTIC_WRITE`(`SELECT … FOR UPDATE`)** 락을 잡는다 — `LectureRepository.findByIdForUpdate`. 그 락 안에서 OPEN 여부·중복 여부·정원을 검사하고 카운터를 갱신한다. `@Version` 낙관 락은 **보조** 로 둔다(비관 락을 우회한 경로의 stale write 차단).

## 근거
- 정원 자리는 경합이 몰리는 핫스팟이다. **낙관 락 단독** 이면 동시 N명 중 1명만 커밋에 성공하고 나머지 N−1명은 `ObjectOptimisticLockingFailureException` → 재시도 루프를 직접 구현해야 한다. 코드가 복잡해지고, 경합이 심하면 재시도가 누적돼 tail latency 가 나빠진다. 비관 락은 차례로 직렬화하므로 재시도 없이 깔끔하다.
- **"검사 시점 = 커밋 시점"** 이 보장된다 → 락을 잡은 뒤 OPEN/중복/정원을 검사하면 그 상태가 곧 커밋되는 상태라, 분기 로직이 단순하다.
- **단일 row 단위 락** 이라 락 순환(데드락) 여지가 없다. (대기열 승급도 그 안에서 `Lecture → WaitlistEntry` 순으로만 잡는다.)
- `@Version` 을 함께 둬서, 비관 락 범위 밖의 경로(마이그레이션, 어드민 직접 SQL, 코드 버그)가 `enrolled_count` 를 덮어쓰면 커밋 시 충돌로 잡힌다 — defense in depth(`OPTIMISTIC_LOCK_CONFLICT` 409).

## 결과 (트레이드오프)
- (+) 재시도 없이 정확하다. 신청/취소 로직이 단순하다(검사 = 커밋).
- (−) **같은 강의** 에 대한 신청이 직렬화되므로 단일 강의 throughput 에 상한이 있다. 단, 락 단위가 강의(row)라 시스템 전체로는 강의 수만큼 병렬이고, 한 신청 트랜잭션이 짧아(락 안에서 무거운 작업 없음) 실무 부하에서 문제가 되지 않는다. (강의 하나에 수만 명이 동시에 몰리는 극단 상황이면 대기열·샤딩·캐시드 카운터 등을 추가 검토.)

## 검토한 대안
- **낙관 락 단독(`@Version` only)** — 핫스팟에 부적합. 재시도 루프 필요.
- **`enrolled_count` 없이 매번 `COUNT(*)` + SERIALIZABLE 격리** — 직렬화 실패 재시도가 잦고, 목록 조회마다 COUNT 비용.
- **애플리케이션 레벨 락(`ReentrantLock` 등)** — 단일 인스턴스 한정 → 다중 인스턴스에서 깨짐(이전 유사 과제의 한계 그대로). 이번 과제가 그 한계를 개선하려는 것이므로 채택 안 함.
- **DB 원자적 조건부 UPDATE** (`UPDATE lectures SET enrolled_count = enrolled_count + 1 WHERE id = ? AND enrolled_count < capacity`) — 카운터만 보면 깔끔하지만, enrollment INSERT·중복 신청 검사와 한 트랜잭션으로 묶어야 하므로 결국 트랜잭션 경계가 필요하고, 비관 락 쪽이 흐름이 더 명확해 채택 안 함.
