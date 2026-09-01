package com.ams.hrms.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import com.ams.hrms.validator.Validators;

/**
 * Pure leave business rules (spec sections 18 and 55): inclusive day
 * counting, calendar-overlap semantics, balance sufficiency and request
 * validation wording - no database, no UI.
 *
 * <p>The overlap predicate is the canonical definition of the SQL check in
 * {@code LeaveRepository.overlaps}: two ranges overlap when each starts at
 * or before the other ends (inclusive on both boundary days).</p>
 */
public final class LeaveRules {

    public static final int MAX_REQUEST_DAYS = 366;

    private LeaveRules() {
    }

    /** Inclusive day count between two ordered dates (start and end count). */
    public static long daysInclusive(LocalDate start, LocalDate end) {
        if (start == null || end == null || end.isBefore(start)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(start, end) + 1;
    }

    /**
     * True when the two date ranges share at least one day. Single-day
     * ranges only overlap themselves; touching ranges do not.
     */
    public static boolean overlaps(LocalDate aStart, LocalDate aEnd,
                                   LocalDate bStart, LocalDate bEnd) {
        if (aStart == null || aEnd == null || bStart == null || bEnd == null) {
            return false;
        }
        return !aStart.isAfter(bEnd) && !bStart.isAfter(aEnd);
    }

    /** Balance rule 4: requested days must fit the remaining availability. */
    public static boolean hasSufficientBalance(BigDecimal available, BigDecimal requestedDays) {
        if (available == null) {
            available = BigDecimal.ZERO;
        }
        if (requestedDays == null) {
            return true;
        }
        return requestedDays.compareTo(available) <= 0;
    }

    /**
     * Year-rollover rule: the days carried into a new year are the previous
     * year's unused remainder, capped by the leave type's
     * {@code max_carry_forward}. Never negative; a missing cap or remaining
     * value carries nothing.
     */
    public static BigDecimal carryForwardDays(BigDecimal previousRemaining,
                                              BigDecimal maxCarryForward) {
        BigDecimal remaining = previousRemaining == null
                ? BigDecimal.ZERO : previousRemaining.max(BigDecimal.ZERO);
        if (maxCarryForward == null || maxCarryForward.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return remaining.min(maxCarryForward);
    }

    /** User-facing explanation for an insufficient balance rejection. */
    public static String insufficientBalanceMessage(BigDecimal available,
                                                    BigDecimal requestedDays,
                                                    String leaveTypeName) {
        return "Insufficient balance: " + plain(available) + " day(s) available for "
                + Validators.normalize(leaveTypeName) + ", but "
                + plain(requestedDays) + " requested.";
    }

    /** Field validation for a request's reason and date range. */
    public static List<String> validateRequest(String reason,
                                               LocalDate start, LocalDate end) {
        List<String> errors = new ArrayList<>();
        Validators.required(errors, reason, "Reason");
        Validators.maxLength(errors, reason, 500, "Reason");

        if (start == null || end == null) {
            errors.add("Start and end dates are required.");
        } else {
            if (end.isBefore(start)) {
                errors.add("End date cannot be before the start date.");
            }
            if (daysInclusive(start, end) > MAX_REQUEST_DAYS) {
                errors.add("Leave cannot exceed " + MAX_REQUEST_DAYS + " days.");
            }
        }
        return errors;
    }

    private static String plain(BigDecimal value) {
        return value == null ? "0" : value.toPlainString();
    }
}
