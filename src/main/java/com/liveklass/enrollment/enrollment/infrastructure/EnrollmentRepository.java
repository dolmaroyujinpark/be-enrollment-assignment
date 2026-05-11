package com.liveklass.enrollment.enrollment.infrastructure;

import com.liveklass.enrollment.enrollment.domain.Enrollment;
import com.liveklass.enrollment.enrollment.domain.EnrollmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

    /** 동일 사용자가 동일 강의에 대해 active(=CANCELLED 가 아닌) 신청을 이미 가지고 있는지. (uq_enrollments_active 와 동일 의미) */
    boolean existsByUserIdAndLectureIdAndStatusNot(Long userId, Long lectureId, EnrollmentStatus status);

    Page<Enrollment> findByUserId(Long userId, Pageable pageable);
}
