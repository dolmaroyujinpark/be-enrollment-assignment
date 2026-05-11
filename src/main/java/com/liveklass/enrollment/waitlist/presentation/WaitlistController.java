package com.liveklass.enrollment.waitlist.presentation;

import com.liveklass.enrollment.common.dto.PageResponse;
import com.liveklass.enrollment.waitlist.application.WaitlistService;
import com.liveklass.enrollment.waitlist.domain.WaitlistEntry;
import com.liveklass.enrollment.waitlist.presentation.dto.WaitlistResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/lectures/{lectureId}/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @PostMapping
    public ResponseEntity<WaitlistResponse> join(
        @PathVariable Long lectureId,
        @RequestHeader("X-User-Id") Long userId
    ) {
        WaitlistEntry entry = waitlistService.join(userId, lectureId);
        return ResponseEntity.status(HttpStatus.CREATED).body(WaitlistResponse.from(entry));
    }

    /** 대기열 조회 — 강의 작성 크리에이터만 (등록 순서). */
    @GetMapping
    public PageResponse<WaitlistResponse> list(
        @PathVariable Long lectureId,
        @RequestHeader("X-User-Id") Long userId,
        @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return PageResponse.from(waitlistService.findByLecture(userId, lectureId, pageable).map(WaitlistResponse::from));
    }
}
