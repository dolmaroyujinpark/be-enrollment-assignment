package com.liveklass.enrollment.lecture.infrastructure;

import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.domain.LectureStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LectureRepository extends JpaRepository<Lecture, Long> {

    List<Lecture> findAllByStatusOrderByIdDesc(LectureStatus status);

    List<Lecture> findAllByOrderByIdDesc();
}
