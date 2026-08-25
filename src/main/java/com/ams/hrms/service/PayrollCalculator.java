package com.ams.hrms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Pure payroll arithmetic (spec section 20) — no database, no UI.
 *
 * <pre>
 * gross     = basic + allowances + bonuses + overtime
 * deduction = tax% × taxable + social_security% × gross + other_deductions
 * net       = gross − total_deduction
 * </pre>
 */
public final class PayrollCalculator {

    private PayrollCalculator() {
    }

    /** Computes tax from gross using the configured percentage rate. */
    public static BigDecimal tax(BigDecimal gross, BigDecimal taxRatePercent) {
        if (taxRatePercent == null || taxRatePercent.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return gross.multiply(taxRatePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Social security contribution from gross using the employee percentage. */
    public static BigDecimal socialSecurity(BigDecimal gross, BigDecimal percent) {
        if (percent == null || percent.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return gross.multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /** Gross = basic + allowances + bonuses + overtime. */
    public static BigDecimal gross(BigDecimal basic, BigDecimal allowances,
                                   BigDecimal bonuses, BigDecimal overtime) {
        return nz(basic).add(nz(allowances)).add(nz(bonuses)).add(nz(overtime));
    }

    /** Total deduction = tax + SS + loan + other (already-computed amounts). */
    public static BigDecimal totalDeduction(BigDecimal... components) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal component : components) {
            total = total.add(nz(component));
        }
        return total;
    }

    /** Net = gross − total deduction (never negative). */
    public static BigDecimal net(BigDecimal gross, BigDecimal totalDeduction) {
        BigDecimal result = nz(gross).subtract(nz(totalDeduction));
        return result.signum() < 0 ? BigDecimal.ZERO : result.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
