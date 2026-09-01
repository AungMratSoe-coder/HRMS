package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.OvertimeRequest;
import com.ams.hrms.repository.Sql;

/** Overtime request persistence (spec section 19). */
public class OvertimeRepository {

    private static final String SELECT =
            "SELECT o.id, o.overtime_code, o.employee_id, o.request_date, o.hours, o.reason, "
                    + "o.rate_per_hour, o.amount, o.status, o.approved_at, e.employee_code, "
                    + "e.full_name, u.full_name AS approved_by_name "
                    + "FROM overtime_requests o "
                    + "JOIN employees e ON e.id = o.employee_id "
                    + "LEFT JOIN users u ON u.id = o.approved_by";

    public List<OvertimeRequest> findAll(String keyword, String status) {
        return findAll(keyword, status, null);
    }

    /**
     * Listing with filters; {@code restrictToEmployeeId} (self-service scope)
     * limits the result to that employee's requests.
     */
    public List<OvertimeRequest> findAll(String keyword, String status,
                                         Long restrictToEmployeeId) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (restrictToEmployeeId != null) {
            sql.append(" AND o.employee_id = ?");
            params.add(restrictToEmployeeId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.employee_code LIKE CONCAT('%', ?, '%') "
                    + "OR e.full_name LIKE CONCAT('%', ?, '%') "
                    + "OR o.overtime_code LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
            params.add(filter);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND o.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY o.request_date DESC, o.id DESC");
        return new Sql().list(sql.toString(), this::mapRow, params.toArray());
    }

    public Optional<OvertimeRequest> findById(long id) {
        return new Sql().first(SELECT + " WHERE o.id = ?", this::mapRow, id);
    }

    public long insert(OvertimeRequest request) {
        return insert(new Sql(), request);
    }

    public long insert(Sql sql, OvertimeRequest request) {
        return sql.executeInsert(
                "INSERT INTO overtime_requests (overtime_code, employee_id, request_date, hours, "
                        + "reason, status) VALUES ('TMP', ?, ?, ?, ?, 'PENDING')",
                request.getEmployeeId(), request.getRequestDate(),
                request.getHours(), request.getReason());
    }

    public void updateOvertimeCode(long id, String code) {
        updateOvertimeCode(new Sql(), id, code);
    }

    public void updateOvertimeCode(Sql sql, long id, String code) {
        sql.executeUpdate(
                "UPDATE overtime_requests SET overtime_code = ? WHERE id = ?", code, id);
    }

    public void approve(long id, BigDecimal ratePerHour, BigDecimal amount, long approvedBy) {
        new Sql().executeUpdate(
                "UPDATE overtime_requests SET status = 'APPROVED', rate_per_hour = ?, amount = ?, "
                        + "approved_by = ?, approved_at = NOW() WHERE id = ?",
                ratePerHour, amount, approvedBy, id);
    }

    public void reject(long id, long approvedBy) {
        new Sql().executeUpdate(
                "UPDATE overtime_requests SET status = 'REJECTED', approved_by = ?, "
                        + "approved_at = NOW() WHERE id = ?", approvedBy, id);
    }

    private OvertimeRequest mapRow(ResultSet rs) throws SQLException {
        OvertimeRequest request = new OvertimeRequest();
        request.setId(rs.getLong("id"));
        request.setOvertimeCode(rs.getString("overtime_code"));
        request.setEmployeeId(rs.getLong("employee_id"));
        request.setEmployeeCode(rs.getString("employee_code"));
        request.setFullName(rs.getString("full_name"));
        request.setRequestDate(rs.getObject("request_date", LocalDate.class));
        request.setHours(rs.getBigDecimal("hours"));
        request.setReason(rs.getString("reason"));
        BigDecimal rate = rs.getBigDecimal("rate_per_hour");
        request.setRatePerHour(rs.wasNull() ? null : rate);
        BigDecimal amount = rs.getBigDecimal("amount");
        request.setAmount(rs.wasNull() ? null : amount);
        request.setStatus(rs.getString("status"));
        request.setApprovedAt(rs.getObject("approved_at", java.time.LocalDateTime.class));
        request.setApprovedByName(rs.getString("approved_by_name"));
        return request;
    }
}
