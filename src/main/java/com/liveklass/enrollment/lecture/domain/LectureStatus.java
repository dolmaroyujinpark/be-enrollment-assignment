package com.liveklass.enrollment.lecture.domain;

public enum LectureStatus {
    DRAFT,
    OPEN,
    CLOSED;

    public boolean canTransitionTo(LectureStatus target) {
        return switch (this) {
            case DRAFT -> target == OPEN;
            case OPEN -> target == CLOSED;
            case CLOSED -> false;
        };
    }

    public boolean isOpenForEnrollment() {
        return this == OPEN;
    }
}
