package com.ams.hrms.service;

import java.util.Set;

/**
 * Approval scoping rules (pure logic, spec section 7): department managers
 * decide only for employees of their own department; HR, Finance and Super
 * Admin decide globally. Unit-testable without database or session.
 */
public final class ApprovalScope {

    /** Role codes whose holders approve across all departments. */
    public static final Set<String> GLOBAL_APPROVER_ROLES =
            Set.of("SUPER_ADMIN", "HR_MANAGER", "HR_OFFICER", "FINANCE");

    public static final String MANAGER_ROLE = "MANAGER";

    /** Sentinel: scoped manager whose own department cannot be resolved. */
    public static final Long NO_DEPARTMENT = -1L;

    private ApprovalScope() {
    }

    /** True when the role set holds MANAGER without any global approver role. */
    public static boolean isScopedManager(Set<String> roleCodes) {
        boolean manager = roleCodes.contains(MANAGER_ROLE);
        boolean global = roleCodes.stream().anyMatch(GLOBAL_APPROVER_ROLES::contains);
        return manager && !global;
    }

    /**
     * True when a viewer limited to {@code viewerDepartmentId} may decide a
     * request of an employee in {@code employeeDepartmentId}. A null viewer
     * department means unrestricted (HR/admin); {@link #NO_DEPARTMENT} denies
     * everything (fail closed), as does an employee without a department.
     */
    public static boolean canDecide(Long viewerDepartmentId, Long employeeDepartmentId) {
        if (viewerDepartmentId == null) {
            return true;
        }
        return !NO_DEPARTMENT.equals(viewerDepartmentId)
                && viewerDepartmentId.equals(employeeDepartmentId);
    }
}
