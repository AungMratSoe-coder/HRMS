package com.ams.hrms.tools;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Department;
import com.ams.hrms.model.Position;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.DepartmentService;
import com.ams.hrms.service.PositionService;

/**
 * Development-only Phase 7 verification against the live database.
 * Idempotent: purges its own SMK-* artifacts (raw SQL - dev tool bypasses the
 * application's soft-delete policy for its test data only) before running,
 * and again afterwards leaves nothing active behind.
 */
public final class OrgSmokeTool {

    private static int failures;
    private static long qaId;
    private static long engId;

    public static void main(String[] args) {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        purgeArtifacts();

        AuthService authService = ServiceRegistry.authService();
        DepartmentService departments = ServiceRegistry.departmentService();
        PositionService positions = ServiceRegistry.positionService();

        authService.login("admin", "Admin@123");

        // --- create -------------------------------------------------------
        Department qa = new Department();
        qa.setCode("SMK-QA");
        qa.setName("Smoke QA");
        qa.setDescription("Created by OrgSmokeTool");
        check("create department", () -> departments.save(qa) > 0);
        qaId = findDepartmentId(departments, "SMK-QA");

        // --- validation: bad code ----------------------------------------
        Department badCode = new Department();
        badCode.setCode("bad code!");
        badCode.setName("Bad Code Dept");
        check("reject invalid code format",
                () -> {
                    try {
                        departments.save(badCode);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        // --- uniqueness ---------------------------------------------------
        Department duplicate = new Department();
        duplicate.setCode("smk-qa");
        duplicate.setName("Another QA");
        check("reject duplicate code (case-insensitive)",
                () -> {
                    try {
                        departments.save(duplicate);
                        return false;
                    } catch (ValidationException expected) {
                        return expected.getErrors().get(0).contains("already in use");
                    }
                });

        // --- positions ----------------------------------------------------
        Position engineer = new Position();
        engineer.setCode("SMK-ENG");
        engineer.setName("QA Engineer");
        engineer.setDepartmentId(qaId);
        engineer.setMinSalary(new java.math.BigDecimal("1200"));
        engineer.setMaxSalary(new java.math.BigDecimal("2000"));
        check("create position with valid envelope",
                () -> positions.save(engineer) > 0);
        engId = findPositionId(positions, "SMK-ENG");

        Position inverted = new Position();
        inverted.setCode("SMK-INV");
        inverted.setName("Inverted Envelope");
        inverted.setDepartmentId(qaId);
        inverted.setMinSalary(new java.math.BigDecimal("3000"));
        inverted.setMaxSalary(new java.math.BigDecimal("2000"));
        check("reject inverted salary envelope",
                () -> {
                    try {
                        positions.save(inverted);
                        return false;
                    } catch (ValidationException expected) {
                        return expected.getErrors().get(0).contains("Minimum salary");
                    }
                });

        // --- referential guards -------------------------------------------
        Long itId = findDepartmentId(departments, "IT");
        check("cannot deactivate IT (active employees)",
                () -> {
                    try {
                        departments.setStatus(itId, "INACTIVE");
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        check("cannot deactivate SMK-QA (active position)",
                () -> {
                    try {
                        departments.setStatus(qaId, "INACTIVE");
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        check("deactivate position OK",
                () -> {
                    positions.setStatus(engId, "INACTIVE");
                    return true;
                });
        check("deactivate now-empty department OK",
                () -> {
                    departments.setStatus(qaId, "INACTIVE");
                    return true;
                });
        check("re-activate department OK",
                () -> {
                    departments.setStatus(qaId, "ACTIVE");
                    return true;
                });

        // --- RBAC: restricted user -----------------------------------------
        authService.logout();
        authService.login("officer", "Officer@123");
        Department officerAttempt = new Department();
        officerAttempt.setCode("SMK-X");
        officerAttempt.setName("Officer X");
        check("officer denied department creation at service gate",
                () -> {
                    try {
                        departments.save(officerAttempt);
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        authService.logout();

        // --- cleanup ---------------------------------------------------------
        authService.login("admin", "Admin@123");
        purgeArtifacts();
        System.out.println("cleanup: SMK-* artifacts removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    /** Removes tool-created rows directly (dev-tool bypass of soft delete). */
    private static void purgeArtifacts() {
        new Sql().executeUpdate(
                "DELETE FROM positions WHERE position_code LIKE 'SMK-%'");
        new Sql().executeUpdate(
                "DELETE FROM departments WHERE dept_code LIKE 'SMK-%'");
    }

    private static boolean check(String label, BooleanCheck action) {
        try {
            boolean passed = action.run();
            System.out.println((passed ? "OK   " : "FAIL ") + label);
            if (!passed) {
                failures++;
            }
            return passed;
        } catch (Exception e) {
            System.out.println("FAIL " + label + " -> unexpected " + e.getClass().getSimpleName()
                    + ": " + e.getMessage());
            failures++;
            return false;
        }
    }

    @FunctionalInterface
    private interface BooleanCheck {
        boolean run() throws Exception;
    }

    private static Long findDepartmentId(DepartmentService service, String code) {
        return service.findAll(code).stream()
                .filter(department -> department.getCode().equals(code))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private static long findPositionId(PositionService service, String code) {
        return service.findAll(code).stream()
                .filter(position -> position.getCode().equals(code))
                .findFirst()
                .orElseThrow()
                .getId();
    }
}
