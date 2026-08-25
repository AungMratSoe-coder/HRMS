package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.dto.CategoryCount;
import com.ams.hrms.dto.PayrollTrendPoint;
import com.ams.hrms.dto.TrendDay;
import com.ams.hrms.dto.TypeUsage;

/**
 * Read-only aggregation queries powering the dashboard. All statements are
 * PreparedStatements through {@link Sql}; no business logic lives here.
 */
public class DashboardRepository {

    // ------------------------------------------------------------------
    // Headline stats
    // ------------------------------------------------------------------

    public record WorkforceTotals(long total, long active, long newThisMonth) {
    }

    public WorkforceTotals workforceTotals() {
        return new Sql().first(
                "SELECT COUNT(*) AS total, "
                        + "COALESCE(SUM(status = 'ACTIVE'), 0) AS active, "
                        + "COALESCE(SUM(join_date >= DATE_FORMAT(CURDATE(), '%Y-%m-01')), 0) AS new_month "
                        + "FROM employees",
                rs -> new WorkforceTotals(rs.getLong("total"), rs.getLong("active"), rs.getLong("new_month")))
                .orElse(new WorkforceTotals(0, 0, 0));
    }

    public record TodayAttendance(long present, long late, long absent) {
    }

    public TodayAttendance todayAttendance() {
        return new Sql().first(
                "SELECT COALESCE(SUM(status = 'PRESENT'), 0) AS present, "
                        + "COALESCE(SUM(status = 'LATE'), 0) AS late, "
                        + "COALESCE(SUM(status = 'ABSENT'), 0) AS absent "
                        + "FROM attendance WHERE attendance_date = CURDATE()",
                rs -> new TodayAttendance(rs.getLong("present"), rs.getLong("late"), rs.getLong("absent")))
                .orElse(new TodayAttendance(0, 0, 0));
    }

    public long onLeaveToday() {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM leave_requests "
                        + "WHERE status = 'APPROVED' AND CURDATE() BETWEEN start_date AND end_date");
    }

    public long pendingLeaveRequests() {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM leave_requests WHERE status = 'PENDING'");
    }

    /** Latest approved/paid payroll totals; empty when payroll never ran. */
    public Optional<PayrollTrendPoint> latestPayroll() {
        return new Sql().first(
                "SELECT CONCAT(pp.period_year, '-', LPAD(pp.period_month, 2, '0')) AS label, "
                        + "SUM(p.gross_salary) AS gross "
                        + "FROM payrolls p JOIN payroll_periods pp ON pp.id = p.payroll_period_id "
                        + "WHERE p.status IN ('APPROVED', 'PAID') "
                        + "GROUP BY pp.id, label "
                        + "ORDER BY pp.period_year DESC, pp.period_month DESC LIMIT 1",
                rs -> new PayrollTrendPoint(rs.getString("label"), rs.getBigDecimal("gross")));
    }

    public record LatestPayrollNet(BigDecimal net) {
    }

    public BigDecimal latestPayrollNet(String periodLabel) {
        return new Sql().first(
                "SELECT COALESCE(SUM(net_salary), 0) AS net FROM payrolls p "
                        + "JOIN payroll_periods pp ON pp.id = p.payroll_period_id "
                        + "WHERE p.status IN ('APPROVED', 'PAID') AND pp.period_name = ?",
                rs -> rs.getBigDecimal("net"),
                periodLabel).orElse(BigDecimal.ZERO);
    }

    public String payrollCurrency() {
        return new Sql().first(
                "SELECT setting_value FROM app_settings WHERE setting_key = 'payroll.currency'",
                rs -> rs.getString(1)).orElse("USD");
    }

    // ------------------------------------------------------------------
    // Chart data
    // ------------------------------------------------------------------

    public List<CategoryCount> employeesByDepartment() {
        return new Sql().list(
                "SELECT d.dept_name AS label, COUNT(e.id) AS cnt "
                        + "FROM departments d "
                        + "LEFT JOIN employees e ON e.department_id = d.id AND e.status = 'ACTIVE' "
                        + "GROUP BY d.id, d.dept_name "
                        + "ORDER BY cnt DESC, d.dept_name",
                rs -> new CategoryCount(rs.getString("label"), rs.getLong("cnt")));
    }

    public List<CategoryCount> employeesByStatus() {
        return new Sql().list(
                "SELECT status AS label, COUNT(*) AS cnt FROM employees GROUP BY status ORDER BY cnt DESC",
                rs -> new CategoryCount(rs.getString("label"), rs.getLong("cnt")));
    }

    public List<TypeUsage> leaveUsageByType() {
        return new Sql().list(
                "SELECT lt.type_name AS label, COALESCE(SUM(lb.used), 0) AS days "
                        + "FROM leave_types lt "
                        + "LEFT JOIN leave_balances lb ON lb.leave_type_id = lt.id AND lb.balance_year = YEAR(CURDATE()) "
                        + "GROUP BY lt.id, lt.type_name "
                        + "ORDER BY days DESC, lt.type_name",
                rs -> new TypeUsage(rs.getString("label"), rs.getBigDecimal("days")));
    }

    public List<TrendDay> attendanceTrendLast14Days() {
        return new Sql().list(
                "SELECT attendance_date AS day, "
                        + "COALESCE(SUM(status IN ('PRESENT', 'MISSION')), 0) AS present, "
                        + "COALESCE(SUM(status = 'LATE'), 0) AS late, "
                        + "COALESCE(SUM(status = 'ABSENT'), 0) AS absent "
                        + "FROM attendance "
                        + "WHERE attendance_date >= CURDATE() - INTERVAL 13 DAY "
                        + "GROUP BY attendance_date ORDER BY attendance_date",
                rs -> new TrendDay(rs.getObject("day", LocalDate.class),
                        rs.getLong("present"), rs.getLong("late"), rs.getLong("absent")));
    }

    public List<PayrollTrendPoint> payrollCostTrend() {
        return new Sql().list(
                "SELECT CONCAT(pp.period_year, '-', LPAD(pp.period_month, 2, '0')) AS label, "
                        + "COALESCE(SUM(p.gross_salary), 0) AS gross "
                        + "FROM payroll_periods pp "
                        + "LEFT JOIN payrolls p ON p.payroll_period_id = pp.id AND p.status IN ('APPROVED', 'PAID') "
                        + "GROUP BY pp.id, label "
                        + "ORDER BY pp.period_year, pp.period_month LIMIT 12",
                rs -> new PayrollTrendPoint(rs.getString("label"),
                        rs.getBigDecimal("gross") == null ? BigDecimal.ZERO : rs.getBigDecimal("gross")));
    }
}
