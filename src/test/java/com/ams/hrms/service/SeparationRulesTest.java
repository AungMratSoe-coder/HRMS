package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * Separation rules (spec sections 26 and 55): resignation transitions,
 * notice-period math and termination categories - pure logic, verified
 * without UI or database.
 */
class SeparationRulesTest {

    // ------------------------------------------------------------------
    // Resignation transitions
    // ------------------------------------------------------------------

    @Test
    void submittedCanApproveRejectOrWithdraw() {
        assertThat(SeparationRules.canTransitionResignation("SUBMITTED", "APPROVED")).isTrue();
        assertThat(SeparationRules.canTransitionResignation("SUBMITTED", "REJECTED")).isTrue();
        assertThat(SeparationRules.canTransitionResignation("SUBMITTED", "WITHDRAWN")).isTrue();
        assertThat(SeparationRules.canTransitionResignation("SUBMITTED", "PROCESSED")).isFalse();
    }

    @Test
    void onlyApprovedResignationsCanBeProcessed() {
        assertThat(SeparationRules.canTransitionResignation("APPROVED", "PROCESSED")).isTrue();
        assertThat(SeparationRules.canTransitionResignation("SUBMITTED", "PROCESSED"))
                .isFalse();
        assertThat(SeparationRules.canTransitionResignation("REJECTED", "PROCESSED"))
                .isFalse();
    }

    @Test
    void terminalStatesAreFrozen() {
        for (String terminal : new String[]{"REJECTED", "WITHDRAWN", "PROCESSED"}) {
            assertThat(SeparationRules.canTransitionResignation(terminal, "APPROVED"))
                    .isFalse();
            assertThat(SeparationRules.canTransitionResignation(terminal, "SUBMITTED"))
                    .isFalse();
        }
        assertThat(SeparationRules.canTransitionResignation("APPROVED", "REJECTED"))
                .isFalse();
    }

    @Test
    void unknownStatusesAreRejected() {
        assertThat(SeparationRules.canTransitionResignation(null, "APPROVED")).isFalse();
        assertThat(SeparationRules.canTransitionResignation("UNKNOWN", "APPROVED")).isFalse();
        assertThat(SeparationRules.canTransitionResignation("SUBMITTED", null)).isFalse();
    }

    // ------------------------------------------------------------------
    // Notice period math
    // ------------------------------------------------------------------

    @Test
    void noticeDaysCountsExclusiveOfEndDatePlusOne() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        assertThat(SeparationRules.noticeDays(start, start.plusDays(30))).isEqualTo(30);
        assertThat(SeparationRules.noticeDays(start, start)).isZero();
    }

    @Test
    void invalidOrderReturnsNegativeOne() {
        LocalDate start = LocalDate.of(2026, 8, 1);
        assertThat(SeparationRules.noticeDays(start, start.minusDays(1))).isEqualTo(-1);
        assertThat(SeparationRules.noticeDays(null, start)).isEqualTo(-1);
        assertThat(SeparationRules.noticeDays(start, null)).isEqualTo(-1);
    }

    // ------------------------------------------------------------------
    // Termination categories
    // ------------------------------------------------------------------

    @Test
    void categoriesMatchSchema() {
        for (String category : new String[]{
                "MISCONDUCT", "PERFORMANCE", "LAYOFF", "CONTRACT_END", "OTHER"}) {
            assertThat(SeparationRules.isValidCategory(category)).isTrue();
        }
        assertThat(SeparationRules.isValidCategory("WHIM")).isFalse();
        assertThat(SeparationRules.isValidCategory(null)).isFalse();
    }
}
