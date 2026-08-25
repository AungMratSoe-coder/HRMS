package com.ams.hrms.tools;

import java.time.LocalDate;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.repository.AuditRepository.AuditRow;
import com.ams.hrms.repository.AuditRepository.Filter;
import com.ams.hrms.service.AuditService;
import com.ams.hrms.service.AuthService;

/**
 * Development-only Phase 24 verification against the live database:
 * keyword/action/module/user/date filtering, server-side page limits,
 * friendly range validation, RBAC denial for FINANCE, and the append-only
 * guarantee (the smoke entry intentionally remains - audit rows are never
 * deleted by application code).
 */
public final class AuditLogSmokeTool {

    private static int failures;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        AuditService audits = ServiceRegistry.auditService();

        authService.login("admin", "Admin@123");

        String marker = "SMOKE-AUDIT-" + System.currentTimeMillis();
        audits.record("SMOKE_ACTION", "SMOKE", "SmokeEntity", 123L,
                marker + " written by the audit smoke tool");
        long smokeUserId = com.ams.hrms.security.SessionContext.currentUserId();

        // --- keyword search ---------------------------------------------------
        Filter byKeyword = new Filter(marker, "", "", null, null, null);
        check("keyword search finds the smoke entry",
                () -> matches(audits.search(byKeyword, 0, 50), marker));

        check("countMatching agrees with search", () ->
                audits.countMatching(byKeyword) == 1);

        // --- exact action / module / user filters ------------------------------
        check("action+module filter finds it", () -> matches(
                audits.search(new Filter("", "SMOKE_ACTION", "SMOKE", null, null, null),
                        0, 50), marker));

        check("user filter narrows to the acting account", () -> {
            var rows = audits.search(new Filter("", "SMOKE_ACTION", "", smokeUserId, null, null),
                    0, 50);
            return matches(rows, marker)
                    && rows.stream().allMatch(row -> row.userId() != null
                            && row.userId() == smokeUserId);
        });

        check("unknown module yields zero rows", () -> audits.countMatching(
                new Filter("", "", "NO_SUCH_MODULE_XYZ", null, null, null)) == 0);

        // --- inclusive date range ----------------------------------------------
        LocalDate today = LocalDate.now();
        check("same-day range is inclusive on both ends", () -> matches(
                audits.search(new Filter(marker, "", "", null, today, today), 0, 50), marker));
        check("range ending yesterday misses it", () -> audits.countMatching(
                new Filter(marker, "", "", null, null, today.minusDays(1))) == 0);

        // --- validation ---------------------------------------------------------
        check("inverted range rejected with a friendly error", () -> {
            try {
                audits.countMatching(new Filter("", "", "", null, today, today.minusDays(1)));
                return false;
            } catch (ValidationException expected) {
                return expected.getErrors().get(0).contains("from date");
            }
        });

        // --- server-side pagination ----------------------------------------------
        check("page limit is respected", () ->
                audits.search(Filter.empty(), 0, 5).size() <= 5);
        check("offset skips the newest entries", () -> {
            var firstPage = audits.search(Filter.empty(), 0, 1);
            var secondPage = audits.search(Filter.empty(), 1, 1);
            return firstPage.size() == 1 && secondPage.size() == 1
                    && firstPage.get(0).id() > secondPage.get(0).id();
        });
        check("rows are ordered newest-first", () -> {
            var rows = audits.search(Filter.empty(), 0, 25);
            for (int i = 1; i < rows.size(); i++) {
                if (rows.get(i - 1).id() < rows.get(i).id()) {
                    return false;
                }
            }
            return true;
        });

        // --- RBAC: FINANCE lacks AUDIT_LOG_VIEW ------------------------------------
        authService.logout();
        authService.login("finance", "Finance@123");
        check("finance user denied audit queries at service gate", () -> {
            try {
                audits.distinctModules();
                return false;
            } catch (AuthorizationException expected) {
                return true;
            }
        });
        authService.logout();

        // --- append-only guarantee ---------------------------------------------------
        authService.login("admin", "Admin@123");
        check("smoke entry remains (trail is immutable)", () ->
                matches(audits.search(byKeyword, 0, 50), marker));
        System.out.println("note: SMOKE-AUDIT entries persist by design (append-only trail)");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    private static boolean matches(java.util.List<AuditRow> rows, String marker) {
        return rows.stream().anyMatch(row ->
                row.description() != null && row.description().contains(marker));
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
