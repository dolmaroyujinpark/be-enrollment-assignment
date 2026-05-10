package com.liveklass.enrollment.lecture.infrastructure;

import com.liveklass.enrollment.lecture.domain.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LectureRepository extends JpaRepository<Lecture, Long> {
}
