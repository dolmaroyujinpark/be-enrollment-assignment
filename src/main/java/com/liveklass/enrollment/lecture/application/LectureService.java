package com.liveklass.enrollment.lecture.application;

import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.domain.LectureStatus;
import com.liveklass.enrollment.lecture.infrastructure.LectureRepository;
import com.liveklass.enrollment.lecture.presentation.dto.CreateLectureRequest;
import com.liveklass.enrollment.user.domain.User;
import com.liveklass.enrollment.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class LectureService {

    private final LectureRepository lectureRepository;
    private final UserRepository userRepository;

    @Transactional
    public Lecture register(Long creatorId, CreateLectureRequest request) {
        User creator = userRepository.findById(creatorId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자: " + creatorId));
        if (!creator.isCreator()) {
            throw new IllegalStateException("강의 등록 권한이 없습니다 (CREATOR 역할 필요)");
        }
        Lecture lecture = new Lecture(
            creator.getId(),
            request.title(),
            request.description(),
            request.price(),
            request.capacity(),
            request.startDate(),
            request.endDate()
        );
        return lectureRepository.save(lecture);
    }

    @Transactional(readOnly = true)
    public List<Lecture> findAll(LectureStatus statusFilter) {
        if (statusFilter == null) {
            return lectureRepository.findAllByOrderByIdDesc();
        }
        return lectureRepository.findAllByStatusOrderByIdDesc(statusFilter);
    }

    @Transactional(readOnly = true)
    public Lecture findById(Long id) {
        return lectureRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 강의: " + id));
    }

    @Transactional
    public Lecture changeStatus(Long lectureId, LectureStatus target, Long requesterId) {
        Lecture lecture = lectureRepository.findById(lectureId)
            .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 강의: " + lectureId));
        if (!lecture.getCreatorId().equals(requesterId)) {
            throw new IllegalStateException("강의 작성자만 상태를 변경할 수 있습니다");
        }
        lecture.changeStatus(target);
        return lecture;
    }
}
