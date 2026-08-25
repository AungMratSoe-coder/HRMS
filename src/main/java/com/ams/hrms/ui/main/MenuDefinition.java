package com.ams.hrms.ui.main;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.ams.hrms.component.SidebarMenuPanel;
import com.ams.hrms.security.Permissions;

/**
 * Single source of truth for the application's main navigation entries,
 * including the permission each module requires (spec section 8). The
 * dashboard is available to any authenticated user.
 */
public final class MenuDefinition {

    public static final List<SidebarMenuPanel.MenuItem> ALL = List.of(
            new SidebarMenuPanel.MenuItem("dashboard", "dashboard", "Dashboard", null),
            new SidebarMenuPanel.MenuItem("departments", "building", "Departments", Permissions.DEPARTMENT_VIEW),
            new SidebarMenuPanel.MenuItem("positions", "badge", "Positions", Permissions.POSITION_VIEW),
            new SidebarMenuPanel.MenuItem("employees", "employees", "Employees", Permissions.EMPLOYEE_VIEW),
            new SidebarMenuPanel.MenuItem("recruitment", "recruitment", "Recruitment", Permissions.RECRUITMENT_VIEW),
            new SidebarMenuPanel.MenuItem("onboarding", "check", "Onboarding", Permissions.ONBOARDING_MANAGE),
            new SidebarMenuPanel.MenuItem("attendance", "attendance", "Attendance", Permissions.ATTENDANCE_VIEW),
            new SidebarMenuPanel.MenuItem("shifts", "attendance", "Shifts", Permissions.SHIFT_VIEW),
            new SidebarMenuPanel.MenuItem("overtime", "attendance", "Overtime", Permissions.OVERTIME_VIEW),
            new SidebarMenuPanel.MenuItem("leave", "leave", "Leave", Permissions.LEAVE_VIEW),
            new SidebarMenuPanel.MenuItem("payroll", "payroll", "Payroll", Permissions.PAYROLL_VIEW),
            new SidebarMenuPanel.MenuItem("payslips", "payroll", "Payslips", Permissions.PAYSLIP_VIEW),
            new SidebarMenuPanel.MenuItem("performance", "performance", "Performance", Permissions.PERFORMANCE_VIEW),
            new SidebarMenuPanel.MenuItem("training", "training", "Training", Permissions.TRAINING_VIEW),
            new SidebarMenuPanel.MenuItem("assets", "assets", "Assets", Permissions.ASSET_VIEW),
            new SidebarMenuPanel.MenuItem("documents", "documents", "Documents", Permissions.DOCUMENT_MANAGE),
            new SidebarMenuPanel.MenuItem("separation", "user", "Separation", Permissions.SEPARATION_MANAGE),
            new SidebarMenuPanel.MenuItem("reports", "reports", "Reports", Permissions.REPORT_VIEW),
            new SidebarMenuPanel.MenuItem("audit", "audit", "Audit Log", Permissions.AUDIT_LOG_VIEW),
            new SidebarMenuPanel.MenuItem("settings", "settings", "Settings", Permissions.SETTINGS_MANAGE));

    /**
     * Module ids that are pure management consoles with no self-service
     * value: hidden from accounts whose only role is EMPLOYEE (their own
     * data lives in the My Profile dialog instead).
     */
    private static final java.util.Set<String> SELF_SERVICE_HIDDEN =
            java.util.Set.of("employees", "attendance");

    /** True when the given module is hidden from EMPLOYEE-only accounts. */
    public static boolean hiddenForSelfService(String id) {
        return SELF_SERVICE_HIDDEN.contains(id);
    }

    /**
     * Entries visible to a session holding {@code permissions}. Used to build
     * the sidebar; navigation is additionally guarded per click.
     */
    public static List<SidebarMenuPanel.MenuItem> visibleTo(Set<Permissions> permissions) {
        return visibleTo(permissions, false);
    }

    /**
     * Entries visible to a session holding {@code permissions};
     * {@code selfServiceOnly} additionally hides management-only modules
     * (directory, attendance console) from plain employee accounts.
     */
    public static List<SidebarMenuPanel.MenuItem> visibleTo(Set<Permissions> permissions,
                                                            boolean selfServiceOnly) {
        return ALL.stream()
                .filter(item -> item.requiredPermission() == null
                        || permissions.contains(item.requiredPermission()))
                .filter(item -> !selfServiceOnly
                        || !SELF_SERVICE_HIDDEN.contains(item.id()))
                .collect(Collectors.toList());
    }

    private MenuDefinition() {
    }

    /** Header title for a menu id; the id itself when unknown. */
    public static String titleFor(String id) {
        return ALL.stream()
                .filter(item -> item.id().equals(id))
                .map(SidebarMenuPanel.MenuItem::label)
                .findFirst()
                .orElse(id);
    }

    /** Menu item for an id; null when unknown. */
    public static SidebarMenuPanel.MenuItem byId(String id) {
        return ALL.stream()
                .filter(item -> item.id().equals(id))
                .findFirst()
                .orElse(null);
    }
}
