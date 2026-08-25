package com.ams.hrms.tools;

import java.time.LocalDate;
import java.util.List;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.model.Employee;
import com.ams.hrms.repository.EmployeeRepository;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.repository.UserRepository;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.OvertimeService;

/**
 * Verifies department-scoped approvals: a plain MANAGER account (linked to
 * an IT employee) approves leave/overtime of same-department employees, is
 * refused for other departments, cannot give final HR-level leave approval,
 * and a global approver (admin) is unrestricted. Idempotent: reuses its test
 * account and cleans created requests.
 */
public final class ManagerScopeSmokeTool {

    private static final String SMOKE_USERNAME = "mgrscope-user";
    private static final String FIRST_PASSWORD = "Start@123";
    private static final String OWN_PASSWORD = "Scoped@123";

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService auth = ServiceRegistry.authService();
        var userService = ServiceRegistry.userService();
        var employeeService = ServiceRegistry.employeeService();
        var leaveService = ServiceRegistry.leaveService();
        OvertimeService overtimeService = ServiceRegistry.overtimeService();
        UserRepository userRepository = new UserRepository();

        String adminPassword = loginAsAdmin(auth);

        // purge leftovers from an interrupted earlier run before resolving data
        purgeArtifacts();

        // --- resolve employees: IT manager, same-dept peer, other-dept peer -
        Employee managerEmployee = employeeService.findAll(
                        new EmployeeRepository.Filter("", null, null, "ACTIVE")).stream()
                .filter(e -> "IT-MGR".equals(positionCode(e.getId())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Seed expects an ACTIVE IT-MGR employee (EMP-0001)."));
        Employee sameDept = employeeService.findAll(
                        new EmployeeRepository.Filter("", null, null, "ACTIVE")).stream()
                .filter(e -> "IT".equals(deptCode(e.getDepartmentId()))
                        && !e.getId().equals(managerEmployee.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Seed expects a second ACTIVE IT employee (EMP-0003)."));
        Employee otherDept = employeeService.findAll(
                        new EmployeeRepository.Filter("", null, null, "ACTIVE")).stream()
                .filter(e -> !"IT".equals(deptCode(e.getDepartmentId())))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Seed expects an ACTIVE non-IT employee."));

        // --- prepare the scoped manager account ------------------------------
        var managerRole = userService.findRoles().stream()
                .filter(role -> role.code().equals("MANAGER")).findFirst().orElseThrow();
        Long userId;
        var existing = userRepository.findAccountByUsername(SMOKE_USERNAME).orElse(null);
        if (existing == null) {
            userId = userService.createUser(SMOKE_USERNAME, "Manager Scope Smoke",
                    "mgrscope-smoke@example.com", FIRST_PASSWORD,
                    List.of(managerRole.id()));
        } else {
            userId = existing.id();
            userService.setActive(userId, true);
            userService.updateRoles(userId, List.of(managerRole.id()));
            userService.resetPassword(userId, FIRST_PASSWORD);
        }
        userService.setEmployeeLink(userId, managerEmployee.getId());

        // --- scoped sign-in ---------------------------------------------------
        auth.logout();
        auth.login(SMOKE_USERNAME, FIRST_PASSWORD);
        auth.completeForcedPasswordChange(OWN_PASSWORD);
        auth.logout();
        auth.login(SMOKE_USERNAME, OWN_PASSWORD);

        long type = new Sql().scalarLong(
                "SELECT id FROM leave_types WHERE status = 'ACTIVE' ORDER BY id LIMIT 1");
        LocalDate today = LocalDate.now();

        // same department, manager level -> allowed
        long own = insertLeave("LV-SM1", sameDept.getId(), type, today);
        check("scoped manager approves same-department leave",
                () -> {
                    leaveService.approve(own, "MANAGER", null);
                    return true;
                });

        // other department -> refused
        long foreign = insertLeave("LV-SM2", otherDept.getId(), type, today);
        check("scoped manager refused for other-department leave", () -> {
            try {
                leaveService.approve(foreign, "MANAGER", null);
                return false;
            } catch (BusinessException expected) {
                return true;
            }
        });

        // final (HR) level -> refused even for own department
        long own2 = insertLeave("LV-SM3", sameDept.getId(), type, today);
        check("scoped manager refused for HR-level approval", () -> {
            try {
                leaveService.approve(own2, "HR", null);
                return false;
            } catch (BusinessException expected) {
                return true;
            }
        });

        // rejection path is scoped too
        long foreign2 = insertLeave("LV-SM4", otherDept.getId(), type, today);
        check("scoped manager refused to reject other-department leave", () -> {
            try {
                leaveService.reject(foreign2, "scope smoke");
                return false;
            } catch (BusinessException expected) {
                return true;
            }
        });

        // overtime decisions follow the same rule (rows inserted directly:
        // the MANAGER role holds approval rights, not request rights)
        long foreignOtId = new Sql().executeInsert(
                "INSERT INTO overtime_requests (overtime_code, employee_id, request_date, "
                        + "hours, reason, status) VALUES ('OT-SM1', ?, ?, 1.00, "
                        + "'mgr-scope smoke foreign', 'PENDING')",
                otherDept.getId(), today);
        check("scoped manager refused to approve other-department overtime", () -> {
            try {
                overtimeService.approve(foreignOtId);
                return false;
            } catch (BusinessException expected) {
                return true;
            }
        });

        long ownOtId = new Sql().executeInsert(
                "INSERT INTO overtime_requests (overtime_code, employee_id, request_date, "
                        + "hours, reason, status) VALUES ('OT-SM2', ?, ?, 1.00, "
                        + "'mgr-scope smoke own', 'PENDING')",
                sameDept.getId(), today);
        check("scoped manager approves same-department overtime", () -> {
            overtimeService.approve(ownOtId);
            return true;
        });

        // --- positive control: global approver (admin) is unrestricted -------
        auth.logout();
        auth.login("admin", adminPassword);
        check("global approver (admin) approves cross-department leave", () -> {
            leaveService.approve(foreign, "MANAGER", null);
            return true;
        });

        // --- cleanup ----------------------------------------------------------
        purgeArtifacts();
        userService.setEmployeeLink(userId, null);
        userService.setActive(userId, false);

        System.out.println("passed=" + passed + " failed=" + failed);
        DatabaseConfig.close();
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Removes rows created by this tool (also from interrupted runs). */
    private static void purgeArtifacts() {
        new Sql().executeUpdate("DELETE FROM leave_requests WHERE leave_code LIKE 'LV-SM%'");
        new Sql().executeUpdate(
                "DELETE FROM overtime_requests WHERE overtime_code LIKE 'OT-SM%'");
    }

    private static long insertLeave(String code, long employeeId, long typeId,
            LocalDate date) {
        return new Sql().executeInsert(
                "INSERT INTO leave_requests (leave_code, employee_id, leave_type_id, "
                        + "start_date, end_date, number_of_days, reason, status) "
                        + "VALUES (?, ?, ?, ?, ?, 1.0, 'mgr-scope smoke', 'PENDING')",
                code, employeeId, typeId, date, date);
    }

    private static String deptCode(Long departmentId) {
        return new Sql().first("SELECT dept_code FROM departments WHERE id = ?",
                rs -> rs.getString("dept_code"), departmentId).orElse("");
    }

    private static String positionCode(long employeeId) {
        return new Sql().first(
                "SELECT p.position_code FROM employees e "
                        + "JOIN positions p ON p.id = e.position_id WHERE e.id = ?",
                rs -> rs.getString("position_code"), employeeId).orElse("");
    }

    /** Signs in as admin, tolerating leftover passwords from earlier runs. */
    private static String loginAsAdmin(AuthService auth) throws Exception {
        for (String candidate : new String[] {"Admin@123"}) {
            try {
                auth.login("admin", candidate);
                return candidate;
            } catch (Exception e) {
                // try the next candidate
            }
        }
        throw new IllegalStateException("Could not sign in as admin");
    }

    private static void check(String name, java.util.concurrent.Callable<Boolean> assertion) {
        try {
            boolean ok = Boolean.TRUE.equals(assertion.call());
            System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
            if (ok) {
                passed++;
            } else {
                failed++;
            }
        } catch (Exception e) {
            System.out.println("[FAIL] " + name + (" (" + e.getMessage() + ")"));
            failed++;
        }
    }
}
