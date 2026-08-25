package com.ams.hrms.tools;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.model.Department;
import com.ams.hrms.model.Employee;
import java.util.List;
import com.ams.hrms.model.Position;
import com.ams.hrms.model.Shift;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AttendanceService;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.DepartmentService;
import com.ams.hrms.service.EmployeeService;
import com.ams.hrms.service.PositionService;
import com.ams.hrms.service.ShiftService;

/**
 * Development-only Phase 11 verification against the live database:
 * check-in/out math (late/early/worked/OT), corrections, the absent sweep,
 * duplicate guards and RBAC denial for FINANCE.
 */
public final class AttendanceSmokeTool {

    private static int failures;

    public static void main(String[] args) {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        purgeArtifacts();

        AuthService authService = ServiceRegistry.authService();
        EmployeeService employees = ServiceRegistry.employeeService();
        DepartmentService departments = ServiceRegistry.departmentService();
        PositionService positions = ServiceRegistry.positionService();
        ShiftService shiftService = ServiceRegistry.shiftService();
        AttendanceService attendance = ServiceRegistry.attendanceService();

        authService.login("admin", "Admin@123");

        // --- fixtures: temp employee + 09:00-17:00 grace15 break60 shift -----
        Department it = departments.findAll("IT").stream()
                .filter(d -> d.getCode().equals("IT")).findFirst().orElseThrow();
        Position dev = positions.findAll("IT-DEV").stream()
                .filter(p -> p.getCode().equals("IT-DEV")).findFirst().orElseThrow();

        Employee worker = new Employee();
        worker.setCode("SMK-E001");
        worker.setFirstName("Smoke");
        worker.setLastName("Attendee");
        worker.setGender("OTHER");
        worker.setJoinDate(LocalDate.now());
        worker.setEmploymentType("FULL_TIME");
        worker.setDepartmentId(it.getId());
        worker.setPositionId(dev.getId());
        worker.setBasicSalary(new java.math.BigDecimal("1500"));
        long workerId = employees.save(worker);

        Shift smokeShift = new Shift();
        smokeShift.setCode("SHK-ATT");
        smokeShift.setName("Smoke Day");
        smokeShift.setStartTime(java.time.LocalTime.of(9, 0));
        smokeShift.setEndTime(java.time.LocalTime.of(17, 0));
        smokeShift.setGraceMinutes(15);
        smokeShift.setBreakMinutes(60);
        shifts_create(shiftService, smokeShift);
        long smokeShiftId = shiftService.findAll("SHK-ATT")
                .stream().filter(s -> s.getCode().equals("SHK-ATT"))
                .findFirst().orElseThrow().getId();
        shiftService.assign(workerId, smokeShiftId, LocalDate.now());

        LocalDate today = LocalDate.now();
        LocalDateTime tenAm = LocalDateTime.of(today, java.time.LocalTime.of(10, 0));

        // --- check-in late ----------------------------------------------------
        check("late check-in recorded", () -> {
            attendance.checkIn(workerId, tenAm);
            return true;
        });
        var record = attendance.findByEmployeeBetween(workerId, today, today).get(0);
        check("status LATE with 60 minutes",
                () -> "LATE".equals(record.getStatus())
                        && record.getLateMinutes() == 60);
        check("duplicate check-in blocked",
                () -> {
                    try {
                        attendance.checkIn(workerId, tenAm.plusMinutes(5));
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        // --- check-out ----------------------------------------------------------
        LocalDateTime sixThirty = LocalDateTime.of(today, java.time.LocalTime.of(18, 30));
        check("check-out computed", () -> {
            attendance.checkOut(workerId, sixThirty);
            return true;
        });
        final var recordAfterOut = attendance.findByEmployeeBetween(workerId, today, today).get(0);
        check("worked hours 7.50 (8.5 - 1 break)",
                () -> recordAfterOut.getWorkedHours().compareTo(new java.math.BigDecimal("7.50")) == 0);
        check("overtime 1.50h",
                () -> recordAfterOut.getOvertimeHours().compareTo(new java.math.BigDecimal("1.50")) == 0);
        check("no early leave", () -> recordAfterOut.getEarlyLeaveMinutes() == 0);

        // --- correction -----------------------------------------------------------
        check("correction recomputes values", () -> {
            attendance.correct(record.getId(),
                    java.time.LocalTime.of(9, 5), java.time.LocalTime.of(16, 10),
                    "Forgot badge at gate");
            var corrected = attendance.findByEmployeeBetween(workerId, today, today).get(0);
            // 09:05 is inside the 15-minute grace window -> not late.
            // 16:10 is 50 minutes before the 17:00 end -> early leave.
            return "EARLY_LEAVE".equals(corrected.getStatus())
                    && corrected.getLateMinutes() == 0
                    && corrected.getEarlyLeaveMinutes() == 50
                    && corrected.getCorrectionReason() != null;
        });

        // --- absent sweep ------------------------------------------------------------
        Employee secondWorker = new Employee();
        secondWorker.setCode("SMK-E002");
        secondWorker.setFirstName("Smoke");
        secondWorker.setLastName("Absent");
        secondWorker.setGender("OTHER");
        secondWorker.setJoinDate(LocalDate.now());
        secondWorker.setEmploymentType("FULL_TIME");
        secondWorker.setDepartmentId(it.getId());
        secondWorker.setPositionId(dev.getId());
        secondWorker.setBasicSalary(new java.math.BigDecimal("1500"));
        employees.save(secondWorker);

        boolean weekend = today.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                || today.getDayOfWeek() == java.time.DayOfWeek.SUNDAY;
        String expectedStatus = weekend ? "WEEKEND" : "ABSENT";
        int created = attendance.generateDaily(today);
        check("absent sweep created rows (" + expectedStatus + ")",
                () -> created >= 1);
        var absentRecord = attendance.findByEmployeeBetween(
                employeeIdByCode("SMK-E002"), today, today);
        final boolean statusMatches = !absentRecord.isEmpty()
                && expectedStatus.equals(absentRecord.get(0).getStatus());
        System.out.println((statusMatches ? "OK   " : "FAIL ")
                + "generated row has status " + expectedStatus);
        if (!statusMatches) {
            failures++;
        }

        // --- RBAC: FINANCE cannot create attendance ----------------------------------
        authService.logout();
        authService.login("finance", "Finance@123");
        check("finance user denied check-in at service gate",
                () -> {
                    try {
                        attendance.checkIn(employeeIdByCode("EMP-0001"), LocalDateTime.now());
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        authService.logout();

        // --- cleanup -------------------------------------------------------------------
        authService.login("admin", "Admin@123");
        purgeArtifacts();
        System.out.println("cleanup: SMK attendance artifacts removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    private static void shifts_create(ShiftService service, Shift shift) {
        service.save(shift);
    }

    private static long employeeIdByCode(String code) {
        return new Sql().scalarLong("SELECT id FROM employees WHERE employee_code = ?", code);
    }

    /** Removes smoke employees, their children, assignments and shifts. */
    private static void purgeArtifacts() {
        List<Long> ids = new Sql().list(
                "SELECT id FROM employees WHERE employee_code LIKE 'SMK-%'",
                rs -> rs.getLong(1));
        for (Long id : ids) {
            new Sql().executeUpdate("DELETE FROM attendance WHERE employee_id = ?", id);
            new Sql().executeUpdate("DELETE FROM salary_structures WHERE employee_id = ?", id);
            new Sql().executeUpdate("DELETE FROM employee_history WHERE employee_id = ?", id);
            new Sql().executeUpdate("DELETE FROM leave_balances WHERE employee_id = ?", id);
            new Sql().executeUpdate("DELETE FROM employee_shifts WHERE employee_id = ?", id);
            new Sql().executeUpdate("DELETE FROM employees WHERE id = ?", id);
        }
        new Sql().executeUpdate("DELETE FROM shifts WHERE shift_code LIKE 'SHK-%'");
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
