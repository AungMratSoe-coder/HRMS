package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PayrollRulesTest {

    @Test
    void eachStepRequiresItsExactSourceStatus() {
        assertThat(PayrollRules.requiredSourceOf("REVIEWED")).isEqualTo("CALCULATED");
        assertThat(PayrollRules.requiredSourceOf("APPROVED")).isEqualTo("REVIEWED");
        assertThat(PayrollRules.requiredSourceOf("PAID")).isEqualTo("APPROVED");
    }

    @Test
    void legalChainIsAccepted() {
        assertThat(PayrollRules.isLegalTransition("CALCULATED", "REVIEWED")).isTrue();
        assertThat(PayrollRules.isLegalTransition("REVIEWED", "APPROVED")).isTrue();
        assertThat(PayrollRules.isLegalTransition("APPROVED", "PAID")).isTrue();
    }

    @Test
    void skippedStepsAndBackwardMovesAreRejected() {
        assertThat(PayrollRules.isLegalTransition("CALCULATED", "PAID")).isFalse();
        assertThat(PayrollRules.isLegalTransition("CALCULATED", "APPROVED")).isFalse();
        assertThat(PayrollRules.isLegalTransition("REVIEWED", "PAID")).isFalse();
        assertThat(PayrollRules.isLegalTransition("APPROVED", "REVIEWED")).isFalse();
        assertThat(PayrollRules.isLegalTransition("PAID", "APPROVED")).isFalse();
        assertThat(PayrollRules.isLegalTransition("PAID", "PAID")).isFalse();
    }

    @Test
    void unknownOrEntryStatusesHaveNoRequiredSource() {
        assertThat(PayrollRules.requiredSourceOf("CALCULATED")).isNull();
        assertThat(PayrollRules.requiredSourceOf("WEIRD")).isNull();
        assertThat(PayrollRules.requiredSourceOf(null)).isNull();

        assertThat(PayrollRules.isLegalTransition("ANYTHING", "CALCULATED")).isFalse();
        assertThat(PayrollRules.isLegalTransition(null, "PAID")).isFalse();
    }
}
