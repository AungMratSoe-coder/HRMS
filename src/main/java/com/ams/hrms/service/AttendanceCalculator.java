package com.ams.hrms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

/**
 * Pure attendance math (spec section 16) - no database, no UI, fully
 * unit-testable.
 *
 * <p>Model: all offsets are expressed as RAW minutes relative to the
 * check-in (may be negative for an early arrival). The scheduled duration
 * stays positive by rolling overnight shifts across midnight.</p>
 *
 * <p>Semantics:</p>
 * <ul>
 *   <li>late = minutes past (scheduled start + grace); arriving before the
 *       start is never late</li>
 *   <li>early = gap between check-out and the scheduled end</li>
 *   <li>overtime = check-out beyond the scheduled end</li>
 *   <li>worked = check-out − check-in − break (never negative)</li>
 *   <li>status precedence: HALF_DAY (worked &lt; half schedule) → LATE →
 *       EARLY_LEAVE → PRESENT</li>
 * </ul>
 */
public final class AttendanceCalculator {

    /** Immutable shift values needed by the calculator. */
    public record Snapshot(LocalTime startTime, LocalTime endTime,
                           int graceMinutes, int breakMinutes) {
    }

    /** Computed values stored on one attendance row. */
    public record Result(String status, int lateMinutes, int earlyLeaveMinutes,
                         BigDecimal workedHours, BigDecimal overtimeHours) {
    }

    private static final int DAY_MINUTES = 24 * 60;

    private AttendanceCalculator() {
    }

    /**
     * Evaluates one day of attendance.
     *
     * @param shift    effective shift snapshot; null = unscheduled, late/early/
     *                 overtime stay zero
     * @param checkIn  punch-in; required
     * @param checkOut punch-out; null while still working
     */
    public static Result evaluate(Snapshot shift, LocalTime checkIn, LocalTime checkOut) {
        if (checkIn == null) {
            throw new IllegalArgumentException("checkIn is required");
        }
        int inMinutes = minutes(checkIn);

        if (shift == null) {
            if (checkOut == null) {
                return new Result("PRESENT", 0, 0, BigDecimal.ZERO, BigDecimal.ZERO);
            }
            return new Result("PRESENT", 0, 0,
                    scale(workedRawHours(inMinutes, minutes(checkOut), 0)), BigDecimal.ZERO);
        }

        int startShift = minutes(shift.startTime());
        int endShift = minutes(shift.endTime());

        // Raw offsets relative to check-in (start may be negative = early arrival).
        int rawStart = startShift - inMinutes;
        int rawEnd = endShift - inMinutes;
        int scheduledDuration = rawEnd - rawStart;
        while (scheduledDuration <= 0) {
            scheduledDuration += DAY_MINUTES; // overnight shift rolls past midnight
        }
        int endFromIn = rawStart + scheduledDuration;

        // rawStart < 0 means the check-in happened AFTER the scheduled start
        // (start sits before the check-in on the timeline). Late minutes run
        // from the scheduled start once past the grace window.
        int lateRaw = -rawStart;
        int lateMinutes = lateRaw > shift.graceMinutes() ? lateRaw : 0;

        if (checkOut == null) {
            String openStatus = lateMinutes > 0 ? "LATE" : "PRESENT";
            return new Result(openStatus, lateMinutes, 0, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        double workedRaw = workedRawHours(inMinutes, minutes(checkOut), shift.breakMinutes());

        int outFromIn = minutes(checkOut) - inMinutes;
        if (outFromIn <= 0) {
            outFromIn += DAY_MINUTES; // punch-out crossed midnight
        }

        int earlyMinutes = Math.max(0, endFromIn - outFromIn);
        double overtimeRaw = Math.max(0, outFromIn - endFromIn) / 60.0;

        double scheduledHours = Math.max(0,
                scheduledDuration / 60.0 - shift.breakMinutes() / 60.0);
        String status;
        if (scheduledHours > 0 && workedRaw < scheduledHours / 2.0) {
            status = "HALF_DAY";
        } else if (lateMinutes > 0) {
            status = "LATE";
        } else if (earlyMinutes > 0) {
            status = "EARLY_LEAVE";
        } else {
            status = "PRESENT";
        }

        return new Result(status, lateMinutes, earlyMinutes,
                scale(workedRaw), scale(overtimeRaw));
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private static int minutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    /** Check-out − check-in minus break, crossing midnight when needed. */
    private static double workedRawHours(int inMinutes, int outMinutes, int breakMinutes) {
        int delta = outMinutes - inMinutes;
        if (delta <= 0) {
            delta += DAY_MINUTES;
        }
        if (delta == 0 && inMinutes == outMinutes) {
            return 0;
        }
        return Math.max(0, (delta - breakMinutes) / 60.0);
    }

    private static BigDecimal scale(double hours) {
        return BigDecimal.valueOf(hours).setScale(2, RoundingMode.HALF_UP);
    }
}
