package com.liveklass.enrollment.lecture.presentation;

import com.liveklass.enrollment.common.dto.PageResponse;
import com.liveklass.enrollment.enrollment.application.EnrollmentService;
import com.liveklass.enrollment.enrollment.presentation.dto.EnrollmentResponse;
import com.liveklass.enrollment.lecture.application.LectureService;
import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.domain.LectureStatus;
import com.liveklass.enrollment.lecture.presentation.dto.CreateLectureRequest;
import com.liveklass.enrollment.lecture.presentation.dto.LectureResponse;
import com.liveklass.enrollment.lecture.presentation.dto.UpdateLectureStatusRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/lectures")
@RequiredArgsConstructor
public class LectureController {

    private final LectureService lectureService;
    private final EnrollmentService enrollmentService;

    @PostMapping
    public ResponseEntity<LectureResponse> create(
        @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody CreateLectureRequest request
    ) {
        Lecture lecture = lectureService.register(userId, request);
        return ResponseEntity
            .created(URI.create("/api/lectures/" + lecture.getId()))
            .body(LectureResponse.from(lecture));
    }

    @GetMapping
    public PageResponse<LectureResponse> list(
        @RequestParam(name = "status", required = false) LectureStatus status,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PageResponse.from(lectureService.findAll(status, pageable).map(LectureResponse::from));
    }

    @GetMapping("/{id}")
    public LectureResponse detail(@PathVariable Long id) {
        return LectureResponse.from(lectureService.findById(id));
    }

    @PatchMapping("/{id}/status")
    public LectureResponse changeStatus(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody UpdateLectureStatusRequest request
    ) {
        Lecture lecture = lectureService.changeStatus(id, request.status(), userId);
        return LectureResponse.from(lecture);
    }

    /** 강의별 수강생 목록 — 강의 작성 크리에이터만 조회 가능 (O3). */
    @GetMapping("/{id}/enrollments")
    public PageResponse<EnrollmentResponse> listEnrollments(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return PageResponse.from(enrollmentService.findByLecture(userId, id, pageable).map(EnrollmentResponse::from));
    }
}
