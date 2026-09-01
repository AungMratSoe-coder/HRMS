package com.ams.hrms.tools;

import java.time.LocalDate;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.repository.AuditRepository;
import com.ams.hrms.repository.EmployeeRepository;
import com.ams.hrms.repository.PayrollRepository;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.PayrollService;

/**
 * Development-only Phase 14 verification against the live database:
 * calculates payroll for all active employees, verifies math and lifecycle
 * transitions, and tests RBAC denial for a restricted user.
 */
public final class PayrollSmokeTool {

    private static int failures;

    public static void main(String[] args) {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        var auditService = new com.ams.hrms.service.AuditService(new AuditRepository());
        var payrollService = new PayrollService(
                new PayrollRepository(),
                auditService,
                new com.ams.hrms.service.EmployeeService(
                        new EmployeeRepository(),
                        new com.ams.hrms.repository.PositionRepository(), auditService));

        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        String monthStr = String.format("%d-%02d", year, month);

        authService.login("admin@ams.local", "Admin@123");

        // --- calculate ---------------------------------------------------------
        check("calculate payroll for " + monthStr,
                () -> {
                    int count = payrollService.calculate(year, month);
                    System.out.println("  calculated: " + count + " record(s)");
                    return count > 0;
                });

        long periodId = new Sql().scalarLong(
                "SELECT id FROM payroll_periods WHERE period_year = ? AND period_month = ?",
                year, month);

        check("payroll records exist",
                () -> new Sql().scalarLong(
                        "SELECT COUNT(*) FROM payrolls WHERE payroll_period_id = ? AND status = 'CALCULATED'",
                        periodId) > 0);

        check("gross >= basic for all records",
                () -> new Sql().scalarLong(
                        "SELECT COUNT(*) FROM payrolls WHERE payroll_period_id = ? "
                                + "AND gross_salary < basic_salary", periodId) == 0);

        check("net < gross for all records (tax+SS deducted)",
                () -> new Sql().scalarLong(
                        "SELECT COUNT(*) FROM payrolls WHERE payroll_period_id = ? "
                                + "AND net_salary >= gross_salary AND gross_salary > 0",
                        periodId) == 0);

        // --- lifecycle ---------------------------------------------------------
        check("bulk review CALCULATEDÃ¢â€ â€™REVIEWED", () -> {
            payrollService.transitionPeriod(periodId, "CALCULATED", "REVIEWED");
            return true;
        });
        check("all REVIEWED after bulk", () -> new Sql().scalarLong(
                "SELECT COUNT(*) FROM payrolls WHERE payroll_period_id = ? "
                        + "AND status != 'REVIEWED'", periodId) == 0);

        check("bulk approve REVIEWEDÃ¢â€ â€™APPROVED", () -> {
            payrollService.transitionPeriod(periodId, "REVIEWED", "APPROVED");
            return true;
        });
        check("period LOCKED after approval",
                () -> new Sql().first(
                        "SELECT status FROM payroll_periods WHERE id = ?",
                        rs -> rs.getString(1), periodId)
                        .orElse("").equals("LOCKED"));

        // --- RBAC: EMPLOYEE role denied -------------------------------------------
        SessionContext.clear();
        SessionContext.login(
                new SessionContext.AuthenticatedUser(99L, "fake-emp", "Fake Employee",
                        null, null, null, false),
                java.util.Set.of(new SessionContext.RoleRef("EMPLOYEE", "Employee")),
                java.util.Set.of("LEAVE_REQUEST"));
        check("employee denied PAYROLL_CALCULATE at service gate",
                () -> {
                    try {
                        payrollService.calculate(year, month);
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        SessionContext.clear();

        // --- cleanup -----------------------------------------------------------------
        authService.login("admin@ams.local", "Admin@123");
        new Sql().executeUpdate(
                "DELETE FROM payroll_items WHERE payroll_id IN "
                        + "(SELECT id FROM payrolls WHERE payroll_period_id = ?)", periodId);
        new Sql().executeUpdate(
                "DELETE FROM payrolls WHERE payroll_period_id = ?", periodId);
        new Sql().executeUpdate(
                "UPDATE overtime_requests SET status = 'APPROVED' WHERE status = 'PAID'");
        System.out.println("cleanup: payroll data removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
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
