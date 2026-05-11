package com.liveklass.enrollment.waitlist.presentation.dto;

import com.liveklass.enrollment.waitlist.domain.WaitlistEntry;

import java.time.Instant;

public record WaitlistResponse(
    Long id,
    Long userId,
    Long lectureId,
    Instant createdAt
) {
    public static WaitlistResponse from(WaitlistEntry entry) {
        return new WaitlistResponse(entry.getId(), entry.getUserId(), entry.getLectureId(), entry.getCreatedAt());
    }
}
