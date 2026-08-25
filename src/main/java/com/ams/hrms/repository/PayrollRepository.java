package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Payroll persistence: periods, payroll records, line items and source-data
 * aggregation queries used by the calculation engine.
 */
public class PayrollRepository {

    private static final String SELECT_PAYROLL =
            "SELECT p.id, p.payroll_number, p.employee_id, p.payroll_period_id, p.currency, "
                    + "p.basic_salary, p.total_allowance, p.total_bonus, p.total_overtime, "
                    + "p.gross_salary, p.tax_amount, p.social_security, p.loan_deduction, "
                    + "p.other_deduction, p.total_deduction, p.net_salary, p.status, "
                    + "e.employee_code, e.full_name, d.dept_name AS department_name, "
                    + "pp.period_name "
                    + "FROM payrolls p "
                    + "JOIN employees e ON e.id = p.employee_id "
                    + "LEFT JOIN departments d ON d.id = e.department_id "
                    + "JOIN payroll_periods pp ON pp.id = p.payroll_period_id";

    // ------------------------------------------------------------------
    // Periods
    // ------------------------------------------------------------------

    public record Period(long id, String periodName, int year, int month,
                         LocalDate startDate, LocalDate endDate, String status) {
    }

    public long findOrCreatePeriod(int year, int month) {
        String periodName = String.format("%d-%02d", year, month);
        Optional<Long> existing = new Sql().first(
                "SELECT id FROM payroll_periods WHERE period_name = ?",
                rs -> rs.getLong(1), periodName);
        if (existing.isPresent()) {
            return existing.get();
        }
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1).minusDays(1);
        return new Sql().executeInsert(
                "INSERT INTO payroll_periods (period_name, period_year, period_month, "
                        + "start_date, end_date, status) VALUES (?, ?, ?, ?, ?, 'OPEN')",
                periodName, year, month, start, end);
    }

    public List<Period> allPeriods() {
        return new Sql().list(
                "SELECT id, period_name, period_year, period_month, start_date, end_date, status "
                        + "FROM payroll_periods ORDER BY period_year DESC, period_month DESC",
                rs -> new Period(rs.getLong("id"), rs.getString("period_name"),
                        rs.getInt("period_year"), rs.getInt("period_month"),
                        rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class),
                        rs.getString("status")));
    }

    public void lockPeriod(long periodId) {
        new Sql().executeUpdate(
                "UPDATE payroll_periods SET status = 'LOCKED' WHERE id = ?", periodId);
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    public BigDecimal settingDecimal(String key, BigDecimal fallback) {
        try {
            String raw = new Sql().first(
                    "SELECT setting_value FROM app_settings WHERE setting_key = ?",
                    rs -> rs.getString(1), key).orElse("");
            return raw.isBlank() ? fallback : new BigDecimal(raw.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public String currency() {
        return new Sql().first(
                "SELECT setting_value FROM app_settings WHERE setting_key = 'payroll.currency'",
                rs -> rs.getString(1)).orElse("USD");
    }

    // ------------------------------------------------------------------
    // Active employees for calculation
    // ------------------------------------------------------------------

    public record EmployeePayrollData(long employeeId, String code, String fullName,
                                      String departmentName, BigDecimal basicSalary) {
    }

    public List<EmployeePayrollData> activeEmployeesForPayroll() {
        return new Sql().list(
                "SELECT e.id, e.employee_code, e.full_name, "
                        + "COALESCE(d.dept_name, '-') AS department_name, e.basic_salary "
                        + "FROM employees e LEFT JOIN departments d ON d.id = e.department_id "
                        + "WHERE e.status = 'ACTIVE' ORDER BY e.employee_code",
                rs -> new EmployeePayrollData(rs.getLong("id"), rs.getString("employee_code"),
                        rs.getString("full_name"), rs.getString("department_name"),
                        rs.getBigDecimal("basic_salary")));
    }

    // ------------------------------------------------------------------
    // Source data
    // ------------------------------------------------------------------

    public BigDecimal allowanceTotal(long employeeId, LocalDate from, LocalDate to) {
        return nz(firstBigDecimal(
                "SELECT COALESCE(SUM(amount), 0) FROM allowances "
                        + "WHERE employee_id = ? AND effective_from <= ? "
                        + "AND (effective_to IS NULL OR effective_to >= ?)",
                employeeId, to, from));
    }

    public BigDecimal bonusTotal(long employeeId, LocalDate from, LocalDate to) {
        return nz(firstBigDecimal(
                "SELECT COALESCE(SUM(amount), 0) FROM bonuses "
                        + "WHERE employee_id = ? AND bonus_date BETWEEN ? AND ?",
                employeeId, from, to));
    }

    public BigDecimal overtimeTotal(long employeeId, LocalDate from, LocalDate to) {
        return nz(firstBigDecimal(
                "SELECT COALESCE(SUM(amount), 0) FROM overtime_requests "
                        + "WHERE employee_id = ? AND request_date BETWEEN ? AND ? "
                        + "AND status = 'APPROVED'",
                employeeId, from, to));
    }

    public BigDecimal otherDeductionTotal(long employeeId, LocalDate from, LocalDate to) {
        return nz(firstBigDecimal(
                "SELECT COALESCE(SUM(CASE WHEN is_percentage = 0 THEN amount ELSE 0 END), 0) "
                        + "FROM deductions WHERE employee_id = ? AND effective_from <= ? "
                        + "AND (effective_to IS NULL OR effective_to >= ?) "
                        + "AND deduction_type NOT IN ('TAX', 'SOCIAL_SECURITY')",
                employeeId, to, from));
    }

    // ------------------------------------------------------------------
    // Payroll CRUD
    // ------------------------------------------------------------------

    public boolean existsForEmployeePeriod(long employeeId, long periodId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM payrolls WHERE employee_id = ? AND payroll_period_id = ?",
                employeeId, periodId) > 0;
    }

    /** Payroll display row for tables. */
    public record PayrollRow(long id, String payrollNumber, String employeeCode,
                             String fullName, String departmentName, String periodName,
                             BigDecimal basicSalary, BigDecimal grossSalary,
                             BigDecimal totalDeduction, BigDecimal netSalary,
                             String currency, String status) {
    }

    public List<PayrollRow> findByPeriod(long periodId) {
        return new Sql().list(
                SELECT_PAYROLL + " WHERE p.payroll_period_id = ? ORDER BY e.employee_code",
                this::mapRow, periodId);
    }

    /** All payroll rows of one employee, newest first (profile view). */
    public List<PayrollRow> findByEmployee(long employeeId) {
        return new Sql().list(
                SELECT_PAYROLL + " WHERE p.employee_id = ? ORDER BY p.id DESC",
                this::mapRow, employeeId);
    }

    public Optional<Long> findOpenPeriodId() {
        return new Sql().first(
                "SELECT id FROM payroll_periods WHERE status = 'OPEN' LIMIT 1",
                rs -> rs.getLong(1));
    }

    public long insertPayroll(long employeeId, long periodId, String number, String currency,
                              BigDecimal basic, BigDecimal gross, BigDecimal tax, BigDecimal ss,
                              BigDecimal other, BigDecimal totalDed, BigDecimal net,
                              long calculatedBy) {
        return new Sql().executeInsert(
                "INSERT INTO payrolls (payroll_number, employee_id, payroll_period_id, currency, "
                        + "basic_salary, gross_salary, tax_amount, social_security, "
                        + "loan_deduction, other_deduction, total_deduction, net_salary, "
                        + "status, calculated_at, calculated_by) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, 'CALCULATED', NOW(), ?)",
                number, employeeId, periodId, currency, basic, gross, tax, ss,
                other, totalDed, net, calculatedBy);
    }

    public void transition(long id, String newStatus, long userId) {
        String column = switch (newStatus) {
            case "REVIEWED" -> "reviewed";
            case "APPROVED" -> "approved";
            case "PAID" -> "paid";
            default -> throw new IllegalArgumentException("Unknown status: " + newStatus);
        };
        new Sql().executeUpdate(
                "UPDATE payrolls SET status = ?, " + column + "_at = NOW(), "
                        + column + "_by = ? WHERE id = ?",
                newStatus, userId, id);
    }

    public void lockAllInPeriod(long periodId) {
        new Sql().executeUpdate(
                "UPDATE payrolls SET status = 'PAID' WHERE payroll_period_id = ? AND status = 'APPROVED'",
                periodId);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private BigDecimal firstBigDecimal(String sql, Object... params) {
        return new Sql().first(sql, rs -> rs.getBigDecimal(1), params)
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private PayrollRow mapRow(ResultSet rs) throws SQLException {
        return new PayrollRow(
                rs.getLong("id"),
                rs.getString("payroll_number"),
                rs.getString("employee_code"),
                rs.getString("full_name"),
                rs.getString("department_name") == null ? "-" : rs.getString("department_name"),
                rs.getString("period_name"),
                rs.getBigDecimal("basic_salary"),
                rs.getBigDecimal("gross_salary"),
                rs.getBigDecimal("total_deduction"),
                rs.getBigDecimal("net_salary"),
                rs.getString("currency"),
                rs.getString("status"));
    }
}
