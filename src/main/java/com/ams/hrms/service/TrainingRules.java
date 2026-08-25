package com.ams.hrms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Training business rules (spec sections 23 and 55): session duration math,
 * roster capacity checks and status transitions - pure logic, unit-testable
 * without UI or database.
 */
public final class TrainingRules {

    public static final Set<String> PROGRAM_STATUSES =
            Set.of("PLANNED", "ONGOING", "COMPLETED", "CANCELLED");
    public static final Set<String> SESSION_STATUSES =
            Set.of("SCHEDULED", "ONGOING", "COMPLETED", "CANCELLED");
    public static final Set<String> ENROLLMENT_RESULTS =
            Set.of("ENROLLED", "ATTENDED", "COMPLETED", "PASSED", "FAILED", "NO_SHOW");

    private static final Set<String> LIVE_PROGRAM_STATUSES = Set.of("PLANNED", "ONGOING");
    private static final Set<String> LIVE_SESSION_STATUSES = Set.of("SCHEDULED", "ONGOING");

    private TrainingRules() {
    }

    /** Hours between start and end, rounded half-up to two decimals. */
    public static BigDecimal durationHours(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null || end.isBefore(start)) {
            return null;
        }
        long seconds = Duration.between(start, end).getSeconds();
        return BigDecimal.valueOf(seconds)
                .divide(BigDecimal.valueOf(3600), 2, RoundingMode.HALF_UP);
    }

    /**
     * Capacity rule: null capacity means unlimited; enrollment is legal while
     * the current count sits below the cap.
     */
    public static boolean hasRoom(Integer capacity, long currentlyEnrolled) {
        return capacity == null || currentlyEnrolled < capacity;
    }

    /** Legal program status transitions. */
    public static boolean canTransitionProgram(String from, String to) {
        if (from == null || !PROGRAM_STATUSES.contains(from)) {
            return false;
        }
        return switch (from) {
            case "PLANNED" -> to.equals("ONGOING") || to.equals("CANCELLED")
                    || to.equals("COMPLETED");
            case "ONGOING" -> to.equals("COMPLETED") || to.equals("CANCELLED");
            default -> false;
        };
    }

    /** Legal session status transitions. */
    public static boolean canTransitionSession(String from, String to) {
        if (from == null || !SESSION_STATUSES.contains(from)) {
            return false;
        }
        return switch (from) {
            case "SCHEDULED" -> to.equals("ONGOING") || to.equals("COMPLETED")
                    || to.equals("CANCELLED");
            case "ONGOING" -> to.equals("COMPLETED") || to.equals("CANCELLED");
            default -> false;
        };
    }

    /** Enrollment is only possible on a live program. */
    public static boolean programAcceptsEnrollment(String status) {
        return LIVE_PROGRAM_STATUSES.contains(status);
    }

    /** Only live sessions may be referenced by an enrollment. */
    public static boolean sessionAcceptsReference(String status) {
        return LIVE_SESSION_STATUSES.contains(status);
    }

    /** A score must sit inside 0..100 with at most two decimals. */
    public static boolean isValidScore(BigDecimal score) {
        if (score == null) {
            return true;
        }
        if (score.scale() > 2 || score.signum() < 0
                || score.compareTo(BigDecimal.valueOf(100)) > 0) {
            return false;
        }
        return true;
    }
}
