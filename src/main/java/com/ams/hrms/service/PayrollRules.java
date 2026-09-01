package com.ams.hrms.service;

import java.util.Map;

/**
 * Pure payroll state machine (spec section 20) - no database, no UI.
 *
 * <pre>
 *   CALCULATED → REVIEWED → APPROVED → PAID
 * </pre>
 *
 * <p>Each step may only be entered from its exact required source status;
 * skipping steps (e.g. CALCULATED → APPROVED → PAID in one jump) is illegal.
 * Approved payrolls are immutable (rule 7): no transition leaves the PAID
 * state and no backward move exists.</p>
 */
public final class PayrollRules {

    /** The only status each target status may legally be reached from. */
    private static final Map<String, String> REQUIRED_SOURCE = Map.of(
            "REVIEWED", "CALCULATED",
            "APPROVED", "REVIEWED",
            "PAID", "APPROVED");

    private PayrollRules() {
    }

    /**
     * Source status required before moving to {@code targetStatus}, or null
     * when the target is not a known/enterable status (including CALCULATED,
     * which is only produced by calculation).
     */
    public static String requiredSourceOf(String targetStatus) {
        return targetStatus == null ? null : REQUIRED_SOURCE.get(targetStatus);
    }

    /** True when moving directly from {@code from} to {@code to} is legal. */
    public static boolean isLegalTransition(String from, String to) {
        return from != null && from.equals(requiredSourceOf(to));
    }
}
