package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Department-manager approval scoping: plain MANAGER accounts decide only
 * inside their own department; global roles are unrestricted; unknown
 * departments fail closed.
 */
class ApprovalScopeTest {

    @Test
    void managerWithoutGlobalRoleIsScoped() {
        assertThat(ApprovalScope.isScopedManager(Set.of("MANAGER"))).isTrue();
        assertThat(ApprovalScope.isScopedManager(Set.of("MANAGER", "EMPLOYEE"))).isTrue();
    }

    @Test
    void globalRolesAreNeverScoped() {
        assertThat(ApprovalScope.isScopedManager(Set.of("SUPER_ADMIN"))).isFalse();
        assertThat(ApprovalScope.isScopedManager(Set.of("HR_MANAGER"))).isFalse();
        assertThat(ApprovalScope.isScopedManager(Set.of("HR_OFFICER"))).isFalse();
        assertThat(ApprovalScope.isScopedManager(Set.of("FINANCE"))).isFalse();
        // A global role plus MANAGER still decides globally.
        assertThat(ApprovalScope.isScopedManager(Set.of("MANAGER", "HR_MANAGER"))).isFalse();
    }

    @Test
    void nonManagerAccountsAreNotScoped() {
        assertThat(ApprovalScope.isScopedManager(Set.of("EMPLOYEE"))).isFalse();
        assertThat(ApprovalScope.isScopedManager(Set.of())).isFalse();
    }

    @Test
    void nullViewerDepartmentMeansUnrestricted() {
        assertThat(ApprovalScope.canDecide(null, 3L)).isTrue();
        assertThat(ApprovalScope.canDecide(null, null)).isTrue();
    }

    @Test
    void scopedManagerOnlyDecidesInsideOwnDepartment() {
        assertThat(ApprovalScope.canDecide(3L, 3L)).isTrue();
        assertThat(ApprovalScope.canDecide(3L, 4L)).isFalse();
    }

    @Test
    void unresolvableOrMissingDepartmentsFailClosed() {
        assertThat(ApprovalScope.canDecide(ApprovalScope.NO_DEPARTMENT, 3L)).isFalse();
        assertThat(ApprovalScope.canDecide(3L, null)).isFalse();
        assertThat(ApprovalScope.canDecide(ApprovalScope.NO_DEPARTMENT, null)).isFalse();
    }
}
