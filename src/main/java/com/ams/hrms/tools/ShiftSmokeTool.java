package com.ams.hrms.tools;

import java.time.LocalDate;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.EmployeeShift;
import com.ams.hrms.model.Shift;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.ShiftService;

/**
 * Development-only Phase 10 verification against the live database:
 * overnight shift CRUD, uniqueness, time validation, assignment lifecycle
 * (close previous, same-day replace, end), history entries, deactivation
 * guards and RBAC denial for FINANCE.
 */
public final class ShiftSmokeTool {

    private static int failures;

    public static void main(String[] args) {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        purgeArtifacts();

        AuthService authService = ServiceRegistry.authService();
        ShiftService shifts = ServiceRegistry.shiftService();

        authService.login("admin", "Admin@123");
        long adminEmployeeId = employeeIdByCode("EMP-0001");

        // --- create overnight shift ------------------------------------------
        Shift night = new Shift();
        night.setCode("SHK-NIGHT");
        night.setName("Smoke Night");
        night.setStartTime(java.time.LocalTime.of(22, 0));
        night.setEndTime(java.time.LocalTime.of(6, 0));
        night.setGraceMinutes(10);
        night.setBreakMinutes(30);
        check("create overnight shift", () -> shifts.save(night) > 0);
        long nightId = findShiftId(shifts, "SHK-NIGHT");

        // --- validation --------------------------------------------------------
        Shift missingTimes = new Shift();
        missingTimes.setCode("SHK-BAD");
        missingTimes.setName("Missing Times");
        check("reject shift without times",
                () -> {
                    try {
                        shifts.save(missingTimes);
                        return false;
                    } catch (ValidationException e) {
                        return e.getErrors().size() >= 2;
                    }
                });

        Shift duplicate = new Shift();
        duplicate.setCode("shk-night");
        duplicate.setName("Other Night");
        duplicate.setStartTime(java.time.LocalTime.of(23, 0));
        duplicate.setEndTime(java.time.LocalTime.of(7, 0));
        check("reject duplicate code (case-insensitive)",
                () -> {
                    try {
                        shifts.save(duplicate);
                        return false;
                    } catch (ValidationException e) {
                        return e.getErrors().get(0).contains("already in use");
                    }
                });

        // --- assignment lifecycle ----------------------------------------------
        check("assign night shift from today", () -> {
            shifts.assign(adminEmployeeId, nightId, LocalDate.now());
            return true;
        });

        var open = shifts.historyForEmployee(adminEmployeeId).stream()
                .filter(EmployeeShift::isCurrent).findFirst().orElseThrow();
        check("open assignment is the new night shift",
                () -> "Smoke Night".equals(open.getShiftName()));
        LocalDate nightStart = open.getEffectiveFrom();
        boolean morningClosedCorrectly = shifts.historyForEmployee(adminEmployeeId).stream()
                .anyMatch(a -> "Morning Shift".equals(a.getShiftName())
                        && !a.isCurrent()
                        && nightStart.minusDays(1).equals(a.getEffectiveTo()));
        System.out.println((morningClosedCorrectly ? "OK   " : "FAIL ")
                + "morning assignment closed the day before the new shift");
        if (!morningClosedCorrectly) {
            failures++;
        }

        check("SHIFT_CHANGE history recorded",
                () -> ServiceRegistry.employeeService().findHistory(adminEmployeeId).stream()
                        .anyMatch(h -> "SHIFT_CHANGE".equals(h.changeType())));

        check("same-day replacement keeps one open row",
                () -> {
                    shifts.assign(adminEmployeeId, nightId, LocalDate.now());
                    long openRows = shifts.historyForEmployee(adminEmployeeId).stream()
                            .filter(EmployeeShift::isCurrent).count();
                    return openRows == 1;
                });

        long openAssignmentId = shifts.historyForEmployee(adminEmployeeId).stream()
                .filter(EmployeeShift::isCurrent).findFirst().orElseThrow().getId();

        check("deactivate assigned shift blocked",
                () -> {
                    try {
                        shifts.setStatus(nightId, "INACTIVE");
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        check("end assignment as of today", () -> {
            shifts.endAssignment(openAssignmentId, LocalDate.now());
            return true;
        });

        // --- separate empty shift for the deactivate pair -----------------------
        Shift empty = new Shift();
        empty.setCode("SHK-EMPTY");
        empty.setName("Smoke Empty");
        empty.setStartTime(java.time.LocalTime.of(9, 0));
        empty.setEndTime(java.time.LocalTime.of(17, 0));
        check("create empty shift", () -> shifts.save(empty) > 0);
        long emptyId = findShiftId(shifts, "SHK-EMPTY");
        check("deactivate unassigned shift OK", () -> {
            shifts.setStatus(emptyId, "INACTIVE");
            return true;
        });
        check("re-activate shift OK", () -> {
            shifts.setStatus(emptyId, "ACTIVE");
            return true;
        });

        // --- RBAC: FINANCE lacks shift permissions -------------------------------
        authService.logout();
        authService.login("finance", "Finance@123");
        check("finance user denied shift list at service gate",
                () -> {
                    try {
                        shifts.findAll("");
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        authService.logout();

        // --- cleanup ----------------------------------------------------------------
        authService.login("admin", "Admin@123");
        purgeArtifacts();
        System.out.println("cleanup: SMK/SHK artifacts removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    private static long employeeIdByCode(String code) {
        return new Sql().scalarLong(
                "SELECT id FROM employees WHERE employee_code = ?", code);
    }

    private static long findShiftId(ShiftService service, String code) {
        return service.findAll(code).stream()
                .filter(shift -> shift.getCode().equals(code))
                .findFirst().orElseThrow().getId();
    }

    /** Removes smoke shifts/assignments and restores the canonical open Morning row. */
    private static void purgeArtifacts() {
        new Sql().executeUpdate(
                "DELETE es FROM employee_shifts es JOIN shifts s ON s.id = es.shift_id "
                        + "WHERE s.shift_code LIKE 'SHK-%'");
        new Sql().executeUpdate("DELETE FROM shifts WHERE shift_code LIKE 'SHK-%'");
        // Restore the seeded Morning assignment to open-ended for a clean run.
        new Sql().executeUpdate(
                "UPDATE employee_shifts es JOIN shifts s ON s.id = es.shift_id "
                        + "JOIN employees e ON e.id = es.employee_id "
                        + "SET es.effective_to = NULL "
                        + "WHERE s.shift_code = 'SH-MORNING' AND e.employee_code = 'EMP-0001'");
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
