package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * Payroll arithmetic (spec sections 20 and 55): gross build-up, percentage
 * deductions, rounding and the never-negative net guarantee - pure logic,
 * verified without UI or database.
 */
class PayrollCalculatorTest {

    private static final BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    // ------------------------------------------------------------------
    // Gross
    // ------------------------------------------------------------------

    @Test
    void grossSumsAllComponents() {
        assertThat(PayrollCalculator.gross(
                bd("1000"), bd("100"), bd("50"), bd("25")))
                .isEqualByComparingTo("1175");
    }

    @Test
    void grossTreatsMissingComponentsAsZero() {
        assertThat(PayrollCalculator.gross(bd("1000"), null, null, null))
                .isEqualByComparingTo("1000");
        assertThat(PayrollCalculator.gross(null, null, null, null))
                .isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------
    // Percentage deductions
    // ------------------------------------------------------------------

    @Test
    void taxAppliesConfiguredPercentage() {
        assertThat(PayrollCalculator.tax(bd("1200"), bd("5")))
                .isEqualByComparingTo("60");
    }

    @Test
    void taxRoundsHalfUpToMoneyScale() {
        assertThat(PayrollCalculator.tax(bd("100"), bd("2.5")))
                .isEqualByComparingTo("2.50");
        assertThat(PayrollCalculator.tax(bd("10"), bd("1.25")))
                .isEqualByComparingTo("0.13");
        assertThat(PayrollCalculator.tax(bd("10"), bd("1.24")))
                .isEqualByComparingTo("0.12");
    }

    @Test
    void taxAndSocialSecurityAreZeroWithoutRate() {
        assertThat(PayrollCalculator.tax(bd("1000"), null)).isEqualTo(BigDecimal.ZERO);
        assertThat(PayrollCalculator.tax(bd("1000"), bd("0"))).isEqualTo(BigDecimal.ZERO);
        assertThat(PayrollCalculator.tax(bd("1000"), bd("-3"))).isEqualTo(BigDecimal.ZERO);
        assertThat(PayrollCalculator.socialSecurity(bd("1000"), null))
                .isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void socialSecurityAppliesEmployeePercent() {
        assertThat(PayrollCalculator.socialSecurity(bd("2000"), bd("2")))
                .isEqualByComparingTo("40");
    }

    // ------------------------------------------------------------------
    // Totals and net
    // ------------------------------------------------------------------

    @Test
    void totalDeductionSumsComponentsAndIgnoresNulls() {
        assertThat(PayrollCalculator.totalDeduction(
                bd("60"), bd("40"), null, bd("10")))
                .isEqualByComparingTo("110");
        assertThat(PayrollCalculator.totalDeduction())
                .isEqualTo(BigDecimal.ZERO);
    }

    @Test
    void netIsGrossMinusDeductions() {
        assertThat(PayrollCalculator.net(bd("1175"), bd("110")))
                .isEqualByComparingTo("1065");
    }

    @Test
    void netNeverGoesNegative() {
        assertThat(PayrollCalculator.net(bd("100"), bd("500")))
                .isEqualByComparingTo("0");
        assertThat(PayrollCalculator.net(null, null))
                .isEqualByComparingTo("0");
    }

    // ------------------------------------------------------------------
    // End-to-end formula from spec section 20
    // ------------------------------------------------------------------

    @Test
    void fullPipelineMatchesTheSpecFormula() {
        BigDecimal basic = bd("1500");
        BigDecimal allowances = bd("150");
        BigDecimal bonuses = bd("100");
        BigDecimal overtime = bd("75");
        BigDecimal otherDeduction = bd("20");

        BigDecimal gross = PayrollCalculator.gross(
                basic, allowances, bonuses, overtime);                 // 1825
        BigDecimal tax = PayrollCalculator.tax(gross, bd("5"));        // 91.25
        BigDecimal ss = PayrollCalculator.socialSecurity(gross, bd("2")); // 36.50
        BigDecimal total = PayrollCalculator.totalDeduction(tax, ss, otherDeduction);
        BigDecimal net = PayrollCalculator.net(gross, total);

        assertThat(gross).isEqualByComparingTo("1825");
        assertThat(tax).isEqualByComparingTo("91.25");
        assertThat(ss).isEqualByComparingTo("36.50");
        assertThat(total).isEqualByComparingTo("147.75");
        assertThat(net).isEqualByComparingTo("1677.25");
    }
}
