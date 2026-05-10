package com.liveklass.enrollment.lecture.presentation;

import com.liveklass.enrollment.lecture.application.LectureService;
import com.liveklass.enrollment.lecture.domain.Lecture;
import com.liveklass.enrollment.lecture.domain.LectureStatus;
import com.liveklass.enrollment.lecture.presentation.dto.CreateLectureRequest;
import com.liveklass.enrollment.lecture.presentation.dto.LectureResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/lectures")
@RequiredArgsConstructor
public class LectureController {

    private final LectureService lectureService;

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
    public List<LectureResponse> list(
        @RequestParam(name = "status", required = false) LectureStatus status
    ) {
        return lectureService.findAll(status).stream()
            .map(LectureResponse::from)
            .toList();
    }

    @GetMapping("/{id}")
    public LectureResponse detail(@PathVariable Long id) {
        return LectureResponse.from(lectureService.findById(id));
    }
}
