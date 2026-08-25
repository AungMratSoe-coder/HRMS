package com.ams.hrms.tools;

import java.util.List;
import java.util.Set;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.security.PasswordHasher;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.ui.main.MenuDefinition;

/**
 * Development-only Phase 4 verification against the live database:
 * provisions a restricted HR_OFFICER test account, signs in as admin and as
 * the officer, and proves that (a) the sidebar is filtered by permissions,
 * (b) SecurityService enforces at service level, and (c) role differences
 * behave correctly.
 */
public final class RbacSmokeTool {

    public static void main(String[] args) {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        provisionOfficerAccount();
        AuthService authService = ServiceRegistry.authService();

        // --- SUPER_ADMIN -------------------------------------------------
        var admin = authService.login("admin", "Admin@123");
        Set<Permissions> adminPerms = SessionContext.permissions();
        List<String> adminMenu = MenuDefinition.visibleTo(adminPerms)
                .stream().map(item -> item.id()).toList();
        System.out.println("admin: " + admin.username()
                + " perms=" + adminPerms.size()
                + " menu=" + adminMenu.size() + " items -> " + adminMenu);
        System.out.println("OK   admin can SETTINGS_MANAGE="
                + adminPerms.contains(Permissions.SETTINGS_MANAGE));

        try {
            SecurityService.require(Permissions.SETTINGS_MANAGE);
            System.out.println("OK   service gate passes for admin on SETTINGS_MANAGE");
        } catch (AuthorizationException e) {
            System.out.println("FAIL: admin denied SETTINGS_MANAGE");
        }

        authService.logout();

        // --- HR_OFFICER (restricted) ------------------------------------
        var officer = authService.login("officer", "Officer@123");
        Set<Permissions> officerPerms = SessionContext.permissions();
        List<String> officerMenu = MenuDefinition.visibleTo(officerPerms)
                .stream().map(item -> item.id()).toList();
        System.out.println("officer: " + officer.username()
                + " perms=" + officerPerms.size()
                + " menu=" + officerMenu.size() + " items -> " + officerMenu);

        boolean systemModulesHidden = !officerMenu.contains("settings")
                && !officerMenu.contains("audit");
        boolean operationalShown = officerMenu.contains("employees")
                && officerMenu.contains("attendance") && officerMenu.contains("leave");
        boolean payrollViewOnly = officerMenu.contains("payroll")
                && !officerPerms.contains(Permissions.PAYROLL_APPROVE);
        System.out.println(systemModulesHidden && operationalShown && payrollViewOnly
                ? "OK   officer sidebar filtered (no settings/audit; operations present; payroll view-only)"
                : "FAIL: officer sidebar filtering incorrect");

        try {
            SecurityService.require(Permissions.PAYROLL_APPROVE);
            System.out.println("FAIL: officer passed PAYROLL_APPROVE gate");
        } catch (AuthorizationException expected) {
            System.out.println("OK   service gate denies officer PAYROLL_APPROVE");
        }
        try {
            SecurityService.require(Permissions.EMPLOYEE_CREATE);
            System.out.println("OK   service gate allows officer EMPLOYEE_CREATE");
        } catch (Exception e) {
            System.out.println("FAIL: officer denied EMPLOYEE_CREATE (" + e.getMessage() + ")");
        }

        authService.logout();
        DatabaseConfig.close();
        System.exit(0);
    }

    /**
     * Creates (or refreshes) the development-only restricted account:
     * officer / Officer@123 with the seeded HR_OFFICER role.
     */
    static void provisionOfficerAccount() {
        String hash = PasswordHasher.hash("Officer@123");
        new Sql().executeUpdate(
                "INSERT INTO users (username, password_hash, full_name, email, is_active) "
                        + "VALUES ('officer', ?, 'Olive Officer', 'officer@ams.local', 1) "
                        + "AS new "
                        + "ON DUPLICATE KEY UPDATE password_hash = new.password_hash, is_active = 1",
                hash);
        Long userId = new Sql().scalarLong(
                "SELECT id FROM users WHERE username = 'officer'");
        new Sql().executeUpdate(
                "INSERT IGNORE INTO user_roles (user_id, role_id) "
                        + "SELECT ?, id FROM roles WHERE role_code = 'HR_OFFICER'",
                userId);
        System.out.println("provisioned dev account: officer / Officer@123 (HR_OFFICER)");
    }
}
