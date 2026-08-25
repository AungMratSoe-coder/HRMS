package com.ams.hrms.validator;

import java.math.BigDecimal;
import java.util.List;

/**
 * Reusable input validation helpers (spec section 31). Each helper appends a
 * human-readable problem to the shared error list; dialogs show the list in
 * one banner. All checks are pure string/number logic - unit-testable
 * without any UI or database.
 */
public final class Validators {

    /** Codes: 2-20 chars, letters/digits/dash/underscore (e.g. HR, IT-DEV). */
    public static final String CODE_PATTERN = "^[A-Za-z0-9_-]{2,20}$";

    private static final String EMAIL_PATTERN = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$";
    private static final String PHONE_PATTERN = "^[0-9+\\-\\s()]{6,20}$";

    private Validators() {
    }

    public static String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }

    public static void required(List<String> errors, String value, String label) {
        if (normalize(value).isEmpty()) {
            errors.add(label + " is required.");
        }
    }

    public static void maxLength(List<String> errors, String value, int max, String label) {
        if (value != null && value.length() > max) {
            errors.add(label + " must be at most " + max + " characters.");
        }
    }

    public static void pattern(List<String> errors, String value, String label,
                               String regex, String sample) {
        String normalized = normalize(value);
        if (!normalized.isEmpty() && !normalized.matches(regex)) {
            errors.add(label + " format is invalid (expected like " + sample + ").");
        }
    }

    /**
     * Parses a money field. Blank returns null; invalid text appends an error
     * and returns null.
     */
    public static BigDecimal parseMoney(List<String> errors, String raw, String label) {
        String normalized = normalize(raw);
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            BigDecimal value = new BigDecimal(normalized);
            if (value.scale() > 2) {
                errors.add(label + " allows at most two decimal places.");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add(label + " must be a number (e.g. 1500 or 1499.50).");
            return null;
        }
    }

    public static void nonNegative(List<String> errors, BigDecimal value, String label) {
        if (value != null && value.signum() < 0) {
            errors.add(label + " cannot be negative.");
        }
    }

    public static void salaryRange(List<String> errors, BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            errors.add("Minimum salary cannot exceed maximum salary.");
        }
    }

    /** Optional email: validated only when non-blank. */
    public static void email(List<String> errors, String value, String label) {
        String normalized = normalize(value);
        if (!normalized.isEmpty() && !normalized.matches(EMAIL_PATTERN)) {
            errors.add(label + " is not a valid email address.");
        }
    }

    /** Optional phone: digits/spaces/()+- only, 6-20 chars, when non-blank. */
    public static void phone(List<String> errors, String value, String label) {
        String normalized = normalize(value);
        if (!normalized.isEmpty() && !normalized.matches(PHONE_PATTERN)) {
            errors.add(label + " format is invalid.");
        }
    }

    /**
     * Parses a 24h time (HH:mm). Blank appends a required error; invalid
     * text appends a format error. Returns null when invalid/blank.
     */
    public static java.time.LocalTime parseTime(List<String> errors, String raw, String label) {
        String normalized = normalize(raw);
        if (normalized.isEmpty()) {
            errors.add(label + " is required.");
            return null;
        }
        if (!normalized.matches("^([01]\\d|2[0-3]):[0-5]\\d$")) {
            errors.add(label + " must use 24-hour HH:mm format (e.g. 08:30).");
            return null;
        }
        return java.time.LocalTime.parse(normalized);
    }
}
