package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * Asset rules (spec sections 24 and 55): manual transitions, the
 * return-condition mapping, overdue detection and catalogue validation -
 * pure logic, verified without UI or database.
 */
class AssetRulesTest {

    // ------------------------------------------------------------------
    // Manual transitions
    // ------------------------------------------------------------------

    @Test
    void availableAssetCanGoToRepairRetiredOrLost() {
        assertThat(AssetRules.canTransitionManually("AVAILABLE", "UNDER_REPAIR")).isTrue();
        assertThat(AssetRules.canTransitionManually("AVAILABLE", "RETIRED")).isTrue();
        assertThat(AssetRules.canTransitionManually("AVAILABLE", "LOST")).isTrue();
    }

    @Test
    void repairCanReleaseOrRetire() {
        assertThat(AssetRules.canTransitionManually("UNDER_REPAIR", "AVAILABLE")).isTrue();
        assertThat(AssetRules.canTransitionManually("UNDER_REPAIR", "RETIRED")).isTrue();
        assertThat(AssetRules.canTransitionManually("UNDER_REPAIR", "LOST")).isTrue();
        assertThat(AssetRules.canTransitionManually("UNDER_REPAIR", "ASSIGNED")).isFalse();
    }

    @Test
    void assignedIsOnlyReachedThroughAssignFlow() {
        assertThat(AssetRules.canTransitionManually("AVAILABLE", "ASSIGNED")).isFalse();
        assertThat(AssetRules.canTransitionManually("UNDER_REPAIR", "ASSIGNED")).isFalse();

        // An assigned asset may still be retired or lost directly.
        assertThat(AssetRules.canTransitionManually("ASSIGNED", "RETIRED")).isTrue();
        assertThat(AssetRules.canTransitionManually("ASSIGNED", "LOST")).isTrue();
        assertThat(AssetRules.canTransitionManually("ASSIGNED", "AVAILABLE")).isFalse();
    }

    @Test
    void terminalStatesAreFrozen() {
        for (String terminal : new String[]{"RETIRED", "LOST"}) {
            assertThat(AssetRules.canTransitionManually(terminal, "AVAILABLE")).isFalse();
            assertThat(AssetRules.canTransitionManually(terminal, "UNDER_REPAIR")).isFalse();
            assertThat(AssetRules.canTransitionManually(terminal, "RETIRED")).isFalse();
        }
    }

    @Test
    void unknownStatusesAreRejected() {
        assertThat(AssetRules.canTransitionManually(null, "RETIRED")).isFalse();
        assertThat(AssetRules.canTransitionManually("UNKNOWN", "RETIRED")).isFalse();
        assertThat(AssetRules.canTransitionManually("AVAILABLE", null)).isFalse();
    }

    // ------------------------------------------------------------------
    // Return-condition mapping
    // ------------------------------------------------------------------

    @Test
    void healthyReturnsReturnTheAssetToAvailable() {
        for (String condition : new String[]{"GOOD", "FAIR", "POOR"}) {
            assertThat(AssetRules.statusAfterReturn(condition)).isEqualTo("AVAILABLE");
        }
    }

    @Test
    void damagedReturnsRouteIntoRepair() {
        assertThat(AssetRules.statusAfterReturn("DAMAGED")).isEqualTo("UNDER_REPAIR");
    }

    @Test
    void unknownConditionIsRejected() {
        assertThatThrownBy(() -> AssetRules.statusAfterReturn("NEW"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AssetRules.statusAfterReturn("PERFECT"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------
    // Overdue detection
    // ------------------------------------------------------------------

    @Test
    void overdueRequiresOpenStatusAndPastDueDate() {
        LocalDate today = LocalDate.of(2026, 8, 23);
        LocalDate yesterday = today.minusDays(1);
        LocalDate tomorrow = today.plusDays(1);

        assertThat(AssetRules.isOverdue("ASSIGNED", yesterday, today)).isTrue();
        assertThat(AssetRules.isOverdue("ASSIGNED", tomorrow, today)).isFalse();
        assertThat(AssetRules.isOverdue("ASSIGNED", null, today)).isFalse();
        assertThat(AssetRules.isOverdue("OVERDUE", yesterday, today)).isFalse();
        assertThat(AssetRules.isOverdue("RETURNED", yesterday, today)).isFalse();
    }

    // ------------------------------------------------------------------
    // Catalogue validation sets
    // ------------------------------------------------------------------

    @Test
    void categoriesMatchSchema() {
        assertThat(AssetRules.isValidCategory("LAPTOP")).isTrue();
        assertThat(AssetRules.isValidCategory("VEHICLE")).isTrue();
        assertThat(AssetRules.isValidCategory("SPACESHIP")).isFalse();
        assertThat(AssetRules.isValidCategory(null)).isFalse();
    }

    @Test
    void conditionsMatchSchema() {
        assertThat(AssetRules.isValidCondition("NEW")).isTrue();
        assertThat(AssetRules.isValidCondition("DAMAGED")).isTrue();
        assertThat(AssetRules.isValidCondition("PERFECT")).isFalse();
        assertThat(AssetRules.isValidCondition(null)).isFalse();
    }
}
