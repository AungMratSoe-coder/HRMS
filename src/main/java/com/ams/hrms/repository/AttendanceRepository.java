package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Optional;
import java.util.List;

import com.ams.hrms.model.AttendanceRecord;

/** Attendance persistence (spec section 16). */
public class AttendanceRepository {

    private static final String SELECT =
            "SELECT a.id, a.employee_id, a.attendance_date, a.check_in, a.check_out, a.status, "
                    + "a.late_minutes, a.early_leave_minutes, a.worked_hours, a.overtime_hours, "
                    + "a.remarks, a.correction_reason, e.employee_code, e.full_name, d.dept_name AS department_name "
                    + "FROM attendance a "
                    + "JOIN employees e ON e.id = a.employee_id "
                    + "LEFT JOIN departments d ON d.id = e.department_id";

    /** One day of records, optionally filtered. */
    public List<AttendanceRecord> findByDate(LocalDate date, String keyword,
                                             Long departmentId, String status) {
        return findByDate(date, keyword, departmentId, status, null);
    }

    /**
     * One day of records; {@code restrictToEmployeeId} (self-service scope)
     * limits the result to that employee's rows.
     */
    public List<AttendanceRecord> findByDate(LocalDate date, String keyword,
                                             Long departmentId, String status,
                                             Long restrictToEmployeeId) {
        StringBuilder sql = new StringBuilder(SELECT).append(
                " WHERE a.attendance_date = ?");
        List<Object> params = new ArrayList<>();
        params.add(date);

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.employee_code LIKE CONCAT('%', ?, '%') "
                    + "OR e.full_name LIKE CONCAT('%', ?, '%'))");
            params.add(keyword.trim());
            params.add(keyword.trim());
        }
        if (departmentId != null) {
            sql.append(" AND e.department_id = ?");
            params.add(departmentId);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND a.status = ?");
            params.add(status);
        }
        if (restrictToEmployeeId != null) {
            sql.append(" AND a.employee_id = ?");
            params.add(restrictToEmployeeId);
        }
        sql.append(" ORDER BY e.full_name");
        return new Sql().list(sql.toString(), this::mapRow, params.toArray());
    }

    public Optional<AttendanceRecord> findById(long id) {
        return new Sql().first(SELECT + " WHERE a.id = ?", this::mapRow, id);
    }

    public Optional<AttendanceRecord> findByEmployeeAndDate(long employeeId, LocalDate date) {
        return new Sql().first(SELECT + " WHERE a.employee_id = ? AND a.attendance_date = ?",
                this::mapRow, employeeId, date);
    }

    public List<AttendanceRecord> findByEmployeeBetween(long employeeId, LocalDate from, LocalDate to) {
        return new Sql().list(
                SELECT + " WHERE a.employee_id = ? AND a.attendance_date BETWEEN ? AND ? "
                        + "ORDER BY a.attendance_date",
                this::mapRow, employeeId, from, to);
    }

    public boolean existsForEmployeeDate(long employeeId, LocalDate date) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM attendance WHERE employee_id = ? AND attendance_date = ?",
                employeeId, date) > 0;
    }

    public long insertCheckIn(long employeeId, LocalDate date,
                              java.time.LocalTime checkIn, String status, int lateMinutes) {
        return new Sql().executeInsert(
                "INSERT INTO attendance (employee_id, attendance_date, check_in, status, late_minutes) "
                        + "VALUES (?, ?, ?, ?, ?)",
                employeeId, date, checkIn, status, lateMinutes);
    }

    public void updateCheckOut(long id, java.time.LocalTime checkOut, String status,
                               int earlyMinutes, BigDecimal workedHours, BigDecimal overtimeHours) {
        new Sql().executeUpdate(
                "UPDATE attendance SET check_out = ?, status = ?, early_leave_minutes = ?, "
                        + "worked_hours = ?, overtime_hours = ? WHERE id = ?",
                checkOut, status, earlyMinutes, workedHours, overtimeHours, id);
    }

    public void applyCorrection(long id, java.time.LocalTime checkIn, java.time.LocalTime checkOut,
                                String status, int lateMinutes, int earlyMinutes,
                                BigDecimal workedHours, BigDecimal overtimeHours,
                                long correctedBy, String reason) {
        new Sql().executeUpdate(
                "UPDATE attendance SET check_in = ?, check_out = ?, status = ?, late_minutes = ?, "
                        + "early_leave_minutes = ?, worked_hours = ?, overtime_hours = ?, "
                        + "corrected_by = ?, correction_reason = ? WHERE id = ?",
                checkIn, checkOut, status, lateMinutes, earlyMinutes, workedHours,
                overtimeHours, correctedBy, reason, id);
    }

    public void insertStatusOnly(long employeeId, LocalDate date, String status) {
        new Sql().executeInsert(
                "INSERT INTO attendance (employee_id, attendance_date, status) VALUES (?, ?, ?)",
                employeeId, date, status);
    }

    /** ACTIVE employees with no attendance row on {@code date}. */
    public List<long[]> employeesWithoutRecord(LocalDate date) {
        return new Sql().list(
                "SELECT e.id FROM employees e "
                        + "LEFT JOIN attendance a ON a.employee_id = e.id AND a.attendance_date = ? "
                        + "WHERE e.status = 'ACTIVE' AND e.join_date <= ? AND a.id IS NULL",
                rs -> new long[]{rs.getLong(1)}, date, date);
    }

    /** Aggregated totals for one employee/month (for the Monthly tab). */
    public record MonthSummary(long present, long late, long earlyLeave, long halfDay,
                               long absent, BigDecimal totalWorked, BigDecimal totalOvertime) {
    }

    public MonthSummary monthTotals(long employeeId, int year, int month) {
        return new Sql().first(
                "SELECT COALESCE(SUM(status IN ('PRESENT','MISSION')),0) AS present, "
                        + "COALESCE(SUM(status='LATE'),0) AS late, "
                        + "COALESCE(SUM(status='EARLY_LEAVE'),0) AS early, "
                        + "COALESCE(SUM(status='HALF_DAY'),0) AS half_day, "
                        + "COALESCE(SUM(status IN ('ABSENT')),0) AS absent, "
                        + "COALESCE(SUM(worked_hours),0) AS worked, "
                        + "COALESCE(SUM(overtime_hours),0) AS overtime "
                        + "FROM attendance WHERE employee_id = ? "
                        + "AND YEAR(attendance_date) = ? AND MONTH(attendance_date) = ?",
                rs -> new MonthSummary(rs.getLong("present"), rs.getLong("late"),
                        rs.getLong("early"), rs.getLong("half_day"), rs.getLong("absent"),
                        rs.getBigDecimal("worked"), rs.getBigDecimal("overtime")),
                employeeId, year, month).orElse(new MonthSummary(0, 0, 0, 0, 0,
                BigDecimal.ZERO, BigDecimal.ZERO));
    }

    private AttendanceRecord mapRow(ResultSet rs) throws SQLException {
        AttendanceRecord record = new AttendanceRecord();
        record.setId(rs.getLong("id"));
        record.setEmployeeId(rs.getLong("employee_id"));
        record.setEmployeeCode(rs.getString("employee_code"));
        record.setFullName(rs.getString("full_name"));
        record.setDepartmentName(rs.getString("department_name"));
        record.setAttendanceDate(rs.getObject("attendance_date", LocalDate.class));
        record.setCheckIn(rs.getObject("check_in", java.time.LocalTime.class));
        record.setCheckOut(rs.getObject("check_out", java.time.LocalTime.class));
        record.setStatus(rs.getString("status"));
        record.setLateMinutes(rs.getInt("late_minutes"));
        record.setEarlyLeaveMinutes(rs.getInt("early_leave_minutes"));
        BigDecimal worked = rs.getBigDecimal("worked_hours");
        record.setWorkedHours(worked == null ? BigDecimal.ZERO : worked);
        BigDecimal overtime = rs.getBigDecimal("overtime_hours");
        record.setOvertimeHours(overtime == null ? BigDecimal.ZERO : overtime);
        record.setRemarks(rs.getString("remarks"));
        record.setCorrectionReason(rs.getString("correction_reason"));
        return record;
    }
}
