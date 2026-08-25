package com.ams.hrms.service;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ams.hrms.validator.Validators;

/**
 * Pure value rules for {@code app_settings} rows (unit-testable without UI
 * or database). Validation is type-driven with optional key-specific
 * refinements: numeric ranges, ISO currency codes, IANA timezones and a set
 * of keys that must never be blank.
 */
public final class SettingsValidator {

    /** Keys whose STRING value must be non-blank. */
    public static final Set<String> REQUIRED_KEYS =
            Set.of("company.name", "attendance.default_shift_code");

    /** Inclusive [min, max] ranges for NUMBER settings; absent = any number. */
    public static final Map<String, BigDecimal[]> NUMBER_RANGES = Map.ofEntries(
            Map.entry("payroll.overtime_rate_multiplier", range("0", "10")),
            Map.entry("payroll.tax_rate_percent", range("0", "100")),
            Map.entry("payroll.social_security_employee_percent", range("0", "100")),
            Map.entry("payroll.social_security_employer_percent", range("0", "100")),
            Map.entry("payroll.working_days_per_month", range("1", "31")),
            Map.entry("documents.expiry_warning_days", range("0", "365")));

    private SettingsValidator() {
    }

    /**
     * Validates {@code rawValue} for the setting and returns every problem
     * found (empty list when acceptable). {@code label} is a human-readable
     * field name for error messages.
     */
    public static List<String> validate(String key, String valueType,
                                        String rawValue, String label) {
        List<String> errors = new ArrayList<>();
        String normalized = Validators.normalize(rawValue);

        switch (valueType == null ? "" : valueType) {
            case com.ams.hrms.model.AppSetting.TYPE_BOOLEAN -> {
                if (!normalized.equalsIgnoreCase("true")
                        && !normalized.equalsIgnoreCase("false")) {
                    errors.add(label + " must be true or false.");
                }
            }
            case com.ams.hrms.model.AppSetting.TYPE_NUMBER -> {
                if (normalized.isEmpty()) {
                    errors.add(label + " is required.");
                    break;
                }
                BigDecimal number;
                try {
                    number = new BigDecimal(normalized);
                } catch (NumberFormatException e) {
                    errors.add(label + " must be a number (e.g. 22 or 1.5).");
                    break;
                }
                BigDecimal[] bounds = NUMBER_RANGES.get(key);
                if (bounds != null
                        && (number.compareTo(bounds[0]) < 0
                        || number.compareTo(bounds[1]) > 0)) {
                    errors.add(label + " must be between "
                            + bounds[0].stripTrailingZeros().toPlainString()
                            + " and "
                            + bounds[1].stripTrailingZeros().toPlainString() + ".");
                }
            }
            case com.ams.hrms.model.AppSetting.TYPE_STRING -> {
                if (REQUIRED_KEYS.contains(key)) {
                    Validators.required(errors, normalized, label);
                }
                if (!normalized.isEmpty()) {
                    Validators.maxLength(errors, normalized, 1000, label);
                    validateStringFormat(errors, key, normalized, label);
                }
            }
            default -> errors.add(label + " has an unknown value type.");
        }
        return errors;
    }

    /**
     * Canonical stored form of an accepted value: trimmed, booleans
     * lowercased. Call only after {@link #validate} passed.
     */
    public static String normalize(String valueType, String rawValue) {
        String normalized = Validators.normalize(rawValue);
        if (com.ams.hrms.model.AppSetting.TYPE_BOOLEAN.equals(valueType)) {
            return normalized.toLowerCase();
        }
        return normalized;
    }

    private static void validateStringFormat(List<String> errors, String key,
                                             String value, String label) {
        if ("payroll.currency".equals(key) && !value.matches("^[A-Z]{3}$")) {
            errors.add(label + " must be a 3-letter ISO code (e.g. USD).");
        }
        if ("app.timezone".equals(key)) {
            try {
                ZoneId.of(value);
            } catch (RuntimeException e) {
                errors.add(label + " is not a valid timezone (e.g. Asia/Yangon).");
            }
        }
    }

    private static BigDecimal[] range(String min, String max) {
        return new BigDecimal[]{new BigDecimal(min), new BigDecimal(max)};
    }
}
