package com.ams.hrms.tools;

import java.time.LocalDate;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.report.ReportDefinition;
import com.ams.hrms.report.ReportFilter;
import com.ams.hrms.report.ReportResult;
import com.ams.hrms.repository.AuditRepository;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.service.AuditService;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.ReportService;

/**
 * Development-only Phase 22 verification against the live database:
 * report catalog RBAC, generation of representative reports (filters,
 * totals rows), filter validation rules, PDF/Excel export payloads,
 * export auditing and export permission denial.
 */
public final class ReportSmokeTool {

    private static int failures;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        var reportRepository = new com.ams.hrms.repository.ReportRepository();
        var reportService = new ReportService(reportRepository,
                new AuditService(new AuditRepository()));

        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);

        authService.login("admin", "Admin@123");

        // --- catalog -------------------------------------------------------------
        check("catalog lists every report definition", () ->
                reportService.catalog().size() == ReportDefinition.values().length);

        // --- generation ----------------------------------------------------------
        ReportResult employees = reportService.generate(
                ReportDefinition.EMPLOYEE_LIST, ReportFilter.between(null, null));
        check("employee list returns columns matching metadata", () ->
                employees.columns().size() == ReportDefinition.EMPLOYEE_LIST.columns().length);

        ReportResult activeOnly = reportService.generate(
                ReportDefinition.EMPLOYEE_LIST,
                new ReportFilter(null, null, null, null, null, "ACTIVE"));
        check("status filter keeps only matching rows", () -> activeOnly.rows().stream()
                .allMatch(row -> "ACTIVE".equals(row[7])));

        ReportResult departments = reportService.generate(
                ReportDefinition.DEPARTMENT_REPORT, ReportFilter.between(null, null));
        check("department report carries totals row when data exists",
                () -> departments.hasData() ? departments.totalsRow() != null : true);

        ReportResult attendance = reportService.generate(
                ReportDefinition.ATTENDANCE_SUMMARY,
                ReportFilter.between(today.minusDays(30), today));
        check("attendance summary accepts 30-day range", () ->
                attendance.title().equals(ReportDefinition.ATTENDANCE_SUMMARY.title()));

        // --- validation ------------------------------------------------------------
        check("range-bound report rejects missing dates",
                () -> rejected(() -> reportService.generate(
                        ReportDefinition.ATTENDANCE_SUMMARY, ReportFilter.between(null, null))));
        check("range-bound report rejects >366-day span",
                () -> rejected(() -> reportService.generate(
                        ReportDefinition.LATE_REPORT,
                        ReportFilter.between(today.minusDays(400), today))));
        check("unknown status value rejected",
                () -> rejected(() -> reportService.generate(
                        ReportDefinition.EMPLOYEE_LIST,
                        new ReportFilter(null, null, null, null, null, "NOT_A_STATUS"))));
        check("keyword on non-keyword report rejected",
                () -> rejected(() -> reportService.generate(
                        ReportDefinition.TURNOVER_REPORT,
                        new ReportFilter(monthStart, today, null, null, "emp", null))));

        // --- exports -----------------------------------------------------------------
        byte[] pdf = reportService.exportPdf(activeOnly);
        check("PDF export starts with %PDF- magic",
                () -> pdf.length > 1000
                        && pdf[0] == '%' && pdf[1] == 'P' && pdf[2] == 'D' && pdf[3] == 'F');
        byte[] excel = reportService.exportExcel(departments.hasData()
                ? departments : activeOnly);
        check("Excel export starts with ZIP (PK) magic",
                () -> excel.length > 1000 && excel[0] == 'P' && excel[1] == 'K');

        check("exports audited", () -> new Sql().scalarLong(
                "SELECT COUNT(*) FROM audit_logs WHERE module = 'REPORTS' "
                        + "AND action IN ('EXPORT_PDF', 'EXPORT_EXCEL')") >= 2);

        // --- RBAC ----------------------------------------------------------------------
        SessionContext.clear();
        SessionContext.login(
                new SessionContext.AuthenticatedUser(99L, "fake-emp", "Fake Employee",
                        null, null, null, false),
                java.util.Set.of(new SessionContext.RoleRef("EMPLOYEE", "Employee")),
                java.util.Set.of("LEAVE_REQUEST"));
        check("user without REPORT_VIEW cannot generate",
                () -> denied(() -> reportService.generate(
                        ReportDefinition.EMPLOYEE_LIST, ReportFilter.between(null, null))));

        SessionContext.clear();
        SessionContext.login(
                new SessionContext.AuthenticatedUser(98L, "fake-viewer", "Fake Viewer",
                        null, null, null, false),
                java.util.Set.of(new SessionContext.RoleRef("VIEWER", "Viewer")),
                java.util.Set.of("REPORT_VIEW"));
        check("REPORT_VIEW holder can generate",
                () -> {
                    try {
                        reportService.generate(ReportDefinition.EMPLOYEE_LIST,
                                ReportFilter.between(null, null));
                        return true;
                    } catch (AuthorizationException unexpected) {
                        return false;
                    }
                });
        check("REPORT_EXPORT required for PDF export",
                () -> denied(() -> reportService.exportPdf(activeOnly)));
        SessionContext.clear();

        authService.login("admin", "Admin@123");
        System.out.println("note: audit_logs rows are append-only and were left in place");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    /** True when the call throws {@link ValidationException}. */
    private static boolean rejected(ThrowingCall call) {
        try {
            call.run();
            return false;
        } catch (ValidationException expected) {
            return true;
        } catch (Exception other) {
            return false;
        }
    }

    /** True when the call throws {@link AuthorizationException}. */
    private static boolean denied(ThrowingCall call) {
        try {
            call.run();
            return false;
        } catch (AuthorizationException expected) {
            return true;
        } catch (Exception other) {
            return false;
        }
    }

    @FunctionalInterface
    private interface ThrowingCall {
        void run() throws Exception;
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
