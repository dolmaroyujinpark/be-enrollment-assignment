package com.liveklass.enrollment.waitlist.presentation;

import com.liveklass.enrollment.common.dto.PageResponse;
import com.liveklass.enrollment.waitlist.application.WaitlistService;
import com.liveklass.enrollment.waitlist.domain.WaitlistEntry;
import com.liveklass.enrollment.waitlist.presentation.dto.WaitlistResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "대기열", description = "강의 대기열 등록·조회")
@RestController
@RequestMapping("/api/lectures/{lectureId}/waitlist")
@RequiredArgsConstructor
public class WaitlistController {

    private final WaitlistService waitlistService;

    @Operation(summary = "대기열 등록",
        description = "OPEN 이면서 정원이 모두 찬 강의의 대기열에 등록한다. 자리가 남아 있으면 거부 (`WAITLIST_NOT_NEEDED`) — 바로 수강 신청 권장. 이미 active 신청이 있거나 이미 대기 중이면 거부. (헤더 X-User-Id 필요)")
    @ApiResponse(responseCode = "201", description = "대기열 등록 성공")
    @PostMapping
    public ResponseEntity<WaitlistResponse> join(
        @PathVariable Long lectureId,
        @RequestHeader("X-User-Id") Long userId
    ) {
        WaitlistEntry entry = waitlistService.join(userId, lectureId);
        return ResponseEntity.status(HttpStatus.CREATED).body(WaitlistResponse.from(entry));
    }

    @Operation(summary = "대기열 조회 (크리에이터 전용)", description = "해당 강의의 작성 크리에이터만 조회 가능. 등록 순서(FIFO). (헤더 X-User-Id 필요)")
    @GetMapping
    public PageResponse<WaitlistResponse> list(
        @PathVariable Long lectureId,
        @RequestHeader("X-User-Id") Long userId,
        @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable
    ) {
        return PageResponse.from(waitlistService.findByLecture(userId, lectureId, pageable).map(WaitlistResponse::from));
    }
}
