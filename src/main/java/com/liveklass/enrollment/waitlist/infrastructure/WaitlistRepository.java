package com.liveklass.enrollment.waitlist.infrastructure;

import com.liveklass.enrollment.waitlist.domain.WaitlistEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, Long> {

    boolean existsByUserIdAndLectureId(Long userId, Long lectureId);

    Page<WaitlistEntry> findByLectureId(Long lectureId, Pageable pageable);
}
