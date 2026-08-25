package com.ams.hrms.tools;

import java.util.List;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Department;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.Position;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.security.PasswordHasher;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.DepartmentService;
import com.ams.hrms.service.EmployeeService;
import com.ams.hrms.service.PositionService;

/**
 * Development-only Phase 8 verification against the live database:
 * employee CRUD rules, salary-envelope enforcement, uniqueness, history
 * tracking, RBAC denial for a role without EMPLOYEE_CREATE (FINANCE), and
 * idempotent cleanup.
 */
public final class EmployeeSmokeTool {

    private static int failures;

    public static void main(String[] args) {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        purgeArtifacts();
        ensureFinanceTestUser();

        AuthService authService = ServiceRegistry.authService();
        EmployeeService employees = ServiceRegistry.employeeService();

        authService.login("admin", "Admin@123");

        Long itDeptId = ServiceRegistry.departmentService().findAll("IT")
                .stream().filter(d -> d.getCode().equals("IT")).findFirst().orElseThrow().getId();
        Position dev = ServiceRegistry.positionService().findAll("IT-DEV")
                .stream().filter(p -> p.getCode().equals("IT-DEV")).findFirst().orElseThrow();

        // --- create -------------------------------------------------------
        Employee employee = baseEmployee("SMK-E001", dev.getId());
        final long[] createdId = new long[1];
        check("create employee inside envelope", () -> {
            createdId[0] = employees.save(employee);
            return createdId[0] > 0;
        });
        long employeeId = createdId[0];
        employee.setId(employeeId); // subsequent saves are updates

        check("history row written on create",
                () -> employees.findHistory(employeeId).size() >= 1);

        // --- uniqueness ----------------------------------------------------
        Employee duplicate = baseEmployee("smk-e001", dev.getId());
        check("reject duplicate code (case-insensitive)",
                () -> {
                    try {
                        employees.save(duplicate);
                        return false;
                    } catch (ValidationException e) {
                        return e.getErrors().get(0).contains("already in use");
                    }
                });

        // --- salary envelope -----------------------------------------------
        Employee overpaid = baseEmployee("SMK-E002", dev.getId());
        overpaid.setBasicSalary(new java.math.BigDecimal("9999"));
        check("reject salary above position maximum",
                () -> {
                    try {
                        employees.save(overpaid);
                        return false;
                    } catch (ValidationException e) {
                        return e.getErrors().get(0).contains("above the position maximum");
                    }
                });

        // --- bad email ------------------------------------------------------
        Employee badEmail = baseEmployee("SMK-E003", dev.getId());
        badEmail.setEmail("not-an-email");
        check("reject invalid email",
                () -> {
                    try {
                        employees.save(badEmail);
                        return false;
                    } catch (ValidationException e) {
                        return e.getErrors().get(0).contains("valid email");
                    }
                });

        // --- update + salary history ----------------------------------------
        employee.setBasicSalary(new java.math.BigDecimal("1700"));
        check("update with salary change", () -> {
            employees.save(employee);
            return true;
        });
        boolean hasSalaryHistory = employees.findHistory(employeeId).stream()
                .anyMatch(entry -> "SALARY_CHANGE".equals(entry.changeType()));
        System.out.println((hasSalaryHistory ? "OK   " : "FAIL ")
                + "SALARY_CHANGE history recorded");
        if (!hasSalaryHistory) {
            failures++;
        }

        // --- status transitions ----------------------------------------------
        check("deactivate employee", () -> {
            employees.setStatus(employeeId, "INACTIVE");
            return true;
        });
        boolean hasStatusHistory = employees.findHistory(employeeId).stream()
                .anyMatch(entry -> "STATUS_CHANGE".equals(entry.changeType()));
        System.out.println((hasStatusHistory ? "OK   " : "FAIL ")
                + "STATUS_CHANGE history recorded");
        if (!hasStatusHistory) {
            failures++;
        }
        check("re-activate employee", () -> {
            employees.setStatus(employeeId, "ACTIVE");
            return true;
        });

        // --- RBAC: FINANCE cannot create/update employees ---------------------
        Employee financeAttempt = baseEmployee("SMK-E900", dev.getId()); // built under admin session
        authService.logout();
        authService.login("finance", "Finance@123");
        check("finance user denied employee creation at service gate",
                () -> {
                    try {
                        employees.save(financeAttempt);
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        authService.logout();

        // --- cleanup ------------------------------------------------------------
        authService.login("admin", "Admin@123");
        purgeArtifacts();
        System.out.println("cleanup: SMK-E* rows removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    private static Employee baseEmployee(String code, long positionId) {
        Employee employee = new Employee();
        employee.setCode(code);
        employee.setFirstName("Smoke");
        employee.setLastName("Test");
        employee.setGender("OTHER");
        employee.setDateOfBirth(java.time.LocalDate.of(1995, 1, 1));
        employee.setJoinDate(java.time.LocalDate.now());
        employee.setEmploymentType("FULL_TIME");
        Department department = ServiceRegistry.departmentService().findAll("IT")
                .stream().filter(d -> d.getCode().equals("IT")).findFirst().orElseThrow();
        employee.setDepartmentId(department.getId());
        employee.setPositionId(positionId);
        employee.setBasicSalary(new java.math.BigDecimal("1500"));
        return employee;
    }

    /** Creates/refreshes the restricted FINANCE test account. */
    private static void ensureFinanceTestUser() {
        String hash = PasswordHasher.hash("Finance@123");
        new Sql().executeUpdate(
                "INSERT INTO users (username, password_hash, full_name, email, is_active) "
                        + "VALUES ('finance', ?, 'Fiona Finance', 'finance@ams.local', 1) "
                        + "AS new "
                        + "ON DUPLICATE KEY UPDATE password_hash = new.password_hash, is_active = 1",
                hash);
        new Sql().executeUpdate(
                "INSERT IGNORE INTO user_roles (user_id, role_id) "
                        + "SELECT u.id, r.id FROM users u JOIN roles r "
                        + "WHERE u.username = 'finance' AND r.role_code = 'FINANCE'");
        System.out.println("provisioned dev account: finance / Finance@123 (FINANCE)");
    }

    /** Removes tool-created employees and their children (dev bypass). */
    private static void purgeArtifacts() {
        List<Long> ids = new Sql().list(
                "SELECT id FROM employees WHERE employee_code LIKE 'SMK-E%'",
                rs -> rs.getLong(1));
        for (Long id : ids) {
            new Sql().executeUpdate(
                    "DELETE FROM salary_structures WHERE employee_id = ?", id);
            new Sql().executeUpdate(
                    "DELETE FROM employee_history WHERE employee_id = ?", id);
            new Sql().executeUpdate(
                    "DELETE FROM leave_balances WHERE employee_id = ?", id);
            new Sql().executeUpdate(
                    "DELETE FROM employees WHERE id = ?", id);
        }
    }

    private static void check(String label, BooleanCheck action) {
        try {
            boolean passed = action.run();
            System.out.println((passed ? "OK   " : "FAIL ") + label);
            if (!passed) {
                failures++;
            }
        } catch (Exception e) {
            System.out.println("FAIL " + label + " -> unexpected "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            failures++;
        }
    }

    @FunctionalInterface
    private interface BooleanCheck {
        boolean run() throws Exception;
    }
}
