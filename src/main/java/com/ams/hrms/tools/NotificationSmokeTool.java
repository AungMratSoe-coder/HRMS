package com.ams.hrms.tools;

import java.time.LocalDate;
import java.util.List;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Notification;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.NotificationRules;
import com.ams.hrms.service.NotificationService;
import com.ams.hrms.service.NotificationService.ScanSummary;

/**
 * Development-only Phase 23 verification against the live database:
 * validation gates, personal feed and read-state, event-driven fan-out
 * (leave requested / payroll processed), operational-scan idempotency and
 * cleanup of every row created during the run.
 */
public final class NotificationSmokeTool {

    private static final long FAKE_LEAVE_REQUEST_ID = 9_000_000_001L;

    private static int failures;
    private static long baselineId;
    private static long adminUserId;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        NotificationService notifications = ServiceRegistry.notificationService();

        purgeArtifacts();
        authService.login("admin", "Admin@123");
        adminUserId = new Sql().first(
                "SELECT id FROM users WHERE username = 'admin'",
                rs -> rs.getLong(1)).orElseThrow();
        baselineId = new Sql().scalarLong("SELECT COALESCE(MAX(id), 0) FROM notifications");

        // --- validation -----------------------------------------------------
        check("reject blank title", () -> {
            try {
                notifications.notifyUser(adminUserId, Notification.TYPE_INFO,
                        "   ", "message", null, null);
                return false;
            } catch (ValidationException expected) {
                return expected.getErrors().get(0).contains("Title");
            }
        });

        check("reject unknown notification type", () -> {
            try {
                notifications.notifyUser(adminUserId, "MAGIC",
                        "SMOKE title", "message", null, null);
                return false;
            } catch (ValidationException expected) {
                return expected.getErrors().get(0).contains("type");
            }
        });

        check("reject oversized message", () -> {
            try {
                notifications.notifyUser(adminUserId, Notification.TYPE_INFO,
                        "SMOKE title", "x".repeat(NotificationRules.MESSAGE_MAX + 1),
                        null, null);
                return false;
            } catch (ValidationException expected) {
                return true;
            }
        });

        // --- targeted create + feed + read-state -----------------------------
        long unreadBefore = notifications.unreadCount();
        notifications.notifyUser(adminUserId, Notification.TYPE_INFO,
                "SMOKE targeted notice", "Created directly by the smoke tool.", "SMOKE", null);
        check("unread count increased by one",
                () -> notifications.unreadCount() == unreadBefore + 1);

        List<Notification> unreadFeed = notifications.list(true);
        Notification created = unreadFeed.stream()
                .filter(n -> n.getTitle().startsWith("SMOKE"))
                .findFirst().orElse(null);
        check("created notification appears in unread feed", () -> created != null);

        long createdId = created == null ? -1 : created.getId();
        boolean firstMark = createdId > 0 && notifications.markRead(createdId);
        boolean secondMark = createdId > 0 && notifications.markRead(createdId);
        check("markRead is effective once", () -> firstMark && !secondMark);

        // --- event-driven: leave request -> LEAVE_APPROVE holders -------------
        EventBus.publish(new Events.LeaveRequested(
                FAKE_LEAVE_REQUEST_ID, "SMOKE-LR-0001", employeeIdForSmoke(),
                "SMOKE Requester"));
        check("leave-requested event produced an approver notification",
                () -> awaitUntil(() -> existsNotification(
                        "reference_module = 'LEAVE_REQUEST' AND reference_id = "
                                + FAKE_LEAVE_REQUEST_ID)));

        // --- event-driven: payroll calculated -> PAYROLL_VIEW holders --------
        EventBus.publish(new Events.PayrollProcessed("CALCULATED", "SMOKE-2026-08", 12));
        check("payroll-processed event produced a payroll notification",
                () -> awaitUntil(() -> existsNotification(
                        "reference_module = 'PAYROLL_CALCULATED' AND title LIKE 'Payroll calculated%'")));

        // --- operational scan with seeded fixtures -----------------------------
        long fixtureEmployeeId = employeeIdForSmoke();
        long fixtureDocumentId = seedExpiringDocument(fixtureEmployeeId);
        String originalBirthDate = new Sql().first(
                "SELECT DATE_FORMAT(date_of_birth, '%Y-%m-%d') FROM employees WHERE id = ?",
                rs -> rs.getString(1), fixtureEmployeeId).orElse(null);
        seedBirthday(fixtureEmployeeId);
        try {
            ScanSummary firstScan = notifications.runOperationalScan(LocalDate.now());
            System.out.println("scan #1 generated " + firstScan.total() + " item(s) " + firstScan);
            check("scan generates expiring-document alert", () -> firstScan.documentAlerts() >= 1);
            check("scan generates birthday notice", () -> firstScan.birthdayNotices() >= 1);

            ScanSummary secondScan = notifications.runOperationalScan(LocalDate.now());
            System.out.println("scan #2 generated " + secondScan.total() + " item(s) " + secondScan);
            check("second scan generates nothing new (dedup)", () -> secondScan.total() == 0);
        } finally {
            restoreBirthday(fixtureEmployeeId, originalBirthDate);
            removeFixtureDocument(fixtureDocumentId);
        }
        check("fixture employee birthday restored", () -> originalBirthDate != null
                && new Sql().first(
                        "SELECT DATE_FORMAT(date_of_birth, '%Y-%m-%d') FROM employees WHERE id = ?",
                        rs -> rs.getString(1), fixtureEmployeeId)
                        .map(originalBirthDate::equals)
                        .orElse(false));
        check("fixture document removed", () -> !existsNotification("1 = 0")
                && new Sql().scalarLong(
                        "SELECT COUNT(*) FROM employee_documents WHERE id = ?", fixtureDocumentId) == 0);

        // --- housekeeping ------------------------------------------------------
        authService.logout();
        purgeArtifacts();
        System.out.println("cleanup: smoke notification rows removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    /** Any active employee id; used only as event payload, never modified. */
    private static long employeeIdForSmoke() {
        return new Sql().first(
                "SELECT id FROM employees WHERE status = 'ACTIVE' ORDER BY id LIMIT 1",
                rs -> rs.getLong(1)).orElse(0L);
    }

    /** Temporary ACTIVE document expiring within the warning window. */
    private static long seedExpiringDocument(long employeeId) {
        return new Sql().executeInsert(
                "INSERT INTO employee_documents (employee_id, document_type, file_name, "
                        + "file_path, expiry_date, status) "
                        + "VALUES (?, 'NRC', 'smoke-expiry.pdf', 'smoke/smoke-expiry.pdf', "
                        + "CURDATE() + INTERVAL 10 DAY, 'ACTIVE')",
                employeeId);
    }

    private static void removeFixtureDocument(long documentId) {
        if (documentId > 0) {
            new Sql().executeUpdate(
                    "DELETE FROM employee_documents WHERE id = ?", documentId);
        }
    }

    /** Moves one employee's birthday to today so the scan has work to do. */
    private static void seedBirthday(long employeeId) {
        new Sql().executeUpdate(
                "UPDATE employees SET date_of_birth = "
                        + "DATE_FORMAT(CURDATE(), '%Y-%m-%d') WHERE id = ?",
                employeeId);
    }

    private static void restoreBirthday(long employeeId, String originalBirthDate) {
        if (originalBirthDate != null && !originalBirthDate.isBlank()) {
            new Sql().executeUpdate(
                    "UPDATE employees SET date_of_birth = ? WHERE id = ?",
                    originalBirthDate, employeeId);
        }
    }

    private static boolean existsNotification(String whereClause) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM notifications WHERE id > " + baselineId
                        + " AND " + whereClause) > 0;
    }

    /** Polls up to ~10 s for async (EDT + dispatcher) work to land. */
    private static boolean awaitUntil(java.util.function.BooleanSupplier condition) {
        for (int attempt = 0; attempt < 50; attempt++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /** Removes every row this tool created since its baseline id. */
    private static void purgeArtifacts() {
        new Sql().executeUpdate(
                "DELETE FROM notifications WHERE id > ?", baselineId);
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
