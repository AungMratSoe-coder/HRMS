package com.ams.hrms.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic tests for attendance computation (spec section 55).
 */
class AttendanceCalculatorTest {

    // Standard day shift: 09:00-17:00, grace 15, break 60.
    private final AttendanceCalculator.Snapshot day =
            new AttendanceCalculator.Snapshot(LocalTime.of(9, 0), LocalTime.of(17, 0), 15, 60);

    // Night shift crossing midnight: 23:00-07:00, grace 10, break 30.
    private final AttendanceCalculator.Snapshot night =
            new AttendanceCalculator.Snapshot(LocalTime.of(23, 0), LocalTime.of(7, 0), 10, 30);

    @Test
    @DisplayName("on-time arrival and on-time departure = PRESENT")
    void present() {
        var result = AttendanceCalculator.evaluate(day,
                LocalTime.of(9, 0), LocalTime.of(17, 0));
        assertThat(result.status()).isEqualTo("PRESENT");
        assertThat(result.lateMinutes()).isZero();
        assertThat(result.earlyLeaveMinutes()).isZero();
        assertThat(result.workedHours()).isEqualByComparingTo("7.00"); // 8h - 1h break
    }

    @Test
    @DisplayName("check-in within the grace window is not late")
    void graceWindow() {
        var result = AttendanceCalculator.evaluate(day,
                LocalTime.of(9, 14), LocalTime.of(17, 0));
        assertThat(result.status()).isEqualTo("PRESENT");
        assertThat(result.lateMinutes()).isZero();
    }

    @Test
    @DisplayName("late counts full minutes from start once past grace")
    void lateMinutesFromStart() {
        var result = AttendanceCalculator.evaluate(day,
                LocalTime.of(10, 0), LocalTime.of(18, 0));
        assertThat(result.status()).isEqualTo("LATE");
        assertThat(result.lateMinutes()).isEqualTo(60);
        assertThat(result.earlyLeaveMinutes()).isZero();
        assertThat(result.overtimeHours()).isEqualByComparingTo("1.00");
    }

    @Test
    @DisplayName("early leave detected with minute precision")
    void earlyLeave() {
        var result = AttendanceCalculator.evaluate(day,
                LocalTime.of(9, 5), LocalTime.of(15, 30));
        assertThat(result.status()).isEqualTo("EARLY_LEAVE");
        assertThat(result.earlyLeaveMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("worked less than half the schedule = HALF_DAY")
    void halfDay() {
        var result = AttendanceCalculator.evaluate(day,
                LocalTime.of(9, 5), LocalTime.of(12, 30));
        assertThat(result.status()).isEqualTo("HALF_DAY");
    }

    @Test
    @DisplayName("early arrival is never late")
    void earlyArrival() {
        var result = AttendanceCalculator.evaluate(day,
                LocalTime.of(8, 40), LocalTime.of(17, 5));
        assertThat(result.lateMinutes()).isZero();
        assertThat(result.status()).isEqualTo("PRESENT");
    }

    @Test
    @DisplayName("overnight shift: early evening arrival works into the morning")
    void overnightNightShift() {
        var result = AttendanceCalculator.evaluate(night,
                LocalTime.of(22, 50), LocalTime.of(7, 0));
        assertThat(result.lateMinutes()).isZero(); // arrived before start
        assertThat(result.status()).isEqualTo("PRESENT");
        // worked = 22:50 -> 07:00 (8h10m) - 30m break = 7.67h
        assertThat(result.workedHours()).isEqualByComparingTo("7.67");
        assertThat(result.overtimeHours()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("night shift overtime past midnight with late arrival")
    void overnightOvertime() {
        var result = AttendanceCalculator.evaluate(night,
                LocalTime.of(23, 20), LocalTime.of(9, 0));
        assertThat(result.lateMinutes()).isEqualTo(20);
        assertThat(result.status()).isEqualTo("LATE");
        assertThat(result.overtimeHours()).isEqualByComparingTo("2.00"); // out 09:00 vs end 07:00
    }

    @Test
    @DisplayName("open checkout keeps provisional status with zero hours")
    void openCheckout() {
        var late = AttendanceCalculator.evaluate(day, LocalTime.of(10, 5), null);
        assertThat(late.status()).isEqualTo("LATE");
        assertThat(late.lateMinutes()).isEqualTo(65);
        assertThat(late.workedHours()).isEqualByComparingTo("0.00");

        var present = AttendanceCalculator.evaluate(day, LocalTime.of(8, 59), null);
        assertThat(present.status()).isEqualTo("PRESENT");
    }
}
