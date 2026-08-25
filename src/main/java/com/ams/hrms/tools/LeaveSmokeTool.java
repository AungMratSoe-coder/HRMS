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
import com.ams.hrms.model.EmployeeLeaveRequest;
import com.ams.hrms.repository.LeaveRepository.BalanceRow;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.LeaveService;

/**
 * Development-only Phase 12 verification against the live database:
 * request lifecycle, overlap rejection, insufficient-balance rejection,
 * approval moving pending→used, cancel-after-decision guard, RBAC denial.
 */
public final class LeaveSmokeTool {

    private static int failures;

    public static void main(String[] args) {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        LeaveService leaves = ServiceRegistry.leaveService();

        long empId = new Sql().scalarLong(
                "SELECT id FROM employees WHERE employee_code = 'EMP-0003'");
        LocalDate start = LocalDate.now().plusDays(30);
        LocalDate end = start.plusDays(4); // 5 calendar days

        authService.login("admin", "Admin@123");

        // Find seeded annual type id (after login: LEAVE_VIEW required).
        long annualTypeId = leaves.activeTypes().stream()
                .filter(type -> type.code().equals("ANNUAL"))
                .findFirst().orElseThrow().id();

        // --- request ----------------------------------------------------------
        EmployeeLeaveRequest request = request(empId, annualTypeId, start, end);
        check("submit request", () -> leaves.request(request) > 0);
        long requestId = request.getId();

        check("status PENDING", () -> leaves.findAll("", "PENDING", null).stream()
                .anyMatch(r -> r.getId() == requestId));

        check("pending balance incremented",
                () -> leaves.balances(empId, start.getYear()).stream()
                        .filter(bal -> bal.leaveTypeId() == annualTypeId)
                        .findFirst().orElseThrow()
                        .pending().compareTo(new BigDecimal(5)) == 0);

        // --- overlap -----------------------------------------------------------
        EmployeeLeaveRequest overlap = request(empId, annualTypeId,
                start.plusDays(2), end.plusDays(6));
        check("overlap rejected", () -> {
            try {
                leaves.request(overlap);
                return false;
            } catch (ValidationException expected) {
                return expected.getErrors().get(0).contains("overlap");
            }
        });

        // --- insufficient balance -------------------------------------------------
        EmployeeLeaveRequest excessive = request(empId, annualTypeId,
                start.plusDays(60), start.plusDays(90)); // 31 days > 18 entitled
        check("insufficient balance rejected",
                () -> {
                    try {
                        leaves.request(excessive);
                        return false;
                    } catch (ValidationException e) {
                        return e.getErrors().get(0).contains("Insufficient balance");
                    }
                });

        // --- approval ----------------------------------------------------------------
        check("HR approval moves pending→used", () -> {
            leaves.approve(requestId, "HR", null);
            BalanceRow row = leaves.balances(empId, start.getYear()).stream()
                    .filter(bal -> bal.leaveTypeId() == annualTypeId)
                    .findFirst().orElseThrow();
            return row.used().compareTo(new BigDecimal(5)) == 0
                    && row.pending().compareTo(BigDecimal.ZERO) == 0;
        });
        check("request APPROVED", () -> leaves.findAll("", "APPROVED", null).stream()
                .anyMatch(r -> r.getId() == requestId));

        // --- decision-after-final guard ----------------------------------------------
        check("approve-after-approval blocked",
                () -> {
                    try {
                        leaves.approve(requestId, "MANAGER", null);
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        // --- reject flow ----------------------------------------------------------------
        EmployeeLeaveRequest rejectable = request(empId, annualTypeId,
                start.plusDays(120), start.plusDays(122));
        leaves.request(rejectable);
        check("reject releases pending", () -> {
            leaves.reject(rejectable.getId(), "Operational coverage");
            var balances = leaves.balances(empId, start.getYear()).stream()
                    .filter(bal -> bal.leaveTypeId() == annualTypeId)
                    .findFirst().orElseThrow();
            return balances.pending().compareTo(BigDecimal.ZERO) == 0
                    && leaves.findAll("", "REJECTED", null).stream()
                            .anyMatch(r -> r.getId() == rejectable.getId());
        });

        // --- RBAC: FINANCE cannot submit ---------------------------------------------------
        authService.logout();
        authService.login("finance", "Finance@123");
        check("finance user denied leave request at service gate",
                () -> {
                    try {
                        EmployeeLeaveRequest attempt =
                                request(empIdByCode("EMP-0004"), annualTypeId,
                                        LocalDate.now().plusDays(200),
                                        LocalDate.now().plusDays(201));
                        leaves.request(attempt);
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        authService.logout();

        // --- cleanup ---------------------------------------------------------------------------
        authService.login("admin", "Admin@123");
        purgeArtifacts();
        System.out.println("cleanup: SMOKE leave requests removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    /** Smoke requests are tagged by their reason for easy purging. */
    private static EmployeeLeaveRequest request(long employeeId, long typeId,
                                                LocalDate start, LocalDate end) {
        EmployeeLeaveRequest request = new EmployeeLeaveRequest();
        request.setEmployeeId(employeeId);
        request.setLeaveTypeId(typeId);
        request.setStartDate(start);
        request.setEndDate(end);
        request.setReason("SMOKE-TEST leave");
        return request;
    }

    private static long empIdByCode(String code) {
        return new Sql().scalarLong("SELECT id FROM employees WHERE employee_code = ?", code);
    }

    /** Removes smoke requests, approvals and resets the affected balances. */
    private static void purgeArtifacts() {
        new Sql().executeUpdate(
                "DELETE la FROM leave_approvals la JOIN leave_requests lr "
                        + "ON lr.id = la.leave_request_id WHERE lr.reason LIKE 'SMOKE%'");
        new Sql().executeUpdate(
                "UPDATE leave_balances lb JOIN leave_requests lr "
                        + "ON lr.employee_id = lb.employee_id "
                        + "AND lr.leave_type_id = lb.leave_type_id "
                        + "AND YEAR(lr.start_date) = lb.balance_year "
                        + "SET lb.used = 0, lb.pending = 0 WHERE lr.reason LIKE 'SMOKE%'");
        new Sql().executeUpdate(
                "DELETE FROM leave_requests WHERE reason LIKE 'SMOKE%'");
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
