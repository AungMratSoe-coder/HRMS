package com.ams.hrms.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Separation business rules (spec sections 26 and 55): resignation status
 * transitions, notice-period math and termination categories - pure logic,
 * unit-testable without UI or database.
 */
public final class SeparationRules {

    public static final String EMPLOYEE_RESIGNED = "RESIGNED";
    public static final String EMPLOYEE_TERMINATED = "TERMINATED";

    public static final Set<String> RESIGNATION_STATUSES =
            Set.of("SUBMITTED", "APPROVED", "REJECTED", "WITHDRAWN", "PROCESSED");

    public static final Set<String> TERMINATION_CATEGORIES =
            Set.of("MISCONDUCT", "PERFORMANCE", "LAYOFF", "CONTRACT_END", "OTHER");

    private SeparationRules() {
    }

    /**
     * Legal resignation transitions:
     * SUBMITTED -&gt; APPROVED / REJECTED / WITHDRAWN; APPROVED -&gt; PROCESSED.
     */
    public static boolean canTransitionResignation(String from, String to) {
        if (from == null || to == null || !RESIGNATION_STATUSES.contains(from)) {
            return false;
        }
        return switch (from) {
            case "SUBMITTED" -> to.equals("APPROVED") || to.equals("REJECTED")
                    || to.equals("WITHDRAWN");
            case "APPROVED" -> to.equals("PROCESSED");
            default -> false;
        };
    }

    /**
     * Notice days between the resignation and the last working day; -1 when
     * the order is invalid (schema CHECK also guards this).
     */
    public static long noticeDays(LocalDate resignationDate, LocalDate lastWorkingDate) {
        if (resignationDate == null || lastWorkingDate == null
                || lastWorkingDate.isBefore(resignationDate)) {
            return -1;
        }
        return ChronoUnit.DAYS.between(resignationDate, lastWorkingDate);
    }

    /** Valid termination reason categories (mirrors schema CHECK). */
    public static boolean isValidCategory(String category) {
        return category != null && TERMINATION_CATEGORIES.contains(category);
    }
}
