package com.liveklass.enrollment.waitlist.infrastructure;

import com.liveklass.enrollment.waitlist.domain.WaitlistEntry;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    boolean existsByUserIdAndLectureId(Long userId, Long lectureId);

    Page<WaitlistEntry> findByLectureId(Long lectureId, Pageable pageable);

    void deleteByUserIdAndLectureId(Long userId, Long lectureId);

    /**
     * 대기열의 가장 오래된(FIFO) 항목 1건을 row-level 락과 함께 가져온다 (SELECT ... FOR UPDATE SKIP LOCKED).
     * 다른 트랜잭션이 이미 head 를 잠갔으면 건너뛰고 다음 항목을 본다 — 다중 인스턴스에서도 한 명만 안전하게 승급.
     * (jakarta.persistence.lock.timeout = -2 → Hibernate 의 SKIP_LOCKED)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select w from WaitlistEntry w where w.lectureId = :lectureId order by w.createdAt asc, w.id asc limit 1")
    Optional<WaitlistEntry> findNextInQueueForUpdate(@Param("lectureId") Long lectureId);
}
