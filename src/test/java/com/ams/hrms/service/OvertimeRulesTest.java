package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ams.hrms.service.OvertimeRules.RateBreakdown;

/**
 * Overtime rules (spec sections 19 and 55): the configurable rate formula,
 * amount arithmetic and request validation - pure logic, verified without
 * UI or database.
 */
class OvertimeRulesTest {

    // ------------------------------------------------------------------
    // Rate derivation
    // ------------------------------------------------------------------

    @Test
    void rateFollowsTheConfiguredFormula() {
        // 2200 / 22 days = 100/day -> 12.50/hour -> × 1.5 = 18.75/hour.
        RateBreakdown breakdown = OvertimeRules.rate(
                new BigDecimal("2200"), new BigDecimal("22"), new BigDecimal("1.5"));

        assertThat(breakdown.hourlyBase()).isEqualByComparingTo("12.50");
        assertThat(breakdown.multiplier()).isEqualByComparingTo("1.5");
        assertThat(breakdown.ratePerHour()).isEqualByComparingTo("18.75");
    }

    @Test
    void rateRoundsIntermediateMathAtSixDecimals() {
        // 1000 / 23 = 43.478261/day -> 5.434783/hour -> × 2 = 10.869565 -> 10.87.
        RateBreakdown breakdown = OvertimeRules.rate(
                new BigDecimal("1000"), new BigDecimal("23"), new BigDecimal("2"));

        assertThat(breakdown.hourlyBase()).isEqualByComparingTo("5.43");
        assertThat(breakdown.ratePerHour()).isEqualByComparingTo("10.87");
    }

    @Test
    void invalidConfigurationFallsBackToDefaults() {
        RateBreakdown breakdown = OvertimeRules.rate(
                new BigDecimal("2200"), BigDecimal.ZERO, null);

        assertThat(breakdown.multiplier()).isEqualByComparingTo("1.5");
        // 2200 / 22 / 8 = 12.50 × 1.5 = 18.75.
        assertThat(breakdown.ratePerHour()).isEqualByComparingTo("18.75");
    }

    @Test
    void missingSalaryYieldsZeroRate() {
        RateBreakdown breakdown = OvertimeRules.rate(null, null, null);
        assertThat(breakdown.ratePerHour()).isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------
    // Amount
    // ------------------------------------------------------------------

    @Test
    void amountIsHoursTimesRate() {
        assertThat(OvertimeRules.amount(
                new BigDecimal("3"), new BigDecimal("18.75")))
                .isEqualByComparingTo("56.25");
    }

    @Test
    void amountRoundsHalfUpToMoneyScale() {
        assertThat(OvertimeRules.amount(
                new BigDecimal("1.5"), new BigDecimal("10.87")))
                .isEqualByComparingTo("16.31"); // 16.305 -> 16.31
        assertThat(OvertimeRules.amount(
                new BigDecimal("2"), new BigDecimal("7.33")))
                .isEqualByComparingTo("14.66");
    }

    @Test
    void missingInputsYieldZeroAmount() {
        assertThat(OvertimeRules.amount(null, new BigDecimal("10")))
                .isEqualTo(BigDecimal.ZERO);
        assertThat(OvertimeRules.amount(new BigDecimal("2"), null))
                .isEqualTo(BigDecimal.ZERO);
    }

    // ------------------------------------------------------------------
    // Request validation
    // ------------------------------------------------------------------

    @Test
    void validRequestPasses() {
        List<String> errors = OvertimeRules.validateRequest(
                LocalDate.of(2026, 8, 21), new BigDecimal("2.5"), "Release rollout");
        assertThat(errors).isEmpty();
    }

    @Test
    void missingDateOrReasonIsRejected() {
        List<String> errors = OvertimeRules.validateRequest(
                null, new BigDecimal("2"), "");
        assertThat(errors).anyMatch(e -> e.contains("date"));
        assertThat(errors).anyMatch(e -> e.contains("Reason"));
    }

    @Test
    void hoursOutsideBoundsAreRejected() {
        assertThat(OvertimeRules.validateRequest(
                LocalDate.now(), BigDecimal.ZERO, "reason"))
                .anyMatch(e -> e.contains("Hours"));

        assertThat(OvertimeRules.validateRequest(
                LocalDate.now(), new BigDecimal("12.01"), "reason"))
                .anyMatch(e -> e.contains("Hours"));

        assertThat(OvertimeRules.validateRequest(
                LocalDate.now(), new BigDecimal("12"), "reason"))
                .noneMatch(e -> e.contains("Hours"));
    }
}
