package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.EmployeeLeaveRequest;

/** Leave request / balance / type persistence (spec section 18). */
public class LeaveRepository {

    private static final String SELECT =
            "SELECT lr.id, lr.leave_code, lr.employee_id, lr.leave_type_id, lr.start_date, "
                    + "lr.end_date, lr.number_of_days, lr.reason, lr.status, lr.decided_at, "
                    + "lr.rejection_reason, lt.type_name, e.employee_code, e.full_name, "
                    + "u.full_name AS decided_by_name "
                    + "FROM leave_requests lr "
                    + "JOIN leave_types lt ON lt.id = lr.leave_type_id "
                    + "JOIN employees e ON e.id = lr.employee_id "
                    + "LEFT JOIN users u ON u.id = lr.decided_by";

    // ------------------------------------------------------------------
    // Requests
    // ------------------------------------------------------------------

    public List<EmployeeLeaveRequest> findAll(String keyword, String status, Long typeId) {
        return findAll(keyword, status, typeId, null);
    }

    /**
     * Listing with filters; {@code restrictToEmployeeId} (self-service scope)
     * limits the result to that employee's requests.
     */
    public List<EmployeeLeaveRequest> findAll(String keyword, String status, Long typeId,
                                              Long restrictToEmployeeId) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (restrictToEmployeeId != null) {
            sql.append(" AND lr.employee_id = ?");
            params.add(restrictToEmployeeId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.employee_code LIKE CONCAT('%', ?, '%') "
                    + "OR e.full_name LIKE CONCAT('%', ?, '%') "
                    + "OR lr.leave_code LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
            params.add(filter);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND lr.status = ?");
            params.add(status);
        }
        if (typeId != null) {
            sql.append(" AND lr.leave_type_id = ?");
            params.add(typeId);
        }
        sql.append(" ORDER BY lr.id DESC");
        return new Sql().list(sql.toString(), this::mapRequest, params.toArray());
    }

    public List<EmployeeLeaveRequest> findByEmployee(long employeeId) {
        return new Sql().list(SELECT + " WHERE lr.employee_id = ? ORDER BY lr.id DESC",
                this::mapRequest, employeeId);
    }

    public Optional<EmployeeLeaveRequest> findById(long id) {
        return new Sql().first(SELECT + " WHERE lr.id = ?", this::mapRequest, id);
    }

    /** Overlapping APPROVED/PENDING request for the same employee. */
    public boolean overlaps(long employeeId, LocalDate from, LocalDate to, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM leave_requests "
                        + "WHERE employee_id = ? AND status IN ('PENDING', 'APPROVED') "
                        + "AND start_date <= ? AND end_date >= ? "
                        + "AND (? IS NULL OR id <> ?)",
                employeeId, to, from, excludeId, excludeId) > 0;
    }

    public long insertRequest(EmployeeLeaveRequest request) {
        return new Sql().executeInsert(
                "INSERT INTO leave_requests (leave_code, employee_id, leave_type_id, start_date, "
                        + "end_date, number_of_days, reason, status) "
                        + "VALUES ('TMP', ?, ?, ?, ?, ?, ?, 'PENDING')",
                request.getEmployeeId(), request.getLeaveTypeId(), request.getStartDate(),
                request.getEndDate(), request.getNumberOfDays(), request.getReason());
    }

    public void updateLeaveCode(long id, String code) {
        new Sql().executeUpdate("UPDATE leave_requests SET leave_code = ? WHERE id = ?", code, id);
    }

    public void approveRequest(long id, long decidedBy) {
        new Sql().executeUpdate(
                "UPDATE leave_requests SET status = 'APPROVED', decided_by = ?, decided_at = NOW() "
                        + "WHERE id = ?", decidedBy, id);
    }

    public void rejectRequest(long id, long decidedBy, String reason) {
        new Sql().executeUpdate(
                "UPDATE leave_requests SET status = 'REJECTED', decided_by = ?, decided_at = NOW(), "
                        + "rejection_reason = ? WHERE id = ?", decidedBy, reason, id);
    }

    public void cancelRequest(long id) {
        new Sql().executeUpdate(
                "UPDATE leave_requests SET status = 'CANCELLED', decided_at = NOW() WHERE id = ?", id);
    }

    public void insertApproval(long requestId, long approverId, String level,
                               String decision, String comments) {
        new Sql().executeUpdate(
                "INSERT INTO leave_approvals (leave_request_id, approver_id, approval_level, "
                        + "decision, comments) VALUES (?, ?, ?, ?, ?)",
                requestId, approverId, level, decision, comments);
    }

    // ------------------------------------------------------------------
    // Types
    // ------------------------------------------------------------------

    public record LeaveTypeOption(long id, String code, String name, BigDecimal quota,
                                  boolean paid, boolean carryForward, String genderRestriction) {
    }

    public List<LeaveTypeOption> findActiveTypes() {
        return new Sql().list(
                "SELECT id, type_code, type_name, annual_quota, is_paid, carry_forward, gender_restriction "
                        + "FROM leave_types WHERE status = 'ACTIVE' ORDER BY id",
                rs -> new LeaveTypeOption(rs.getLong("id"), rs.getString("type_code"),
                        rs.getString("type_name"), rs.getBigDecimal("annual_quota"),
                        rs.getBoolean("is_paid"), rs.getBoolean("carry_forward"),
                        rs.getString("gender_restriction")));
    }

    // ------------------------------------------------------------------
    // Balances
    // ------------------------------------------------------------------

    public record BalanceRow(long id, long employeeId, long leaveTypeId, String typeName,
                             BigDecimal entitled, BigDecimal carriedForward, BigDecimal used,
                             BigDecimal pending, BigDecimal adjusted) {

        public BigDecimal available() {
            return entitled.add(carriedForward).add(adjusted).subtract(used).subtract(pending);
        }
    }

    public List<BalanceRow> findBalances(long employeeId, int year) {
        return new Sql().list(
                "SELECT lb.id, lb.employee_id, lb.leave_type_id, lt.type_name, lb.entitled, "
                        + "lb.carried_forward, lb.used, lb.pending, lb.adjusted "
                        + "FROM leave_balances lb JOIN leave_types lt ON lt.id = lb.leave_type_id "
                        + "WHERE lb.employee_id = ? AND lb.balance_year = ? ORDER BY lt.id",
                rs -> new BalanceRow(rs.getLong("id"), rs.getLong("employee_id"),
                        rs.getLong("leave_type_id"), rs.getString("type_name"),
                        rs.getBigDecimal("entitled"), rs.getBigDecimal("carried_forward"),
                        rs.getBigDecimal("used"), rs.getBigDecimal("pending"),
                        rs.getBigDecimal("adjusted")),
                employeeId, year);
    }

    /** Creates the year's balance row from the type quota when missing. */
    public long ensureBalance(long employeeId, long leaveTypeId, int year) {
        long existing = new Sql().scalarLong(
                "SELECT COUNT(*) FROM leave_balances "
                        + "WHERE employee_id = ? AND leave_type_id = ? AND balance_year = ?",
                employeeId, leaveTypeId, year);
        if (existing > 0) {
            return new Sql().scalarLong(
                    "SELECT id FROM leave_balances "
                            + "WHERE employee_id = ? AND leave_type_id = ? AND balance_year = ?",
                    employeeId, leaveTypeId, year);
        }
        return new Sql().executeInsert(
                "INSERT INTO leave_balances (employee_id, leave_type_id, balance_year, entitled) "
                        + "SELECT ?, ?, ?, lt.annual_quota FROM leave_types lt WHERE lt.id = ?",
                employeeId, leaveTypeId, year, leaveTypeId);
    }

    public void adjustPending(long employeeId, long leaveTypeId, int year, BigDecimal delta) {
        new Sql().executeUpdate(
                "UPDATE leave_balances SET pending = pending + ? "
                        + "WHERE employee_id = ? AND leave_type_id = ? AND balance_year = ?",
                delta, employeeId, leaveTypeId, year);
    }

    public void approveUsage(long employeeId, long leaveTypeId, int year, BigDecimal days) {
        new Sql().executeUpdate(
                "UPDATE leave_balances SET pending = pending - ?, used = used + ? "
                        + "WHERE employee_id = ? AND leave_type_id = ? AND balance_year = ?",
                days, days, employeeId, leaveTypeId, year);
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private EmployeeLeaveRequest mapRequest(ResultSet rs) throws SQLException {
        EmployeeLeaveRequest request = new EmployeeLeaveRequest();
        request.setId(rs.getLong("id"));
        request.setLeaveCode(rs.getString("leave_code"));
        request.setEmployeeId(rs.getLong("employee_id"));
        request.setLeaveTypeId(rs.getLong("leave_type_id"));
        request.setStartDate(rs.getObject("start_date", LocalDate.class));
        request.setEndDate(rs.getObject("end_date", LocalDate.class));
        request.setNumberOfDays(rs.getBigDecimal("number_of_days"));
        request.setReason(rs.getString("reason"));
        request.setStatus(rs.getString("status"));
        request.setDecidedAt(rs.getObject("decided_at", java.time.LocalDateTime.class));
        request.setRejectionReason(rs.getString("rejection_reason"));
        request.setTypeName(rs.getString("type_name"));
        request.setEmployeeCode(rs.getString("employee_code"));
        request.setFullName(rs.getString("full_name"));
        request.setDecidedByName(rs.getString("decided_by_name"));
        return request;
    }
}
