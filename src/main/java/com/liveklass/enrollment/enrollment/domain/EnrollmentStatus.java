package com.liveklass.enrollment.enrollment.domain;

public enum EnrollmentStatus {
    PENDING,
    CONFIRMED,
    CANCELLED;

    public boolean canTransitionTo(EnrollmentStatus target) {
        return switch (this) {
            case PENDING -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == CANCELLED;
            case CANCELLED -> false;
        };
    }

    public boolean isActive() {
        return this != CANCELLED;
    }
}
