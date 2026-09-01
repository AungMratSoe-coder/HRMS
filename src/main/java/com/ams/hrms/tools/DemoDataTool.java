package com.ams.hrms.tools;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.model.OvertimeRequest;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.OvertimeService;

/**
 * Opt-in demo data seeder (development/demo databases only - never runs
 * automatically). Fills the workflow modules that start empty by design:
 * overtime (pending/approved/rejected), leave requests (pending/approved)
 * and one resignation awaiting approval. Rows are marked (reason/code
 * prefixes "DEMO"/"LV-D"/"RSG-D") and re-running the tool replaces them.
 *
 * Run the same way as the other tools in this package.
 */
public final class DemoDataTool {

    private DemoDataTool() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        authService.login("admin@ams.local", "Admin@123");

        purgeDemoData();

        long adminId = new Sql().scalarLong(
                "SELECT id FROM users WHERE username = 'admin'");
        LocalDate today = LocalDate.now();

        // --- Overtime: one request per decision state -----------------------
        OvertimeService overtime = ServiceRegistry.overtimeService();

        OvertimeRequest pending = overtimeRequest("EMP-0003", today.minusDays(1), "2",
                "DEMO overtime - awaiting manager decision");
        overtime.request(pending);

        OvertimeRequest approved = overtimeRequest("EMP-0004", today.minusDays(3), "3",
                "DEMO overtime - month-end release deployment");
        overtime.request(approved);
        overtime.approve(approved.getId());

        OvertimeRequest rejected = overtimeRequest("EMP-0005", today.minusDays(2), "1",
                "DEMO overtime - rejected (not pre-approved)");
        overtime.request(rejected);
        overtime.reject(rejected.getId());

        // --- Leave: direct inserts; the balance ledger is kept in sync so
        // the pending rows are approvable (see ledgerContribution) --------
        long annualType = new Sql().scalarLong(
                "SELECT id FROM leave_types WHERE status = 'ACTIVE' ORDER BY id LIMIT 1");
        long secondType = new Sql().scalarLong(
                "SELECT id FROM leave_types WHERE status = 'ACTIVE' ORDER BY id LIMIT 1, 1");
        if (secondType == 0) {
            secondType = annualType;
        }

        insertLeave("LV-D001", "EMP-0003", annualType,
                today.plusDays(3), today.plusDays(4), "2.0", "DEMO annual leave - family trip",
                "PENDING", null);
        insertLeave("LV-D002", "EMP-0004", secondType,
                today.minusDays(1), today.minusDays(1), "1.0", "DEMO sick leave",
                "APPROVED", adminId);
        insertLeave("LV-D003", "EMP-0005", annualType,
                today.plusDays(10), today.plusDays(10), "0.5", "DEMO half-day leave",
                "PENDING", null);

        // --- Separation: one resignation awaiting approval -------------------
        long emp5 = employeeId("EMP-0005");
        new Sql().executeInsert(
                "INSERT INTO resignations (resignation_code, employee_id, resignation_date, "
                        + "last_working_date, notice_period_days, reason, status) "
                        + "VALUES ('RSG-D001', ?, ?, ?, 30, ?, 'SUBMITTED')",
                emp5, today.minusDays(7), today.plusDays(23),
                "DEMO resignation - pursuing further education");

        System.out.println("Demo data created:");
        System.out.println("  overtime_requests : 3 (PENDING / APPROVED / REJECTED)");
        System.out.println("  leave_requests    : 3 (2 x PENDING, 1 x APPROVED)");
        System.out.println("  resignations      : 1 (SUBMITTED - ready for the exit workflow)");
        System.out.println("Re-run this tool to replace them; "
                + "purge by deleting DEMO/LV-D/RSG-D marked rows.");

        authService.logout();
        DatabaseConfig.close();
        System.exit(0);
    }

    private static OvertimeRequest overtimeRequest(String code, LocalDate date,
            String hours, String reason) {
        OvertimeRequest request = new OvertimeRequest();
        request.setEmployeeId(employeeId(code));
        request.setRequestDate(date);
        request.setHours(new BigDecimal(hours));
        request.setReason(reason);
        return request;
    }

    private static void insertLeave(String leaveCode, String employeeCode, long typeId,
            LocalDate start, LocalDate end, String days, String reason,
            String status, Long decidedBy) {
        long empId = employeeId(employeeCode);
        new Sql().executeInsert(
                "INSERT INTO leave_requests (leave_code, employee_id, leave_type_id, "
                        + "start_date, end_date, number_of_days, reason, status, "
                        + "decided_by, decided_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, "
                        + (decidedBy == null ? "NULL)" : "CURRENT_TIMESTAMP)"),
                leaveCode, empId, typeId, start, end,
                new BigDecimal(days), reason, status, decidedBy);
        ledgerContribution(empId, typeId, start.getYear(), new BigDecimal(days),
                "PENDING".equals(status));
    }

    /**
     * Mirrors the LeaveService ledger: a PENDING request holds its days in
     * {@code pending}, an APPROVED one counts them in {@code used}. Without
     * this, approving a demo request would drive {@code pending} below zero
     * and trip the chk_lb_nonnegative constraint.
     */
    private static void ledgerContribution(long employeeId, long typeId, int year,
            BigDecimal days, boolean pending) {
        long existing = new Sql().scalarLong(
                "SELECT COUNT(*) FROM leave_balances "
                        + "WHERE employee_id = ? AND leave_type_id = ? AND balance_year = ?",
                employeeId, typeId, year);
        if (existing == 0) {
            new Sql().executeInsert(
                    "INSERT INTO leave_balances (employee_id, leave_type_id, balance_year, entitled) "
                            + "SELECT ?, ?, ?, lt.annual_quota FROM leave_types lt WHERE lt.id = ?",
                    employeeId, typeId, year, typeId);
        }
        String column = pending ? "pending" : "used";
        new Sql().executeUpdate(
                "UPDATE leave_balances SET " + column + " = " + column + " + ? "
                        + "WHERE employee_id = ? AND leave_type_id = ? AND balance_year = ?",
                days, employeeId, typeId, year);
    }

    private static long employeeId(String code) {
        long id = new Sql().scalarLong(
                "SELECT id FROM employees WHERE employee_code = ?", code);
        if (id == 0) {
            throw new IllegalStateException("Demo data needs employee " + code
                    + " (seeded in V2). Run against the seeded database.");
        }
        return id;
    }

    /**
     * Removes previously created demo rows so the tool is re-runnable. The
     * leave rows' ledger contributions are released first (PENDING held in
     * {@code pending}, APPROVED in {@code used}) so re-running never
     * double-counts; GREATEST guards against pre-existing drift.
     */
    private static void purgeDemoData() {
        new Sql().executeUpdate(
                "UPDATE leave_balances lb JOIN leave_requests lr "
                        + "ON lr.employee_id = lb.employee_id "
                        + "AND lr.leave_type_id = lb.leave_type_id "
                        + "AND lb.balance_year = YEAR(lr.start_date) "
                        + "SET lb.pending = GREATEST(lb.pending - lr.number_of_days, 0) "
                        + "WHERE lr.leave_code LIKE 'LV-D%' AND lr.status = 'PENDING'");
        new Sql().executeUpdate(
                "UPDATE leave_balances lb JOIN leave_requests lr "
                        + "ON lr.employee_id = lb.employee_id "
                        + "AND lr.leave_type_id = lb.leave_type_id "
                        + "AND lb.balance_year = YEAR(lr.start_date) "
                        + "SET lb.used = GREATEST(lb.used - lr.number_of_days, 0) "
                        + "WHERE lr.leave_code LIKE 'LV-D%' AND lr.status = 'APPROVED'");
        new Sql().executeUpdate(
                "DELETE FROM overtime_requests WHERE reason LIKE 'DEMO%'");
        new Sql().executeUpdate(
                "DELETE FROM leave_requests WHERE leave_code LIKE 'LV-D%'");
        new Sql().executeUpdate(
                "DELETE FROM resignations WHERE resignation_code LIKE 'RSG-D%'");
    }
}
