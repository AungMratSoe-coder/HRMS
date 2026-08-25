package com.ams.hrms.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.Resignation;
import com.ams.hrms.model.Termination;

/** Separation persistence: resignations and terminations (spec section 26). */
public class SeparationRepository {

    // ------------------------------------------------------------------
    // Resignations
    // ------------------------------------------------------------------

    private static final String SELECT_RESIGNATION =
            "SELECT r.id, r.resignation_code, r.employee_id, r.resignation_date, "
                    + "r.last_working_date, r.notice_period_days, r.reason, r.status, "
                    + "r.approved_by, r.approved_at, r.exit_interview_notes, "
                    + "e.employee_code, e.full_name AS employee_name "
                    + "FROM resignations r "
                    + "JOIN employees e ON e.id = r.employee_id";

    public List<Resignation> findResignations(String keyword, String status) {
        StringBuilder sql = new StringBuilder(SELECT_RESIGNATION).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.full_name LIKE CONCAT('%', ?, '%') "
                    + "OR e.employee_code LIKE CONCAT('%', ?, '%') "
                    + "OR r.resignation_code LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
            params.add(filter);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND r.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY r.id DESC");
        return new Sql().list(sql.toString(), this::mapResignation, params.toArray());
    }

    public Optional<Resignation> findResignationById(long id) {
        return new Sql().first(SELECT_RESIGNATION + " WHERE r.id = ?",
                this::mapResignation, id);
    }

    /** True when the employee already has an open (non-terminal) resignation. */
    public boolean hasOpenResignation(long employeeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM resignations WHERE employee_id = ? "
                        + "AND status IN ('SUBMITTED', 'APPROVED')",
                employeeId) > 0;
    }

    public long insertResignation(Resignation resignation) {
        return new Sql().executeInsert(
                "INSERT INTO resignations (resignation_code, employee_id, resignation_date, "
                        + "last_working_date, notice_period_days, reason, status) "
                        + "VALUES ('TMP', ?, ?, ?, ?, ?, 'SUBMITTED')",
                resignation.getEmployeeId(), resignation.getResignationDate(),
                resignation.getLastWorkingDate(), resignation.getNoticePeriodDays(),
                resignation.getReason());
    }

    public void updateResignationCode(long id, String code) {
        new Sql().executeUpdate(
                "UPDATE resignations SET resignation_code = ? WHERE id = ?", code, id);
    }

    public void updateResignationStatus(long id, String status) {
        boolean approving = Resignation.STATUS_APPROVED.equals(status)
                || Resignation.STATUS_PROCESSED.equals(status);
        String sql = "UPDATE resignations SET status = ?"
                + (approving ? ", approved_by = ?, approved_at = NOW()" : "")
                + " WHERE id = ?";
        if (approving) {
            new Sql().executeUpdate(sql, status,
                    com.ams.hrms.security.SessionContext.currentUserId(), id);
        } else {
            new Sql().executeUpdate(sql, status, id);
        }
    }

    public void updateExitInterviewNotes(long id, String notes) {
        new Sql().executeUpdate(
                "UPDATE resignations SET exit_interview_notes = ? WHERE id = ?",
                notes, id);
    }

    // ------------------------------------------------------------------
    // Terminations
    // ------------------------------------------------------------------

    private static final String SELECT_TERMINATION =
            "SELECT t.id, t.termination_code, t.employee_id, t.termination_date, "
                    + "t.reason_category, t.reason, t.approved_by, t.approved_at, "
                    + "t.eligible_rehire, t.notes, e.employee_code, e.full_name AS employee_name, "
                    + "u.full_name AS approved_by_name "
                    + "FROM terminations t "
                    + "JOIN employees e ON e.id = t.employee_id "
                    + "LEFT JOIN users u ON u.id = t.approved_by";

    public List<Termination> findTerminations(String keyword) {
        StringBuilder sql = new StringBuilder(SELECT_TERMINATION).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.full_name LIKE CONCAT('%', ?, '%') "
                    + "OR e.employee_code LIKE CONCAT('%', ?, '%') "
                    + "OR t.termination_code LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
            params.add(filter);
        }
        sql.append(" ORDER BY t.id DESC");
        return new Sql().list(sql.toString(), this::mapTermination, params.toArray());
    }

    public long insertTermination(Termination termination) {
        return new Sql().executeInsert(
                "INSERT INTO terminations (termination_code, employee_id, termination_date, "
                        + "reason_category, reason, approved_by, approved_at, eligible_rehire, "
                        + "notes) VALUES ('TMP', ?, ?, ?, ?, ?, NOW(), ?, ?)",
                termination.getEmployeeId(), termination.getTerminationDate(),
                termination.getReasonCategory(), termination.getReason(),
                com.ams.hrms.security.SessionContext.currentUserId(),
                termination.isEligibleRehire(), termination.getNotes());
    }

    public void updateTerminationCode(long id, String code) {
        new Sql().executeUpdate(
                "UPDATE terminations SET termination_code = ? WHERE id = ?", code, id);
    }

    // ------------------------------------------------------------------
    // Exit checklist support
    // ------------------------------------------------------------------

    /**
     * Voids pre-approval payroll rows for a separating employee so no ghost
     * payroll is processed; REVIEWED and beyond stay as immutable history.
     * Runs inside the caller's transaction.
     *
     * @return number of payroll rows cancelled
     */
    public int cancelDraftPayrolls(Sql sql, long employeeId) {
        return sql.executeUpdate(
                "UPDATE payrolls SET status = 'CANCELLED', remarks = 'Voided on separation' "
                        + "WHERE employee_id = ? AND status IN ('DRAFT', 'CALCULATED')",
                employeeId);
    }

    /** True when the employee currently holds ACTIVE employment status. */
    public boolean isActiveEmployee(long employeeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM employees WHERE id = ? AND status = 'ACTIVE'",
                employeeId) > 0;
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private Resignation mapResignation(ResultSet rs) throws SQLException {
        Resignation resignation = new Resignation();
        resignation.setId(rs.getLong("id"));
        resignation.setResignationCode(rs.getString("resignation_code"));
        resignation.setEmployeeId(rs.getLong("employee_id"));
        resignation.setResignationDate(rs.getObject("resignation_date", LocalDate.class));
        resignation.setLastWorkingDate(rs.getObject("last_working_date", LocalDate.class));
        resignation.setNoticePeriodDays(rs.getInt("notice_period_days"));
        resignation.setReason(rs.getString("reason"));
        resignation.setStatus(rs.getString("status"));
        long approvedBy = rs.getLong("approved_by");
        resignation.setApprovedBy(rs.wasNull() ? null : approvedBy);
        resignation.setApprovedAt(rs.getObject("approved_at", LocalDateTime.class));
        resignation.setExitInterviewNotes(rs.getString("exit_interview_notes"));
        resignation.setEmployeeCode(rs.getString("employee_code"));
        resignation.setEmployeeName(rs.getString("employee_name"));
        return resignation;
    }

    private Termination mapTermination(ResultSet rs) throws SQLException {
        Termination termination = new Termination();
        termination.setId(rs.getLong("id"));
        termination.setTerminationCode(rs.getString("termination_code"));
        termination.setEmployeeId(rs.getLong("employee_id"));
        termination.setTerminationDate(rs.getObject("termination_date", LocalDate.class));
        termination.setReasonCategory(rs.getString("reason_category"));
        termination.setReason(rs.getString("reason"));
        long approvedBy = rs.getLong("approved_by");
        termination.setApprovedBy(rs.wasNull() ? null : approvedBy);
        termination.setApprovedAt(rs.getObject("approved_at", LocalDateTime.class));
        termination.setEligibleRehire(rs.getBoolean("eligible_rehire"));
        termination.setNotes(rs.getString("notes"));
        termination.setEmployeeCode(rs.getString("employee_code"));
        termination.setEmployeeName(rs.getString("employee_name"));
        termination.setApprovedByName(rs.getString("approved_by_name"));
        return termination;
    }
}
