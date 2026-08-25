package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.Employee;
import com.ams.hrms.security.SessionContext;

/**
 * Employee persistence. Mutations run inside {@link TransactionManager} so an
 * employee change, its salary-structure row and its history entry commit or
 * roll back together (spec section 46 rule: history is never lost).
 */
public class EmployeeRepository {

    private static final String SELECT =
            "SELECT e.id, e.employee_code, e.first_name, e.last_name, e.gender, e.date_of_birth, "
                    + "e.nrc, e.phone, e.email, e.address, e.photo_path, e.join_date, "
                    + "e.employment_type, e.department_id, e.position_id, e.manager_id, "
                    + "e.basic_salary, e.status, d.dept_name AS department_name, "
                    + "p.position_name, m.full_name AS manager_name "
                    + "FROM employees e "
                    + "LEFT JOIN departments d ON d.id = e.department_id "
                    + "LEFT JOIN positions p ON p.id = e.position_id "
                    + "LEFT JOIN employees m ON m.id = e.manager_id";

    /** Search/filter criterion bundle for the list screen. */
    public record Filter(String keyword, Long departmentId, Long positionId, String status) {
    }

    public List<Employee> findAll(Filter filter) {
        return findAll(filter, null);
    }

    /** Same as {@link #findAll(Filter)} but optionally pinned to one employee (self-service scope). */
    public List<Employee> findAll(Filter filter, Long restrictToEmployeeId) {
        return new Sql().list(SELECT + whereClause(filter, restrictToEmployeeId)
                        + " ORDER BY e.full_name",
                this::mapRow, filterParams(filter, restrictToEmployeeId).toArray());
    }

    /**
     * One page of the filtered list plus {@link #countMatching(Filter)} power
     * the list screen's server-side pagination (spec sections 37 and 44).
     */
    public List<Employee> findPage(Filter filter, int offset, int limit) {
        return findPage(filter, null, offset, limit);
    }

    /** Paged read with an optional self-service employee restriction. */
    public List<Employee> findPage(Filter filter, Long restrictToEmployeeId,
                                   int offset, int limit) {
        String sql = SELECT + whereClause(filter, restrictToEmployeeId)
                + " ORDER BY e.full_name LIMIT ? OFFSET ?";
        List<Object> params = filterParams(filter, restrictToEmployeeId);
        params.add(limit);
        params.add(Math.max(0, offset));
        return new Sql().list(sql, this::mapRow, params.toArray());
    }

    public long countMatching(Filter filter) {
        return countMatching(filter, null);
    }

    /** Count paired with {@link #findPage(Filter, Long, int, int)}. */
    public long countMatching(Filter filter, Long restrictToEmployeeId) {
        String sql = "SELECT COUNT(*) FROM employees e "
                + whereClause(filter, restrictToEmployeeId);
        return new Sql().scalarLong(sql, filterParams(filter, restrictToEmployeeId).toArray());
    }

    // ------------------------------------------------------------------
    // Filter composition
    // ------------------------------------------------------------------

    private static String whereClause(Filter filter, Long restrictToEmployeeId) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        if (restrictToEmployeeId != null) {
            sql.append(" AND e.id = ?");
        }

        String keyword = filter.keyword() == null ? "" : filter.keyword().trim();
        if (!keyword.isEmpty()) {
            sql.append(" AND (e.employee_code LIKE CONCAT('%', ?, '%') ");
            sql.append("OR e.full_name LIKE CONCAT('%', ?, '%') ");
            sql.append("OR IFNULL(e.phone, '') LIKE CONCAT('%', ?, '%'))");
        }
        if (filter.departmentId() != null) {
            sql.append(" AND e.department_id = ?");
        }
        if (filter.positionId() != null) {
            sql.append(" AND e.position_id = ?");
        }
        String status = filter.status();
        if (status != null && !status.isBlank()) {
            sql.append(" AND e.status = ?");
        }
        return sql.toString();
    }

    /** Parameters in the exact order of {@link #whereClause(Filter, Long)}. */
    private static List<Object> filterParams(Filter filter, Long restrictToEmployeeId) {
        List<Object> params = new ArrayList<>();
        if (restrictToEmployeeId != null) {
            params.add(restrictToEmployeeId);
        }

        String keyword = filter.keyword() == null ? "" : filter.keyword().trim();
        if (!keyword.isEmpty()) {
            params.add(keyword);
            params.add(keyword);
            params.add(keyword);
        }
        if (filter.departmentId() != null) {
            params.add(filter.departmentId());
        }
        if (filter.positionId() != null) {
            params.add(filter.positionId());
        }
        String status = filter.status();
        if (status != null && !status.isBlank()) {
            params.add(status);
        }
        return params;
    }

    public Optional<Employee> findById(long id) {
        return new Sql().first(SELECT + " WHERE e.id = ?", this::mapRow, id);
    }

    /** Employee id sharing the given user email, or empty (self-service link). */
    public Optional<Long> findIdByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return new Sql().first(
                "SELECT id FROM employees WHERE email = ? ORDER BY id LIMIT 1",
                rs -> rs.getLong("id"), email.trim());
    }

    public boolean codeExists(String code, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM employees "
                        + "WHERE UPPER(employee_code) = UPPER(?) AND (? IS NULL OR id <> ?)",
                code, excludeId, excludeId) > 0;
    }

    public boolean nrcExists(String nrc, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM employees "
                        + "WHERE UPPER(nrc) = UPPER(?) AND (? IS NULL OR id <> ?)",
                nrc, excludeId, excludeId) > 0;
    }

    /** Active employees formatted as "CODE - Full Name" (pickers), with the
     *  username of the account that owns the record (null when unlinked). */    public List<EmployeeRepository.EmployeeOption> findActiveOptions() {
        return new Sql().list(
                "SELECT e.id, CONCAT(e.employee_code, ' - ', e.full_name) AS display, "
                        + "u.username AS linked_username "
                        + "FROM employees e "
                        + "LEFT JOIN users u ON u.employee_id = e.id "
                        + "WHERE e.status = 'ACTIVE' ORDER BY e.full_name",
                rs -> new EmployeeOption(rs.getLong("id"), rs.getString("display"),
                        rs.getString("linked_username")));
    }

    public record EmployeeOption(long id, String display, String linkedUsername) {
    }

    /**
     * Next free {@code EMP-####} code, derived from the highest existing
     * number and verified against the unique constraint. Shared by the
     * manual employee dialog (as a prefill) and the recruitment hire flow.
     */
    public String nextEmployeeCode() {
        long maxNumber = new Sql().scalarLong(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(employee_code, 5) AS UNSIGNED)), 0) "
                        + "FROM employees WHERE employee_code REGEXP '^EMP-[0-9]+$'");
        int candidate = (int) maxNumber + 1;
        while (codeExists("EMP-" + String.format("%04d", candidate), null)) {
            candidate++;
        }
        return "EMP-" + String.format("%04d", candidate);
    }

    // ------------------------------------------------------------------
    // Transactional mutations
    // ------------------------------------------------------------------

    /**
     * Inserts the employee, the opening salary structure and the first
     * history entry in one transaction. Returns the generated id.
     */
    public long insert(final Employee employee) {
        return TransactionManager.execute(tx -> {
            long id = tx.executeInsert(
                    "INSERT INTO employees (employee_code, first_name, last_name, gender, date_of_birth, "
                            + "nrc, phone, email, address, join_date, employment_type, department_id, "
                            + "position_id, manager_id, basic_salary, status, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?)",
                    employee.getCode(), employee.getFirstName(), employee.getLastName(),
                    employee.getGender(), employee.getDateOfBirth(), employee.getNrc(),
                    employee.getPhone(), employee.getEmail(), employee.getAddress(),
                    employee.getJoinDate(), employee.getEmploymentType(),
                    employee.getDepartmentId(), employee.getPositionId(), employee.getManagerId(),
                    employee.getBasicSalary(), SessionContext.currentUserId());

            tx.executeUpdate(
                    "INSERT INTO salary_structures (employee_id, basic_salary, currency, effective_from, "
                            + "effective_to, created_by) VALUES (?, ?, "
                            + "COALESCE((SELECT setting_value FROM app_settings WHERE setting_key = 'payroll.currency'), 'USD'), "
                            + "?, NULL, ?)",
                    id, employee.getBasicSalary(), employee.getJoinDate(),
                    SessionContext.currentUserId());

            insertHistory(tx, id, "OTHER", null, "ACTIVE",
                    "Employee record created (" + employee.getCode() + ")");
            return id;
        });
    }

    /**
     * Updates core fields; closes/reopens the salary structure when pay
     * changes and writes history rows for department/position/salary moves.
     */
    public void update(final Employee employee, final Employee old) {
        TransactionManager.execute(tx -> {
            tx.executeUpdate(
                    "UPDATE employees SET employee_code = ?, first_name = ?, last_name = ?, gender = ?, "
                            + "date_of_birth = ?, nrc = ?, phone = ?, email = ?, address = ?, "
                            + "join_date = ?, employment_type = ?, department_id = ?, position_id = ?, "
                            + "manager_id = ?, basic_salary = ? WHERE id = ?",
                    employee.getCode(), employee.getFirstName(), employee.getLastName(),
                    employee.getGender(), employee.getDateOfBirth(), employee.getNrc(),
                    employee.getPhone(), employee.getEmail(), employee.getAddress(),
                    employee.getJoinDate(), employee.getEmploymentType(),
                    employee.getDepartmentId(), employee.getPositionId(), employee.getManagerId(),
                    employee.getBasicSalary(), employee.getId());

            if (changed(old.getBasicSalary(), employee.getBasicSalary())) {
                LocalDate today = LocalDate.now();
                // When does the currently-open structure start?
                LocalDate openFrom = tx.first(
                        "SELECT effective_from AS f FROM salary_structures "
                                + "WHERE employee_id = ? AND effective_to IS NULL",
                        rs -> rs.getObject("f", LocalDate.class),
                        employee.getId()).orElse(null);

                if (openFrom != null && !today.isAfter(openFrom)) {
                    // Structure opened today: amend it in place (keeps chk_ss_range valid).
                    tx.executeUpdate(
                            "UPDATE salary_structures SET basic_salary = ? "
                                    + "WHERE employee_id = ? AND effective_to IS NULL",
                            employee.getBasicSalary(), employee.getId());
                } else {
                    // Close as of today; the new rate starts tomorrow.
                    tx.executeUpdate(
                            "UPDATE salary_structures SET effective_to = ? "
                                    + "WHERE employee_id = ? AND effective_to IS NULL",
                            today, employee.getId());
                    tx.executeUpdate(
                            "INSERT INTO salary_structures (employee_id, basic_salary, currency, "
                                    + "effective_from, effective_to, created_by) VALUES (?, ?, "
                                    + "COALESCE((SELECT setting_value FROM app_settings WHERE setting_key = 'payroll.currency'), 'USD'), "
                                    + "?, NULL, ?)",
                            employee.getId(), employee.getBasicSalary(), today.plusDays(1),
                            SessionContext.currentUserId());
                }
                insertHistory(tx, employee.getId(), "SALARY_CHANGE",
                        moneyText(old.getBasicSalary()), moneyText(employee.getBasicSalary()),
                        "Basic salary adjusted");
            }
            if (changed(old.getDepartmentId(), employee.getDepartmentId())) {
                insertHistory(tx, employee.getId(), "DEPARTMENT_CHANGE",
                        old.getDepartmentName(), employee.getDepartmentName(), null);
            }
            if (changed(old.getPositionId(), employee.getPositionId())) {
                insertHistory(tx, employee.getId(), "POSITION_CHANGE",
                        old.getPositionName(), employee.getPositionName(), null);
            }
            return null;
        });
    }

    public void setStatus(long id, String newStatus) {
        TransactionManager.execute(tx -> {
            setStatusWithinTransaction(tx, id, newStatus);
            return null;
        });
    }

    /**
     * Same status + history pair, bound to the caller's transaction
     * (separation exit checklist). No-op when the status already matches.
     */
    public void setStatusWithinTransaction(Sql tx, long id, String newStatus) {
        List<String> rows = tx.list("SELECT status AS status FROM employees WHERE id = ?",
                rs -> rs.getString("status"), id);
        String previous = rows.isEmpty() ? null : rows.get(0);
        if (newStatus.equals(previous)) {
            return;
        }
        tx.executeUpdate("UPDATE employees SET status = ? WHERE id = ?", newStatus, id);
        insertHistory(tx, id, "STATUS_CHANGE", previous, newStatus, null);
    }

    public void updatePhotoPath(long id, String photoPath) {
        new Sql().executeUpdate("UPDATE employees SET photo_path = ? WHERE id = ?", photoPath, id);
    }

    // ------------------------------------------------------------------
    // History
    // ------------------------------------------------------------------

    public record HistoryEntry(LocalDate effectiveDate, String changeType,
                               String oldValue, String newValue, String remarks) {
    }

    public List<HistoryEntry> findHistory(long employeeId) {
        return new Sql().list(
                "SELECT effective_date, change_type, old_value, new_value, remarks "
                        + "FROM employee_history WHERE employee_id = ? ORDER BY effective_date DESC, id DESC",
                rs -> new HistoryEntry(rs.getObject("effective_date", LocalDate.class),
                        rs.getString("change_type"), rs.getString("old_value"),
                        rs.getString("new_value"), rs.getString("remarks")),
                employeeId);
    }

    private void insertHistory(Sql tx, long employeeId, String type,
                               String oldValue, String newValue, String remarks) {
        tx.executeUpdate(
                "INSERT INTO employee_history (employee_id, change_type, effective_date, "
                        + "old_value, new_value, remarks, recorded_by) VALUES (?, ?, CURDATE(), ?, ?, ?, ?)",
                employeeId, type, oldValue, newValue, remarks, SessionContext.currentUserId());
    }

    private static boolean changed(Object a, Object b) {
        return a == null ? b != null : !a.equals(b);
    }

    private static String moneyText(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }

    private Employee mapRow(ResultSet rs) throws SQLException {
        Employee employee = new Employee();
        employee.setId(rs.getLong("id"));
        employee.setCode(rs.getString("employee_code"));
        employee.setFirstName(rs.getString("first_name"));
        employee.setLastName(rs.getString("last_name"));
        employee.setGender(rs.getString("gender"));
        employee.setDateOfBirth(rs.getObject("date_of_birth", LocalDate.class));
        employee.setNrc(rs.getString("nrc"));
        employee.setPhone(rs.getString("phone"));
        employee.setEmail(rs.getString("email"));
        employee.setAddress(rs.getString("address"));
        employee.setPhotoPath(rs.getString("photo_path"));
        employee.setJoinDate(rs.getObject("join_date", LocalDate.class));
        employee.setEmploymentType(rs.getString("employment_type"));
        employee.setDepartmentId(getNullableLong(rs, "department_id"));
        employee.setPositionId(getNullableLong(rs, "position_id"));
        employee.setManagerId(getNullableLong(rs, "manager_id"));
        BigDecimal salary = rs.getBigDecimal("basic_salary");
        employee.setBasicSalary(salary == null ? BigDecimal.ZERO : salary);
        employee.setStatus(rs.getString("status"));
        employee.setDepartmentName(rs.getString("department_name"));
        employee.setPositionName(rs.getString("position_name"));
        employee.setManagerName(rs.getString("manager_name"));
        return employee;
    }

    private Long getNullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }
}
