package com.ams.hrms.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.repository.AuditRepository;
import com.ams.hrms.repository.EmployeeRepository;
import com.ams.hrms.repository.PayrollRepository;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.PayslipService;
import com.ams.hrms.service.PayrollService;

/** Development-only Phase 15 verification: generates payslip PDFs and checks output. */
public final class PayslipSmokeTool {

    private static int failures;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        var auditService = new com.ams.hrms.service.AuditService(new AuditRepository());
        var employeeService = new com.ams.hrms.service.EmployeeService(
                new EmployeeRepository(), new com.ams.hrms.repository.PositionRepository(),
                auditService);
        var payrollService = new PayrollService(
                new PayrollRepository(),
                auditService,
                employeeService);
        var payslipService = new PayslipService(new PayrollRepository());

        LocalDate now = LocalDate.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        authService.login("admin", "Admin@123");

        // Calculate to ensure records exist
        try {
            payrollService.calculate(year, month);
        } catch (Exception ignored) {
        }

        long periodId = new Sql().scalarLong(
                "SELECT id FROM payroll_periods WHERE period_year = ? AND period_month = ?",
                year, month);

        // Get first APPROVED or CALCULATED payroll id
        Long payrollId = new Sql().first(
                "SELECT id FROM payrolls WHERE payroll_period_id = ? LIMIT 1",
                rs -> rs.getLong(1), periodId).orElse(null);

        if (payrollId == null) {
            System.out.println("FAIL no payroll record found for period");
            failures++;
            DatabaseConfig.close();
            System.exit(1);
            return;
        }

        Path tempDir = Files.createTempDirectory("smoke-payslip-");

        check("generate payslip PDF", () -> {
            Path result = payslipService.generatePayslip(payrollId, tempDir);
            return Files.exists(result) && Files.size(result) > 1000;
        });

        // Verify it's a valid PDF by checking the magic bytes
        check("output is a valid PDF", () -> {
            Path pdfFile = Files.list(tempDir).findFirst().orElseThrow();
            byte[] header = new byte[5];
            try (var is = Files.newInputStream(pdfFile)) {
                is.read(header);
            }
            return new String(header).equals("%PDF-");
        });

        check("PDF size > 1KB (non-trivial content)", () -> {
            Path pdfFile = Files.list(tempDir).findFirst().orElseThrow();
            return Files.size(pdfFile) > 1024;
        });

        // --- RBAC: EMPLOYEE role denied -------------------------------------------
        SessionContext.clear();
        SessionContext.login(
                new SessionContext.AuthenticatedUser(99L, "fake-emp", "Fake Employee",
                        null, null, null, false),
                java.util.Set.of(new SessionContext.RoleRef("EMPLOYEE", "Employee")),
                java.util.Set.of("LEAVE_REQUEST"));
        check("employee denied PAYSLIP_GENERATE at service gate",
                () -> {
                    try {
                        payslipService.generatePayslip(payrollId, tempDir);
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        SessionContext.clear();

        // cleanup
        try (var paths = Files.list(tempDir)) {
            paths.forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
        Files.deleteIfExists(tempDir);

        authService.login("admin", "Admin@123");

        // Cleanup payroll data
        new Sql().executeUpdate(
                "DELETE FROM payroll_items WHERE payroll_id IN "
                        + "(SELECT id FROM payrolls WHERE payroll_period_id = ?)", periodId);
        new Sql().executeUpdate(
                "DELETE FROM payrolls WHERE payroll_period_id = ?", periodId);
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
