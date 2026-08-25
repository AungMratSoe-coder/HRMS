package com.ams.hrms.service;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import com.ams.hrms.model.AssetAssignment;

/**
 * Asset business rules (spec sections 24 and 55): status transitions, the
 * return-condition to asset-status mapping and overdue detection - pure
 * logic, unit-testable without UI or database.
 */
public final class AssetRules {

    public static final String ASSET_AVAILABLE = "AVAILABLE";
    public static final String ASSET_ASSIGNED = "ASSIGNED";
    public static final String ASSET_UNDER_REPAIR = "UNDER_REPAIR";
    public static final String ASSET_RETIRED = "RETIRED";
    public static final String ASSET_LOST = "LOST";

    private static final Set<String> ASSET_STATUSES =
            Set.of(ASSET_AVAILABLE, ASSET_ASSIGNED, ASSET_UNDER_REPAIR, ASSET_RETIRED, ASSET_LOST);

    /** Manual transitions only; ASSIGNED is reached exclusively through assign(). */
    private static final Map<String, String> MANUAL_TRANSITIONS = Map.of(
            ASSET_AVAILABLE, ASSET_UNDER_REPAIR + "," + ASSET_RETIRED + "," + ASSET_LOST,
            ASSET_UNDER_REPAIR, ASSET_AVAILABLE + "," + ASSET_RETIRED + "," + ASSET_LOST,
            ASSET_ASSIGNED, ASSET_RETIRED + "," + ASSET_LOST,
            ASSET_RETIRED, "",
            ASSET_LOST, "");

    /** Return conditions allowed on an assignment (mirrors schema CHECK). */
    private static final Set<String> RETURN_CONDITIONS =
            Set.of("GOOD", "FAIR", "POOR", "DAMAGED");

    private static final Set<String> ASSET_CATEGORIES =
            Set.of("LAPTOP", "DESKTOP", "MONITOR", "PHONE", "TABLET", "ID_CARD",
                    "VEHICLE", "FURNITURE", "OTHER");

    private static final Set<String> ASSET_CONDITIONS =
            Set.of("NEW", "GOOD", "FAIR", "POOR", "DAMAGED");

    private AssetRules() {
    }

    /**
     * Manual (non-assignment) status transition; ASSIGNED is never a manual
     * target because it must always come with a live assignment record.
     */
    public static boolean canTransitionManually(String from, String to) {
        if (from == null || to == null
                || !ASSET_STATUSES.contains(from) || !ASSET_STATUSES.contains(to)
                || ASSET_ASSIGNED.equals(to)) {
            return false;
        }
        return MANUAL_TRANSITIONS.get(from).contains(to);
    }

    /** Status the asset takes after a return with the given condition. */
    public static String statusAfterReturn(String conditionOnReturn) {
        if (!RETURN_CONDITIONS.contains(conditionOnReturn)) {
            throw new IllegalArgumentException("Unknown return condition: " + conditionOnReturn);
        }
        return "DAMAGED".equals(conditionOnReturn) ? ASSET_UNDER_REPAIR : ASSET_AVAILABLE;
    }

    /** True when an open assignment's due date has passed. */
    public static boolean isOverdue(String assignmentStatus, LocalDate dueReturnDate,
                                    LocalDate today) {
        return (AssetAssignment.STATUS_ASSIGNED.equals(assignmentStatus)
                && dueReturnDate != null && dueReturnDate.isBefore(today));
    }

    /** Valid asset categories (mirrors schema CHECK). */
    public static boolean isValidCategory(String category) {
        return category != null && ASSET_CATEGORIES.contains(category);
    }

    /** Valid asset conditions (mirrors schema CHECK). */
    public static boolean isValidCondition(String condition) {
        return condition != null && ASSET_CONDITIONS.contains(condition);
    }
}
