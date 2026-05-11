package com.liveklass.enrollment.lecture.application;

import com.liveklass.enrollment.common.exception.BusinessException;
import com.liveklass.enrollment.common.exception.ErrorCode;
import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.domain.LectureStatus;
import com.liveklass.enrollment.lecture.infrastructure.LectureRepository;
import com.liveklass.enrollment.lecture.presentation.dto.CreateLectureRequest;
import com.liveklass.enrollment.user.domain.User;
import com.liveklass.enrollment.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LectureService {

    private final LectureRepository lectureRepository;
    private final UserRepository userRepository;

    @Transactional
    public Lecture register(Long creatorId, CreateLectureRequest request) {
        User creator = userRepository.findById(creatorId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "존재하지 않는 사용자: " + creatorId));
        if (!creator.isCreator()) {
            throw new BusinessException(ErrorCode.NOT_CREATOR);
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
    public Page<Lecture> findAll(LectureStatus statusFilter, Pageable pageable) {
        if (statusFilter == null) {
            return lectureRepository.findAll(pageable);
        }
        return lectureRepository.findByStatus(statusFilter, pageable);
    }

    @Transactional(readOnly = true)
    public Lecture findById(Long id) {
        return lectureRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND, "존재하지 않는 강의: " + id));
    }

    @Transactional
    public Lecture changeStatus(Long lectureId, LectureStatus target, Long requesterId) {
        Lecture lecture = lectureRepository.findById(lectureId)
            .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND, "존재하지 않는 강의: " + lectureId));
        if (!lecture.getCreatorId().equals(requesterId)) {
            throw new BusinessException(ErrorCode.NOT_LECTURE_OWNER);
        }
        try {
            lecture.changeStatus(target);
        } catch (IllegalStateException e) {
            throw new BusinessException(ErrorCode.INVALID_LECTURE_STATUS_TRANSITION, e.getMessage());
        }
        return lecture;
    }
}
