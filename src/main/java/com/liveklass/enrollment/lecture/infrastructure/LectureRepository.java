package com.liveklass.enrollment.lecture.infrastructure;

import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.domain.LectureStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LectureRepository extends JpaRepository<Lecture, Long> {

    List<Lecture> findAllByStatusOrderByIdDesc(LectureStatus status);

    List<Lecture> findAllByOrderByIdDesc();

    /**
     * 신청/취소 시 정원 카운터 갱신을 직렬화하기 위한 row-level 비관 락 (SELECT ... FOR UPDATE).
     * 같은 강의의 마지막 자리에 동시에 여러 요청이 와도 한 번에 하나씩만 통과한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lecture l where l.id = :id")
    Optional<Lecture> findByIdForUpdate(@Param("id") Long id);
}
