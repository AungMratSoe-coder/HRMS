package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.Position;

/** Position persistence (soft-delete via status). */
public class PositionRepository {

    private static final String SELECT =
            "SELECT p.id, p.position_code, p.position_name, p.department_id, "
                    + "p.description, p.min_salary, p.max_salary, p.status, d.dept_name AS department_name "
                    + "FROM positions p JOIN departments d ON d.id = p.department_id";

    public List<Position> findAll(String keyword) {
        String filter = keyword == null ? "" : keyword.trim();
        return new Sql().list(
                SELECT + " WHERE (IFNULL(?, '') = '' "
                        + "OR p.position_name LIKE CONCAT('%', ?, '%') "
                        + "OR p.position_code LIKE CONCAT('%', ?, '%') "
                        + "OR d.dept_name LIKE CONCAT('%', ?, '%')) "
                        + "ORDER BY d.dept_name, p.position_name",
                this::mapRow,
                filter, filter, filter, filter);
    }

    public Optional<Position> findById(long id) {
        return new Sql().first(SELECT + " WHERE p.id = ?", this::mapRow, id);
    }

    public boolean codeExists(String code, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM positions "
                        + "WHERE UPPER(position_code) = UPPER(?) AND (? IS NULL OR id <> ?)",
                code, excludeId, excludeId) > 0;
    }

    public boolean nameExistsInDepartment(String name, long departmentId, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM positions "
                        + "WHERE UPPER(position_name) = UPPER(?) AND department_id = ? "
                        + "AND (? IS NULL OR id <> ?)",
                name, departmentId, excludeId, excludeId) > 0;
    }

    public long insert(Position position) {
        return new Sql().executeInsert(
                "INSERT INTO positions (position_code, position_name, department_id, description, "
                        + "min_salary, max_salary, status, created_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                position.getCode(),
                position.getName(),
                position.getDepartmentId(),
                position.getDescription(),
                position.getMinSalary(),
                position.getMaxSalary(),
                position.getStatus(),
                com.ams.hrms.security.SessionContext.currentUserId());
    }

    public void update(Position position) {
        new Sql().executeUpdate(
                "UPDATE positions SET position_code = ?, position_name = ?, department_id = ?, "
                        + "description = ?, min_salary = ?, max_salary = ? WHERE id = ?",
                position.getCode(),
                position.getName(),
                position.getDepartmentId(),
                position.getDescription(),
                position.getMinSalary(),
                position.getMaxSalary(),
                position.getId());
    }

    public void setStatus(long id, String status) {
        new Sql().executeUpdate("UPDATE positions SET status = ? WHERE id = ?", status, id);
    }

    public long activeEmployeeCount(long positionId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM employees WHERE position_id = ? AND status = 'ACTIVE'",
                positionId);
    }

    private Position mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Position position = new Position();
        position.setId(rs.getLong("id"));
        position.setCode(rs.getString("position_code"));
        position.setName(rs.getString("position_name"));
        position.setDepartmentId(rs.getLong("department_id"));
        position.setDepartmentName(rs.getString("department_name"));
        position.setDescription(rs.getString("description"));
        BigDecimal min = rs.getBigDecimal("min_salary");
        position.setMinSalary(rs.wasNull() ? null : min);
        BigDecimal max = rs.getBigDecimal("max_salary");
        position.setMaxSalary(rs.wasNull() ? null : max);
        position.setStatus(rs.getString("status"));
        return position;
    }
}
