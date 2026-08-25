package com.ams.hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.EmployeeShift;

/**
 * Effective-dated employee-shift assignments. One open-ended assignment per
 * employee is maintained by {@code ShiftService}; ranges never overlap.
 */
public class EmployeeShiftRepository {

    private static final String SELECT =
            "SELECT es.id, es.employee_id, es.shift_id, es.effective_from, es.effective_to, "
                    + "CONCAT(e.employee_code, ' - ', e.full_name) AS employee_display, "
                    + "s.shift_name AS shift_name, u.full_name AS assigned_by_name "
                    + "FROM employee_shifts es "
                    + "JOIN employees e ON e.id = es.employee_id "
                    + "JOIN shifts s ON s.id = es.shift_id "
                    + "LEFT JOIN users u ON u.id = es.assigned_by";

    /** Current assignments across all employees (Assignments tab). */
    public List<EmployeeShift> findCurrent() {
        return findCurrent(null);
    }

    /**
     * Current assignments; {@code restrictToEmployeeId} (self-service scope)
     * limits the result to that employee's row.
     */
    public List<EmployeeShift> findCurrent(Long restrictToEmployeeId) {
        StringBuilder sql = new StringBuilder(SELECT)
                .append(" WHERE es.effective_to IS NULL");
        List<Object> params = new ArrayList<>();
        if (restrictToEmployeeId != null) {
            sql.append(" AND es.employee_id = ?");
            params.add(restrictToEmployeeId);
        }
        sql.append(" ORDER BY s.start_time, e.full_name");
        return new Sql().list(sql.toString(), this::mapRow, params.toArray());
    }

    /** Full assignment history for one employee, newest first. */
    public List<EmployeeShift> findByEmployee(long employeeId) {
        return new Sql().list(
                SELECT + " WHERE es.employee_id = ? ORDER BY es.effective_from DESC",
                this::mapRow, employeeId);
    }

    /** The open-ended assignment for an employee, when present. */
    public Optional<EmployeeShift> findOpenForEmployee(long employeeId) {
        return new Sql().first(
                SELECT + " WHERE es.employee_id = ? AND es.effective_to IS NULL",
                this::mapRow, employeeId);
    }

    /**
     * True when an existing assignment would conflict with a NEW assignment
     * starting at {@code from} and running indefinitely (or to {@code to}).
     * Touching ranges are allowed: an old row may END on {@code from}.
     */
    public boolean overlaps(long employeeId, LocalDate from, LocalDate to, Long excludeAssignmentId) {
        String rangeEnd = (to == null) ? "9999-12-31" : to.toString();
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM employee_shifts "
                        + "WHERE employee_id = ? "
                        + "AND effective_from <= ? "
                        + "AND (effective_to IS NULL OR effective_to > ?) "
                        + "AND (? IS NULL OR id <> ?)",
                employeeId, rangeEnd, from,
                excludeAssignmentId, excludeAssignmentId) > 0;
    }

    /**
     * True when a FUTURE-SCHEDULED assignment exists for the employee
     * (starting after today). Such planned rows must not be silently replaced.
     */
    public boolean hasFutureAssignment(long employeeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM employee_shifts "
                        + "WHERE employee_id = ? AND effective_from > CURDATE()",
                employeeId) > 0;
    }

    /** True when an assignment already starts exactly on {@code date}. */
    public boolean hasAssignmentStartingAt(long employeeId, LocalDate date) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM employee_shifts "
                        + "WHERE employee_id = ? AND effective_from = ?",
                employeeId, date) > 0;
    }

    /** Shift name of the assignment starting exactly on {@code date}, when present. */
    public Optional<String> shiftNameStartingAt(long employeeId, LocalDate date) {
        return new Sql().first(
                "SELECT s.shift_name AS name FROM employee_shifts es "
                        + "JOIN shifts s ON s.id = es.shift_id "
                        + "WHERE es.employee_id = ? AND es.effective_from = ?",
                rs -> rs.getString("name"), employeeId, date);
    }

    /** Inserts an assignment; returns its id. Caller runs inside a transaction. */
    public long insert(Sql sql, EmployeeShift assignment) {
        return sql.executeInsert(
                "INSERT INTO employee_shifts (employee_id, shift_id, effective_from, effective_to, assigned_by) "
                        + "VALUES (?, ?, ?, ?, ?)",
                assignment.getEmployeeId(),
                assignment.getShiftId(),
                assignment.getEffectiveFrom(),
                assignment.getEffectiveTo(),
                com.ams.hrms.security.SessionContext.currentUserId());
    }

    /** Closes the open assignment as of {@code effectiveTo}. */
    public void closeOpen(Sql sql, long employeeId, LocalDate effectiveTo) {
        sql.executeUpdate(
                "UPDATE employee_shifts SET effective_to = ? "
                        + "WHERE employee_id = ? AND effective_to IS NULL",
                effectiveTo, employeeId);
    }

    public void endAssignment(long assignmentId, LocalDate endDate) {
        new Sql().executeUpdate(
                "UPDATE employee_shifts SET effective_to = ? WHERE id = ?", endDate, assignmentId);
    }

    private EmployeeShift mapRow(ResultSet rs) throws SQLException {
        EmployeeShift assignment = new EmployeeShift();
        assignment.setId(rs.getLong("id"));
        assignment.setEmployeeId(rs.getLong("employee_id"));
        assignment.setEmployeeDisplay(rs.getString("employee_display"));
        assignment.setShiftId(rs.getLong("shift_id"));
        assignment.setShiftName(rs.getString("shift_name"));
        assignment.setEffectiveFrom(rs.getObject("effective_from", LocalDate.class));
        LocalDate to = rs.getObject("effective_to", LocalDate.class);
        assignment.setEffectiveTo(rs.wasNull() ? null : to);
        assignment.setAssignedByName(rs.getString("assigned_by_name"));
        return assignment;
    }
}
