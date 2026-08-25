package com.ams.hrms.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.model.AttendanceRecord;
import com.ams.hrms.repository.AttendanceRepository;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;

/**
 * Attendance operations (spec section 16): check-in/out against the
 * employee's effective shift, corrections with recomputation, and the daily
 * absent/weekend sweep. All math lives in {@link AttendanceCalculator}.
 */
public class AttendanceService {

    public static final String DATA_SCOPE = "attendance";

    private static final Logger LOG = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRepository repository;
    private final AuditService auditService;
    private final EmployeeService employeeService;

    public AttendanceService(AttendanceRepository repository, AuditService auditService,
                             EmployeeService employeeService) {
        this.repository = repository;
        this.auditService = auditService;
        this.employeeService = employeeService;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** One day of records; plain EMPLOYEE accounts only see their own row. */
    public List<AttendanceRecord> findByDate(LocalDate date, String keyword,
                                             Long departmentId, String status) {
        SecurityService.require(Permissions.ATTENDANCE_VIEW);
        return repository.findByDate(date, keyword, departmentId, status,
                employeeService.selfScopeEmployeeId());
    }

    public List<AttendanceRecord> findByEmployeeBetween(long employeeId,
                                                        LocalDate from, LocalDate to) {
        if (!employeeService.isOwnRecord(employeeId)) {
            SecurityService.require(Permissions.ATTENDANCE_VIEW);
            employeeService.requireVisible(employeeId);
        }
        return repository.findByEmployeeBetween(employeeId, from, to);
    }

    public AttendanceRepository.MonthSummary monthTotals(long employeeId, int year, int month) {
        if (!employeeService.isOwnRecord(employeeId)) {
            SecurityService.require(Permissions.ATTENDANCE_VIEW);
            employeeService.requireVisible(employeeId);
        }
        return repository.monthTotals(employeeId, year, month);
    }

    // ------------------------------------------------------------------
    // Check in / out
    // ------------------------------------------------------------------

    public long checkIn(long employeeId, LocalDateTime when) {
        SecurityService.require(Permissions.ATTENDANCE_CREATE);
        requireActiveEmployee(employeeId);

        LocalDate date = when.toLocalDate();
        if (repository.existsForEmployeeDate(employeeId, date)) {
            throw new BusinessException(
                    "Attendance already recorded",
                    "Attendance for this employee is already recorded today.");
        }
        var snapshot = resolveShift(employeeId, date).orElse(null);
        var result = AttendanceCalculator.evaluate(snapshot, when.toLocalTime(), null);

        long id = repository.insertCheckIn(employeeId, date, when.toLocalTime(),
                result.status(), result.lateMinutes());
        audit("CHECK_IN", id, "Check-in recorded for employee #" + employeeId
                + " (" + result.status() + ")");
        publishChange();
        return id;
    }

    public void checkOut(long employeeId, LocalDateTime when) {
        SecurityService.require(Permissions.ATTENDANCE_CREATE);
        LocalDate date = when.toLocalDate();
        AttendanceRecord record = repository.findByEmployeeAndDate(employeeId, date)
                .orElseThrow(() -> new BusinessException(
                        "No check-in found",
                        "There is no check-in recorded today for this employee."));
        if (record.getCheckOut() != null) {
            throw new BusinessException(
                    "Already checked out",
                    "This employee has already checked out today.");
        }
        applyComputedValues(record.getId(), record.getAttendanceDate(), employeeId,
                record.getCheckIn(), when.toLocalTime());
        audit("CHECK_OUT", record.getId(), "Check-out recorded for employee #" + employeeId);
        publishChange();
    }

    /** HR correction: recompute all values from edited times (spec section 16). */
    public void correct(long recordId, java.time.LocalTime checkIn,
                        java.time.LocalTime checkOut, String reason) {
        SecurityService.require(Permissions.ATTENDANCE_UPDATE);
        AttendanceRecord record = repository.findById(recordId)
                .orElseThrow(() -> new BusinessException(
                        "Record not found", "The attendance record no longer exists."));
        if (reason == null || reason.isBlank()) {
            throw new com.ams.hrms.exception.ValidationException(
                    List.of("A correction reason is required."));
        }
        if (checkIn == null || checkOut == null) {
            throw new com.ams.hrms.exception.ValidationException(
                    List.of("Both check-in and check-out times are required for a correction."));
        }

        var snapshot = resolveShift(record.getEmployeeId(), record.getAttendanceDate())
                .orElse(null);
        var result = AttendanceCalculator.evaluate(snapshot, checkIn, checkOut);
        long correctedBy = com.ams.hrms.security.SessionContext.currentUserId();

        repository.applyCorrection(recordId, checkIn, checkOut,
                result.status(), result.lateMinutes(), result.earlyLeaveMinutes(),
                result.workedHours(), result.overtimeHours(), correctedBy, reason.trim());
        audit("CORRECTION", recordId, "Corrected attendance for '"
                + record.getEmployeeCode() + "' on " + record.getAttendanceDate()
                + " (" + reason.trim() + ")");
        publishChange();
    }

    /**
     * Marks every active employee without a record as ABSENT (or WEEKEND on
     * Sat/Sun). Returns how many rows were created.
     */
    public int generateDaily(LocalDate date) {
        SecurityService.require(Permissions.ATTENDANCE_CREATE);
        boolean weekend = date.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                || date.getDayOfWeek() == java.time.DayOfWeek.SUNDAY;
        String status = weekend ? "WEEKEND" : "ABSENT";

        List<long[]> missing = repository.employeesWithoutRecord(date);
        int created = 0;
        for (long[] row : missing) {
            repository.insertStatusOnly(row[0], date, status);
            created++;
        }
        if (created > 0) {
            audit("CREATE", null, "Generated " + created + " '" + status
                    + "' attendance rows for " + date);
            publishChange();
        }
        return created;
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private void applyComputedValues(long recordId, LocalDate date, long employeeId,
                                     java.time.LocalTime checkIn, java.time.LocalTime checkOut) {
        var snapshot = resolveShift(employeeId, date).orElse(null);
        var result = AttendanceCalculator.evaluate(snapshot, checkIn, checkOut);
        repository.updateCheckOut(recordId, checkOut, result.status(),
                result.earlyLeaveMinutes(), result.workedHours(), result.overtimeHours());
    }

    /**
     * Effective shift snapshot for an employee/date: latest covering
     * assignment, else the default shift from settings, else null.
     */
    Optional<AttendanceCalculator.Snapshot> resolveShift(long employeeId, LocalDate date) {
        Optional<AttendanceCalculator.Snapshot> assigned = new Sql().first(
                "SELECT s.start_time AS st, s.end_time AS et, s.grace_minutes AS g, s.break_minutes AS b "
                        + "FROM employee_shifts es JOIN shifts s ON s.id = es.shift_id "
                        + "WHERE es.employee_id = ? AND es.effective_from <= ? "
                        + "AND (es.effective_to IS NULL OR es.effective_to >= ?) "
                        + "ORDER BY es.effective_from DESC LIMIT 1",
                rs -> new AttendanceCalculator.Snapshot(
                        rs.getObject("st", java.time.LocalTime.class),
                        rs.getObject("et", java.time.LocalTime.class),
                        rs.getInt("g"), rs.getInt("b")),
                employeeId, date, date);
        if (assigned.isPresent()) {
            return assigned;
        }
        String defaultCode = new Sql().first(
                "SELECT setting_value FROM app_settings WHERE setting_key = 'attendance.default_shift_code'",
                rs -> rs.getString(1)).orElse("");
        if (defaultCode.isBlank()) {
            return Optional.empty();
        }
        return new Sql().first(
                "SELECT start_time AS st, end_time AS et, grace_minutes AS g, break_minutes AS b "
                        + "FROM shifts WHERE shift_code = ?",
                rs -> new AttendanceCalculator.Snapshot(
                        rs.getObject("st", java.time.LocalTime.class),
                        rs.getObject("et", java.time.LocalTime.class),
                        rs.getInt("g"), rs.getInt("b")),
                defaultCode);
    }

    private void requireActiveEmployee(long employeeId) {
        long count = new Sql().scalarLong(
                "SELECT COUNT(*) FROM employees WHERE id = ? AND status = 'ACTIVE'", employeeId);
        if (count == 0) {
            throw new BusinessException(
                    "Employee not found or inactive",
                    "Only active employees can record attendance.");
        }
    }

    private void audit(String action, Long entityId, String description) {
        auditService.record(action, "ATTENDANCE", "Attendance", entityId, description);
    }

    private void publishChange() {
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}
