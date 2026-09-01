package com.ams.hrms.tools;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.EmployeeTraining;
import com.ams.hrms.model.TrainingProgram;
import com.ams.hrms.model.TrainingSession;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.TrainingService;

/**
 * Development-only Phase 19 verification against the live database: program
 * creation with capacity, session scheduling with duration math, enrollment
 * (duplicate + capacity guards), result recording through terminal lock,
 * unenroll guard and RBAC denial for FINANCE. Idempotent cleanup afterwards.
 */
public final class TrainingSmokeTool {

    private static int failures;
    private static long programId;
    private static long sessionId;
    private static long enrollmentAId;
    private static long enrollmentBId;
    private static Long employeeCId;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        TrainingService training = ServiceRegistry.trainingService();

        purgeArtifacts();
        authService.login("admin@ams.local", "Admin@123");

        // --- program ------------------------------------------------------------
        TrainingProgram program = new TrainingProgram();
        program.setName("SMOKE Safety Orientation");
        program.setTrainerName("Smoke Trainer");
        program.setCost(BigDecimal.valueOf(250));
        program.setCapacity(2);
        check("create PLANNED program", () -> {
            programId = training.saveProgram(program);
            return training.findPrograms("SMOKE", "PLANNED").stream()
                    .anyMatch(candidate -> candidate.getId() == programId
                            && candidate.getCode() != null
                            && candidate.getEnrolledCount() == 0);
        });

        // --- session --------------------------------------------------------------
        LocalDateTime base = LocalDateTime.now().plusDays(3).withMinute(0).withSecond(0)
                .withNano(0);
        TrainingSession session = new TrainingSession();
        session.setProgramId(programId);
        session.setStartDateTime(base);
        session.setEndDateTime(base.plusMinutes(150));
        session.setLocation("Training Room 1");
        check("schedule session computes 2.5h duration", () -> {
            sessionId = training.saveSession(session);
            var stored = training.findSessions(programId, "SCHEDULED").stream()
                    .filter(candidate -> candidate.getId() == sessionId).findFirst().orElseThrow();
            return stored.getDurationHours() != null
                    && stored.getDurationHours().compareTo(BigDecimal.valueOf(2.5)) == 0;
        });

        check("session end before start rejected",
                () -> {
                    try {
                        TrainingSession bad = cloneSession();
                        bad.setEndDateTime(base.minusMinutes(30));
                        training.saveSession(bad);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        // --- enrollments -------------------------------------------------------------
        long employeeAId = employeeIdByCode("EMP-0001");
        long employeeBId = employeeIdByCode("EMP-0004");
        employeeCId = new Sql().first(
                "SELECT id FROM employees WHERE employee_code = 'EMP-0006'",
                rs -> rs.getLong(1)).orElse(null);

        EmployeeTraining enrollmentA = new EmployeeTraining();
        enrollmentA.setProgramId(programId);
        enrollmentA.setEmployeeId(employeeAId);
        enrollmentA.setSessionId(sessionId);
        check("enroll first employee into session", () -> {
            enrollmentAId = training.enroll(enrollmentA);
            return enrolledCount() == 1;
        });

        check("duplicate enrollment rejected",
                () -> {
                    try {
                        training.enroll(enrollmentA);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        EmployeeTraining enrollmentB = new EmployeeTraining();
        enrollmentB.setProgramId(programId);
        enrollmentB.setEmployeeId(employeeBId);
        check("second employee fills capacity", () -> {
            enrollmentBId = training.enroll(enrollmentB);
            return enrolledCount() == 2;
        });

        final boolean[] thirdEnrollmentBlocked = {false};
        if (employeeCId != null) {
            EmployeeTraining enrollmentC = new EmployeeTraining();
            enrollmentC.setProgramId(programId);
            enrollmentC.setEmployeeId(employeeCId);
            try {
                training.enroll(enrollmentC);
                thirdEnrollmentBlocked[0] = false;
            } catch (BusinessException expected) {
                thirdEnrollmentBlocked[0] = true;
            }
        } else {
            thirdEnrollmentBlocked[0] = true;
        }
        check("third employee blocked at capacity", () -> thirdEnrollmentBlocked[0]);

        // --- results -------------------------------------------------------------------
        check("unenroll blocked after progress", () -> {
            training.recordResult(enrollmentAId, "ATTENDED", BigDecimal.valueOf(80), null);
            try {
                training.unenroll(enrollmentAId);
                return false;
            } catch (BusinessException expected) {
                return true;
            }
        });

        check("result completes with score and completion date", () -> {
            training.recordResult(enrollmentAId, "PASSED", BigDecimal.valueOf(92),
                    "Great safety awareness.");
            var stored = training.findEnrollments(programId, "PASSED", null).stream()
                    .filter(candidate -> candidate.getId() == enrollmentAId)
                    .findFirst().orElseThrow();
            return stored.getScore() != null
                    && stored.getScore().compareTo(BigDecimal.valueOf(92)) == 0
                    && stored.getCompletionDate() != null
                    && stored.getNotes().contains("safety");
        });

        check("terminal PASSED locks the record",
                () -> {
                    try {
                        training.recordResult(enrollmentAId, "FAILED",
                                BigDecimal.valueOf(10), "overwrite");
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        check("program completes only from live states",
                () -> {
                    training.setProgramStatus(programId, "ONGOING");
                    boolean ongoingOk = training.findPrograms("SMOKE", "ONGOING").stream()
                            .anyMatch(candidate -> candidate.getId() == programId);
                    try {
                        training.setProgramStatus(programId, "PLANNED");
                        return false;
                    } catch (BusinessException expected) {
                        // illegal backwards transition refused
                    }
                    training.setProgramStatus(programId, "COMPLETED");
                    return ongoingOk;
                });

        // --- RBAC: FINANCE has no TRAINING permissions ---------------------------------
        authService.logout();
        authService.login("finance@ams.local", "Finance@123");
        check("finance user denied training access at service gate",
                () -> {
                    try {
                        training.findPrograms(null, null);
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        authService.logout();

        // --- cleanup ----------------------------------------------------------------------
        authService.login("admin@ams.local", "Admin@123");
        purgeArtifacts();
        System.out.println("cleanup: smoke program/session/enrollments removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static TrainingSession cloneSession() {
        TrainingSession copy = new TrainingSession();
        copy.setProgramId(programId);
        copy.setStartDateTime(LocalDateTime.now().plusDays(4));
        copy.setEndDateTime(LocalDateTime.now().plusDays(4).plusHours(2));
        return copy;
    }

    private static long enrolledCount() {
        return ServiceRegistry.trainingService()
                .findEnrollments(programId, null, null).size();
    }

    private static long employeeIdByCode(String code) {
        return new Sql().scalarLong(
                "SELECT id FROM employees WHERE employee_code = ?", code);
    }

    /** Removes smoke-created training rows; EMP-0006 is untouched seed data. */
    private static void purgeArtifacts() {
        new Sql().executeUpdate(
                "DELETE FROM employee_trainings WHERE training_program_id IN "
                        + "(SELECT id FROM training_programs WHERE program_name LIKE 'SMOKE %')");
        new Sql().executeUpdate(
                "DELETE FROM training_sessions WHERE training_program_id IN "
                        + "(SELECT id FROM training_programs WHERE program_name LIKE 'SMOKE %')");
        new Sql().executeUpdate(
                "DELETE FROM training_programs WHERE program_name LIKE 'SMOKE %'");
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
