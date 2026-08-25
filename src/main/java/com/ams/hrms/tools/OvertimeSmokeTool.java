package com.ams.hrms.tools;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.OvertimeRequest;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.OvertimeService;

/**
 * Development-only Phase 13 verification against the live database:
 * request lifecycle, rate/amount math from settings (salary 1500 / 22 days /
 * 8h × 1.5 = 12.78/h), decision guards and RBAC denial for FINANCE.
 */
public final class OvertimeSmokeTool {

    private static int failures;

    public static void main(String[] args) {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        purgeArtifacts();

        AuthService authService = ServiceRegistry.authService();
        OvertimeService overtime = ServiceRegistry.overtimeService();

        authService.login("admin", "Admin@123");
        long empId = new Sql().scalarLong(
                "SELECT id FROM employees WHERE employee_code = 'EMP-0003'");
        LocalDate date = LocalDate.now().minusDays(1);

        // --- submit ---------------------------------------------------------
        OvertimeRequest request = new OvertimeRequest();
        request.setEmployeeId(empId);
        request.setRequestDate(date);
        request.setHours(new BigDecimal("2"));
        request.setReason("SMOKE-TEST overtime");
        check("submit overtime request", () -> overtime.request(request) > 0);
        long requestId = request.getId();

        check("status PENDING with no rate yet",
                () -> {
                    var saved = overtime.findAll("EMP-0003", "PENDING").stream()
                            .filter(r -> r.getId() == requestId)
                            .findFirst().orElseThrow();
                    return saved.getRatePerHour() == null && saved.getAmount() == null;
                });

        check("reject hours > 12",
                () -> {
                    try {
                        var bad = new OvertimeRequest();
                        bad.setEmployeeId(empId);
                        bad.setRequestDate(date.plusDays(1));
                        bad.setHours(new BigDecimal(13));
                        bad.setReason("SMOKE-TEST too many hours");
                        overtime.request(bad);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        // --- approval + math ----------------------------------------------
        // EMP-0003 salary 1500; hourly base = 1500/22/8 = 8.52; rate = 12.78
        check("approve computes rate and amount",
                () -> {
                    overtime.approve(requestId);
                    var approved = overtime.findAll("EMP-0003", "APPROVED").stream()
                            .filter(r -> r.getId() == requestId).findFirst().orElseThrow();
                    return approved.getRatePerHour().compareTo(new BigDecimal("12.78")) == 0
                            && approved.getAmount().compareTo(new BigDecimal("25.56")) == 0;
                });

        check("approve-after-approval blocked",
                () -> {
                    try {
                        overtime.approve(requestId);
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        // --- rejection flow ----------------------------------------------------
        OvertimeRequest rejectable = new OvertimeRequest();
        rejectable.setEmployeeId(empId);
        rejectable.setRequestDate(date.minusDays(1));
        rejectable.setHours(new BigDecimal("1"));
        rejectable.setReason("SMOKE-TEST rejectable");
        long rejectableId = overtime.request(rejectable);
        check("reject pending request", () -> {
            overtime.reject(rejectableId);
            return true;
        });
        String rejectStatus = new Sql().first(
                "SELECT status FROM overtime_requests WHERE id = ?",
                rs -> rs.getString("status"), rejectableId).orElse("");
        System.out.println(("REJECTED".equals(rejectStatus) ? "OK   " : "FAIL ")
                + "rejected status persisted");
        if (!"REJECTED".equals(rejectStatus)) {
            failures++;
        }

        // --- RBAC: FINANCE cannot submit/approve ---------------------------------
        authService.logout();
        authService.login("finance", "Finance@123");
        check("finance user denied overtime submission at service gate",
                () -> {
                    try {
                        OvertimeRequest attempt = new OvertimeRequest();
                        attempt.setEmployeeId(empIdByCode("EMP-0004"));
                        attempt.setRequestDate(LocalDate.now());
                        attempt.setHours(new BigDecimal("1"));
                        attempt.setReason("SMOKE-TEST finance");
                        overtime.request(attempt);
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        authService.logout();

        // --- cleanup ------------------------------------------------------------------
        authService.login("admin", "Admin@123");
        purgeArtifacts();
        System.out.println("cleanup: SMOKE overtime requests removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    private static long empIdByCode(String code) {
        return new Sql().scalarLong("SELECT id FROM employees WHERE employee_code = ?", code);
    }

    /** Removes smoke-created overtime rows. */
    private static void purgeArtifacts() {
        new Sql().executeUpdate(
                "DELETE FROM overtime_requests WHERE reason LIKE 'SMOKE%'");
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
