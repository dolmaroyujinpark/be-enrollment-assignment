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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "강의", description = "강의 개설·조회·상태 전이")
@RestController
@RequestMapping("/api/lectures")
@RequiredArgsConstructor
public class LectureController {

    private final LectureService lectureService;
    private final EnrollmentService enrollmentService;

    @Operation(summary = "강의 등록", description = "CREATOR 역할 사용자가 강의를 개설한다. 초기 상태는 DRAFT. (헤더 X-User-Id 필요)")
    @ApiResponse(responseCode = "201", description = "강의 생성 성공 (Location 헤더에 새 리소스 경로)")
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

    @Operation(summary = "강의 목록 조회", description = "status 필터(DRAFT/OPEN/CLOSED)와 페이지네이션(page/size)을 지원한다.")
    @GetMapping
    public PageResponse<LectureResponse> list(
        @RequestParam(name = "status", required = false) LectureStatus status,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return PageResponse.from(lectureService.findAll(status, pageable).map(LectureResponse::from));
    }

    @Operation(summary = "강의 상세 조회", description = "현재 신청 인원(enrolledCount)과 남은 자리(availableSeats)를 포함한다.")
    @GetMapping("/{id}")
    public LectureResponse detail(@PathVariable Long id) {
        return LectureResponse.from(lectureService.findById(id));
    }

    @Operation(summary = "강의 상태 전이", description = "DRAFT→OPEN→CLOSED 단방향 전이만 허용. 강의 작성 크리에이터만 가능. (헤더 X-User-Id 필요)")
    @PatchMapping("/{id}/status")
    public LectureResponse changeStatus(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody UpdateLectureStatusRequest request
    ) {
        Lecture lecture = lectureService.changeStatus(id, request.status(), userId);
        return LectureResponse.from(lecture);
    }

    @Operation(summary = "강의별 수강생 목록 (크리에이터 전용)", description = "해당 강의의 작성 크리에이터만 조회 가능. 신청 순서(id asc)·페이지네이션. (헤더 X-User-Id 필요)")
    @GetMapping("/{id}/enrollments")
    public PageResponse<EnrollmentResponse> listEnrollments(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId,
        @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return PageResponse.from(enrollmentService.findByLecture(userId, id, pageable).map(EnrollmentResponse::from));
    }
}
