package com.ams.hrms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.ams.hrms.validator.Validators;

/**
 * Pure overtime rules (spec sections 19 and 55): the configurable rate
 * derivation, amount arithmetic and request validation - no database, no UI.
 *
 * <pre>
 *   hourly_base = basic_salary / working_days_per_month / 8
 *   rate        = hourly_base × multiplier        (2 dp)
 *   amount      = hours × rate                    (2 dp, HALF_UP)
 * </pre>
 */
public final class OvertimeRules {

    public static final int HOURS_PER_DAY = 8;
    public static final BigDecimal DEFAULT_WORKING_DAYS = new BigDecimal("22");
    public static final BigDecimal DEFAULT_MULTIPLIER = new BigDecimal("1.5");
    public static final BigDecimal MAX_HOURS = new BigDecimal("12");

    private static final int MATH_SCALE = 6;
    private static final int MONEY_SCALE = 2;

    private OvertimeRules() {
    }

    /** Rate snapshot for one approval (spec section 19 formula). */
    public static RateBreakdown rate(BigDecimal basicSalary,
                                     BigDecimal workingDaysPerMonth,
                                     BigDecimal multiplier) {
        BigDecimal days = workingDaysPerMonth == null || workingDaysPerMonth.signum() <= 0
                ? DEFAULT_WORKING_DAYS
                : workingDaysPerMonth;
        BigDecimal factor = multiplier == null || multiplier.signum() <= 0
                ? DEFAULT_MULTIPLIER
                : multiplier;
        BigDecimal salary = basicSalary == null ? BigDecimal.ZERO : basicSalary;

        BigDecimal dailyRate = salary.divide(days, MATH_SCALE, RoundingMode.HALF_UP);
        BigDecimal hourlyBase = dailyRate.divide(
                BigDecimal.valueOf(HOURS_PER_DAY), MATH_SCALE, RoundingMode.HALF_UP);
        BigDecimal ratePerHour = hourlyBase.multiply(factor)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        return new RateBreakdown(hourlyBase.setScale(MONEY_SCALE, RoundingMode.HALF_UP),
                factor, ratePerHour);
    }

    /** Approved amount: hours × rate, money-rounded HALF_UP. */
    public static BigDecimal amount(BigDecimal hours, BigDecimal ratePerHour) {
        if (hours == null || ratePerHour == null) {
            return BigDecimal.ZERO;
        }
        return hours.multiply(ratePerHour).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /** Field validation for a submitted request. */
    public static List<String> validateRequest(LocalDate requestDate,
                                               BigDecimal hours, String reason) {
        List<String> errors = new ArrayList<>();
        if (requestDate == null) {
            errors.add("Overtime date is required.");
        }
        if (hours == null || hours.signum() <= 0
                || hours.compareTo(MAX_HOURS) > 0) {
            errors.add("Hours must be between 0.01 and " + MAX_HOURS.toPlainString() + ".");
        }
        Validators.required(errors, reason, "Reason");
        Validators.maxLength(errors, reason, 500, "Reason");
        return errors;
    }

    /** Derived rate values carried onto the approved record. */
    public record RateBreakdown(BigDecimal hourlyBase, BigDecimal multiplier,
                                BigDecimal ratePerHour) {
    }
}
