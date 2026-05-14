-- [Bug #1 fix] 같은 사용자가 동시에 같은 enrollment 를 cancel 두 번 보낼 때
-- 두 트랜잭션이 PENDING 상태의 enrollment 를 동시에 로드한 뒤 둘 다 cancel + decrementEnrolled 를
-- 호출해 enrolled_count 가 실제 active 신청 수보다 적게 깎이는 race 를 막기 위한 낙관 락 컬럼.
-- Lecture 와 동일한 패턴(@Version) 으로 stale write 를 OptimisticLockingFailure 로 잡는다.
ALTER TABLE enrollments
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
