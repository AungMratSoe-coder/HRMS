package com.ams.hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.Shift;

/** Shift definition persistence (soft-delete via status). */
public class ShiftRepository {

    private static final String SELECT =
            "SELECT id, shift_code, shift_name, start_time, end_time, grace_minutes, "
                    + "break_minutes, description, status FROM shifts";

    public List<Shift> findAll(String keyword) {
        String filter = keyword == null ? "" : keyword.trim();
        return new Sql().list(
                SELECT + " WHERE (IFNULL(?, '') = '' "
                        + "OR shift_name LIKE CONCAT('%', ?, '%') "
                        + "OR shift_code LIKE CONCAT('%', ?, '%')) "
                        + "ORDER BY start_time, shift_name",
                this::mapRow, filter, filter, filter);
    }

    public Optional<Shift> findById(long id) {
        return new Sql().first(SELECT + " WHERE id = ?", this::mapRow, id);
    }

    public boolean codeExists(String code, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM shifts WHERE UPPER(shift_code) = UPPER(?) AND (? IS NULL OR id <> ?)",
                code, excludeId, excludeId) > 0;
    }

    public boolean nameExists(String name, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM shifts WHERE UPPER(shift_name) = UPPER(?) AND (? IS NULL OR id <> ?)",
                name, excludeId, excludeId) > 0;
    }

    public long insert(Shift shift) {
        return new Sql().executeInsert(
                "INSERT INTO shifts (shift_code, shift_name, start_time, end_time, grace_minutes, "
                        + "break_minutes, description, status) VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE')",
                shift.getCode(), shift.getName(), shift.getStartTime(), shift.getEndTime(),
                shift.getGraceMinutes(), shift.getBreakMinutes(), shift.getDescription());
    }

    public void update(Shift shift) {
        new Sql().executeUpdate(
                "UPDATE shifts SET shift_code = ?, shift_name = ?, start_time = ?, end_time = ?, "
                        + "grace_minutes = ?, break_minutes = ?, description = ? WHERE id = ?",
                shift.getCode(), shift.getName(), shift.getStartTime(), shift.getEndTime(),
                shift.getGraceMinutes(), shift.getBreakMinutes(), shift.getDescription(),
                shift.getId());
    }

    public void setStatus(long id, String status) {
        new Sql().executeUpdate("UPDATE shifts SET status = ? WHERE id = ?", status, id);
    }

    /** Assignments active as of today referencing the shift. */
    public long openAssignmentCount(long shiftId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM employee_shifts "
                        + "WHERE shift_id = ? AND effective_from <= CURDATE() "
                        + "AND (effective_to IS NULL OR effective_to >= CURDATE())", shiftId);
    }

    private Shift mapRow(ResultSet rs) throws SQLException {
        Shift shift = new Shift();
        shift.setId(rs.getLong("id"));
        shift.setCode(rs.getString("shift_code"));
        shift.setName(rs.getString("shift_name"));
        shift.setStartTime(rs.getObject("start_time", java.time.LocalTime.class));
        shift.setEndTime(rs.getObject("end_time", java.time.LocalTime.class));
        shift.setGraceMinutes(rs.getInt("grace_minutes"));
        shift.setBreakMinutes(rs.getInt("break_minutes"));
        shift.setDescription(rs.getString("description"));
        shift.setStatus(rs.getString("status"));
        return shift;
    }
}
