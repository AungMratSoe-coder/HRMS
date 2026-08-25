package com.ams.hrms.repository;

import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.Department;

/**
 * Department persistence. Soft-delete via status; physical deletes never
 * happen from application code.
 */
public class DepartmentRepository {

    private static final String SELECT =
            "SELECT d.id, d.dept_code, d.dept_name, d.description, d.manager_id, "
                    + "d.status, e.full_name AS manager_name "
                    + "FROM departments d LEFT JOIN employees e ON e.id = d.manager_id";

    /** Lists departments, optionally filtered by code/name substring. */
    public List<Department> findAll(String keyword) {
        String filter = keyword == null ? "" : keyword.trim();
        return new Sql().list(
                SELECT + " WHERE (IFNULL(?, '') = '' "
                        + "OR d.dept_name LIKE CONCAT('%', ?, '%') "
                        + "OR d.dept_code LIKE CONCAT('%', ?, '%')) "
                        + "ORDER BY d.dept_name",
                this::mapRow,
                filter, filter, filter);
    }

    public Optional<Department> findById(long id) {
        return new Sql().first(SELECT + " WHERE d.id = ?", this::mapRow, id);
    }

    public boolean codeExists(String code, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM departments "
                        + "WHERE UPPER(dept_code) = UPPER(?) AND (? IS NULL OR id <> ?)",
                code, excludeId, excludeId) > 0;
    }

    public boolean nameExists(String name, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM departments "
                        + "WHERE UPPER(dept_name) = UPPER(?) AND (? IS NULL OR id <> ?)",
                name, excludeId, excludeId) > 0;
    }

    public long insert(Department department) {
        return new Sql().executeInsert(
                "INSERT INTO departments (dept_code, dept_name, description, manager_id, status, created_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                department.getCode(),
                department.getName(),
                department.getDescription(),
                department.getManagerId(),
                department.getStatus(),
                com.ams.hrms.security.SessionContext.currentUserId());
    }

    public void update(Department department) {
        new Sql().executeUpdate(
                "UPDATE departments SET dept_code = ?, dept_name = ?, description = ?, manager_id = ? "
                        + "WHERE id = ?",
                department.getCode(),
                department.getName(),
                department.getDescription(),
                department.getManagerId(),
                department.getId());
    }

    public void setStatus(long id, String status) {
        new Sql().executeUpdate("UPDATE departments SET status = ? WHERE id = ?", status, id);
    }

    public long activeEmployeeCount(long departmentId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM employees WHERE department_id = ? AND status = 'ACTIVE'",
                departmentId);
    }

    public long activePositionCount(long departmentId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM positions WHERE department_id = ? AND status = 'ACTIVE'",
                departmentId);
    }

    private Department mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Department department = new Department();
        department.setId(rs.getLong("id"));
        department.setCode(rs.getString("dept_code"));
        department.setName(rs.getString("dept_name"));
        department.setDescription(rs.getString("description"));
        long managerId = rs.getLong("manager_id");
        department.setManagerId(rs.wasNull() ? null : managerId);
        department.setManagerName(rs.getString("manager_name"));
        department.setStatus(rs.getString("status"));
        return department;
    }
}
