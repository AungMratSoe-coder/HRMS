package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Leave rules (spec sections 18, 46 and 55): inclusive day counting,
 * overlap semantics (rule 5), balance sufficiency (rule 4) and request
 * validation - pure logic, verified without UI or database.
 */
class LeaveRulesTest {

    private static final LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    // ------------------------------------------------------------------
    // Day counting
    // ------------------------------------------------------------------

    @Test
    void dayCountIsInclusiveOnBothEnds() {
        assertThat(LeaveRules.daysInclusive(d("2026-08-01"), d("2026-08-01")))
                .isEqualTo(1);
        assertThat(LeaveRules.daysInclusive(d("2026-08-01"), d("2026-08-07")))
                .isEqualTo(7);
        assertThat(LeaveRules.daysInclusive(d("2026-01-01"), d("2026-12-31")))
                .isEqualTo(365);
        assertThat(LeaveRules.daysInclusive(d("2024-01-01"), d("2024-12-31")))
                .isEqualTo(366);
    }

    @Test
    void invertedOrMissingRangesCountZeroDays() {
        assertThat(LeaveRules.daysInclusive(d("2026-08-10"), d("2026-08-01")))
                .isEqualTo(0);
        assertThat(LeaveRules.daysInclusive(null, d("2026-08-01"))).isEqualTo(0);
        assertThat(LeaveRules.daysInclusive(d("2026-08-01"), null)).isEqualTo(0);
    }

    // ------------------------------------------------------------------
    // Overlap semantics (spec rule 5)
    // ------------------------------------------------------------------

    @Test
    void overlappingRangesAreDetected() {
        // Partial overlap on the tail.
        assertThat(LeaveRules.overlaps(
                d("2026-08-01"), d("2026-08-05"),
                d("2026-08-04"), d("2026-08-09"))).isTrue();
        // Partial overlap on the head.
        assertThat(LeaveRules.overlaps(
                d("2026-08-04"), d("2026-08-09"),
                d("2026-08-01"), d("2026-08-05"))).isTrue();
        // Fully contained.
        assertThat(LeaveRules.overlaps(
                d("2026-08-01"), d("2026-08-31"),
                d("2026-08-10"), d("2026-08-12"))).isTrue();
        // Identical ranges.
        assertThat(LeaveRules.overlaps(
                d("2026-08-01"), d("2026-08-05"),
                d("2026-08-01"), d("2026-08-05"))).isTrue();
    }

    @Test
    void boundaryDaysCountAsOverlapping() {
        // Shared boundary day: 1st–5th and 5th–9th both contain the 5th.
        assertThat(LeaveRules.overlaps(
                d("2026-08-01"), d("2026-08-05"),
                d("2026-08-05"), d("2026-08-09"))).isTrue();
    }

    @Test
    void disjointRangesDoNotOverlap() {
        assertThat(LeaveRules.overlaps(
                d("2026-08-01"), d("2026-08-05"),
                d("2026-08-06"), d("2026-08-09"))).isFalse();
        assertThat(LeaveRules.overlaps(
                d("2026-08-06"), d("2026-08-09"),
                d("2026-08-01"), d("2026-08-05"))).isFalse();
    }

    @Test
    void missingDatesNeverOverlap() {
        assertThat(LeaveRules.overlaps(null, null, d("2026-08-01"), d("2026-08-05")))
                .isFalse();
    }

    // ------------------------------------------------------------------
    // Balance sufficiency (spec rule 4)
    // ------------------------------------------------------------------

    @Test
    void requestWithinBalancePasses() {
        assertThat(LeaveRules.hasSufficientBalance(
                new BigDecimal("3.5"), new BigDecimal("3.5"))).isTrue();
        assertThat(LeaveRules.hasSufficientBalance(
                new BigDecimal("10"), new BigDecimal("2"))).isTrue();
    }

    @Test
    void requestBeyondBalanceFails() {
        assertThat(LeaveRules.hasSufficientBalance(
                new BigDecimal("2.99"), new BigDecimal("3"))).isFalse();
        assertThat(LeaveRules.hasSufficientBalance(
                BigDecimal.ZERO, new BigDecimal("1"))).isFalse();
    }

    @Test
    void missingValuesFailSafe() {
        assertThat(LeaveRules.hasSufficientBalance(null, new BigDecimal("1"))).isFalse();
        assertThat(LeaveRules.hasSufficientBalance(new BigDecimal("5"), null)).isTrue();
    }

    @Test
    void insufficientMessageNamesTypeAndAmounts() {
        String message = LeaveRules.insufficientBalanceMessage(
                new BigDecimal("1.50"), new BigDecimal("3"), "Annual");
        assertThat(message)
                .contains("1.50").contains("3").contains("Annual");
    }

    // ------------------------------------------------------------------
    // Request validation
    // ------------------------------------------------------------------

    @Test
    void validRequestHasNoErrors() {
        List<String> errors = LeaveRules.validateRequest(
                "Family trip", d("2026-09-01"), d("2026-09-03"));
        assertThat(errors).isEmpty();
    }

    @Test
    void invalidRequestsReportEveryProblem() {
        assertThat(LeaveRules.validateRequest("  ", null, null))
                .anyMatch(e -> e.contains("Reason"))
                .anyMatch(e -> e.contains("Start and end dates"));

        List<String> errors = LeaveRules.validateRequest(
                "Trip", d("2026-09-10"), d("2026-09-01"));
        assertThat(errors).anyMatch(e -> e.contains("before the start date"));
    }

    @Test
    void overLongLeavesAreRejected() {
        List<String> errors = LeaveRules.validateRequest(
                "Sabbatical", d("2026-01-01"), d("2027-06-30"));
        assertThat(errors).anyMatch(e -> e.contains("366"));
    }
}
