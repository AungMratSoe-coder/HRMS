package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * Training rules (spec sections 23 and 55): duration math, capacity checks
 * and status transitions - pure logic, verified without UI or database.
 */
class TrainingRulesTest {

    // ------------------------------------------------------------------
    // Duration
    // ------------------------------------------------------------------

    @Test
    void wholeHourDuration() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 10, 9, 0);
        LocalDateTime end = LocalDateTime.of(2026, 8, 10, 17, 0);
        assertThat(TrainingRules.durationHours(start, end))
                .isEqualByComparingTo("8.00");
    }

    @Test
    void partialHourDurationRoundsHalfUp() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 10, 9, 0);
        // 90 minutes = 1.5 hours; 45 minutes = 0.75 hours.
        assertThat(TrainingRules.durationHours(start, start.plusMinutes(90)))
                .isEqualByComparingTo("1.50");
        assertThat(TrainingRules.durationHours(start, start.plusMinutes(45)))
                .isEqualByComparingTo("0.75");
    }

    @Test
    void invalidRangesReturnNull() {
        LocalDateTime base = LocalDateTime.of(2026, 8, 10, 9, 0);
        assertThat(TrainingRules.durationHours(base, null)).isNull();
        assertThat(TrainingRules.durationHours(null, base)).isNull();
        assertThat(TrainingRules.durationHours(base, base)).isNotNull();
    }

    // ------------------------------------------------------------------
    // Capacity
    // ------------------------------------------------------------------

    @Test
    void nullCapacityMeansUnlimited() {
        assertThat(TrainingRules.hasRoom(null, 500)).isTrue();
    }

    @Test
    void roomAvailableOnlyBelowCapacity() {
        assertThat(TrainingRules.hasRoom(20, 19)).isTrue();
        assertThat(TrainingRules.hasRoom(20, 20)).isFalse();
        assertThat(TrainingRules.hasRoom(20, 25)).isFalse();
    }

    // ------------------------------------------------------------------
    // Status transitions
    // ------------------------------------------------------------------

    @Test
    void programLifecycleForwardOnly() {
        assertThat(TrainingRules.canTransitionProgram("PLANNED", "ONGOING")).isTrue();
        assertThat(TrainingRules.canTransitionProgram("PLANNED", "COMPLETED")).isTrue();
        assertThat(TrainingRules.canTransitionProgram("PLANNED", "CANCELLED")).isTrue();
        assertThat(TrainingRules.canTransitionProgram("ONGOING", "COMPLETED")).isTrue();

        assertThat(TrainingRules.canTransitionProgram("COMPLETED", "PLANNED")).isFalse();
        assertThat(TrainingRules.canTransitionProgram("CANCELLED", "ONGOING")).isFalse();
        assertThat(TrainingRules.canTransitionProgram("COMPLETED", "CANCELLED")).isFalse();
        assertThat(TrainingRules.canTransitionProgram("UNKNOWN", "ONGOING")).isFalse();
    }

    @Test
    void sessionLifecycleForwardOnly() {
        assertThat(TrainingRules.canTransitionSession("SCHEDULED", "ONGOING")).isTrue();
        assertThat(TrainingRules.canTransitionSession("SCHEDULED", "CANCELLED")).isTrue();
        assertThat(TrainingRules.canTransitionSession("ONGOING", "COMPLETED")).isTrue();

        assertThat(TrainingRules.canTransitionSession("COMPLETED", "SCHEDULED")).isFalse();
        assertThat(TrainingRules.canTransitionSession("CANCELLED", "ONGOING")).isFalse();
    }

    // ------------------------------------------------------------------
    // Enrollment gates
    // ------------------------------------------------------------------

    @Test
    void onlyLiveProgramsAcceptEnrollment() {
        assertThat(TrainingRules.programAcceptsEnrollment("PLANNED")).isTrue();
        assertThat(TrainingRules.programAcceptsEnrollment("ONGOING")).isTrue();
        assertThat(TrainingRules.programAcceptsEnrollment("COMPLETED")).isFalse();
        assertThat(TrainingRules.programAcceptsEnrollment("CANCELLED")).isFalse();
    }

    @Test
    void onlyLiveSessionsCanBeReferenced() {
        assertThat(TrainingRules.sessionAcceptsReference("SCHEDULED")).isTrue();
        assertThat(TrainingRules.sessionAcceptsReference("ONGOING")).isTrue();
        assertThat(TrainingRules.sessionAcceptsReference("COMPLETED")).isFalse();
    }

    // ------------------------------------------------------------------
    // Scores
    // ------------------------------------------------------------------

    @Test
    void scoreValidationBoundsAndScale() {
        assertThat(TrainingRules.isValidScore(null)).isTrue();
        assertThat(TrainingRules.isValidScore(BigDecimal.ZERO)).isTrue();
        assertThat(TrainingRules.isValidScore(new BigDecimal("99.99"))).isTrue();
        assertThat(TrainingRules.isValidScore(new BigDecimal("-1"))).isFalse();
        assertThat(TrainingRules.isValidScore(new BigDecimal("100.01"))).isFalse();
        assertThat(TrainingRules.isValidScore(new BigDecimal("12.345"))).isFalse();
    }
}
