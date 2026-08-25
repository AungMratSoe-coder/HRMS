package com.ams.hrms.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.EmployeeDocument;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.security.PasswordHasher;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.DocumentService;
import com.ams.hrms.service.EmployeeService;

/**
 * Development-only Phase 9 verification against the live database:
 * document upload validation (type/expiry/past-date), storage on disk,
 * archive + soft-delete, expiry detection, RBAC denial for FINANCE, and
 * idempotent cleanup of rows and files.
 */
public final class DocumentSmokeTool {

    private static int failures;
    private static long documentId;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();
        ServiceRegistry.documentService().refreshExpiredStatuses();

        AuthService authService = ServiceRegistry.authService();
        DocumentService documents = ServiceRegistry.documentService();
        EmployeeService employees = ServiceRegistry.employeeService();

        purgeArtifacts();
        authService.login("admin", "Admin@123");

        Long employeeId = employees.findAll(
                        new com.ams.hrms.repository.EmployeeRepository.Filter(
                                "EMP-0001", null, null, null))
                .stream().findFirst().orElseThrow().getId();

        // --- upload a small fake PDF ---------------------------------------
        Path tempFile = Files.createTempFile("smoke-contract-", ".pdf");
        Files.writeString(tempFile, "%PDF-1.4 smoke test document");

        check("upload CONTRACT document", () -> {
            documentId = documents.upload(employeeId, "CONTRACT", tempFile,
                    LocalDate.now().plusYears(2), "Smoke upload");
            return documentId > 0;
        });

        check("document listed for employee",
                () -> documents.findByEmployee(employeeId).stream()
                        .anyMatch(d -> d.getId() == documentId));

        check("stored file exists on disk",
                () -> {
                    var stored = documents.findByEmployee(employeeId).stream()
                            .filter(d -> d.getId() == documentId).findFirst().orElseThrow();
                    return Files.isRegularFile(com.ams.hrms.util.FileStorage.resolve(stored.getFilePath()));
                });

        // --- validation ------------------------------------------------------
        check("reject unsupported file type",
                () -> {
                    Path bad = Files.createTempFile("smoke-bad-", ".exe");
                    Files.writeString(bad, "MZ...");
                    try {
                        documents.upload(employeeId, "OTHER", bad, null, null);
                        return false;
                    } catch (com.ams.hrms.exception.HrmsException expected) {
                        // Either exception type is correct: friendly business rejection.
                        return true;
                    } finally {
                        Files.deleteIfExists(bad);
                    }
                });

        check("reject past expiry date",
                () -> {
                    try {
                        documents.upload(employeeId, "PASSPORT", tempFile,
                                LocalDate.now().minusDays(1), null);
                        return false;
                    } catch (ValidationException expected) {
                        return expected.getErrors().get(0).contains("past");
                    }
                });

        // --- expiry detection --------------------------------------------------
        Path expiringFile = Files.createTempFile("smoke-nrc-", ".pdf");
        Files.writeString(expiringFile, "%PDF-1.4 expiring soon");
        long expiringId = documents.upload(employeeId, "NRC", expiringFile,
                LocalDate.now().plusDays(10), null);
        check("expiring-soon document detected",
                () -> documents.findExpiring().stream().anyMatch(d -> d.getId() == expiringId));

        // --- archive / delete ---------------------------------------------------
        check("archive document", () -> {
            documents.archive(documentId);
            return documents.findByEmployee(employeeId).stream()
                    .filter(d -> d.getId() == documentId)
                    .findFirst().orElseThrow()
                    .getStatus().equals("ARCHIVED");
        });

        // --- RBAC: FINANCE lacks DOCUMENT_MANAGE ---------------------------------
        authService.logout();
        authService.login("finance", "Finance@123");
        check("finance user denied document upload at service gate",
                () -> {
                    try {
                        documents.upload(employeeId, "OTHER", tempFile, null, null);
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        authService.logout();

        // --- cleanup ---------------------------------------------------------------
        authService.login("admin", "Admin@123");
        purgeArtifacts();
        Files.deleteIfExists(tempFile);
        Files.deleteIfExists(expiringFile);
        System.out.println("cleanup: smoke document rows + files removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    /** Removes smoke-created document rows and files (dev bypass). */
    private static void purgeArtifacts() {
        new Sql().executeUpdate(
                "DELETE FROM employee_documents WHERE file_name LIKE 'smoke-%'");
        try (var paths = Files.walk(Path.of(AppConfig.get().documentsRoot(), "documents"))) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().contains("smoke-"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception ignored) {
                            // best-effort cleanup
                        }
                    });
        } catch (Exception ignored) {
            // documents root may not exist yet
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
