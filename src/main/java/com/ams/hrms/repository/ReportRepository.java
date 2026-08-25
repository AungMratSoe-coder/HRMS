package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ams.hrms.report.ReportDefinition;
import com.ams.hrms.report.ReportFilter;
import com.ams.hrms.report.ReportResult;

/**
 * Read-only query source for the standard reports (spec section 27). Every
 * method builds a parameterized statement (never concatenated input) and
 * returns rows whose cell types match the {@link ReportColumn} metadata:
 * String, BigDecimal, LocalDate or null.
 */
public class ReportRepository {

    private static final String COMPANY_FALLBACK = "Company";

    /** Generates any catalog report for the given filter. */
    public ReportResult generate(ReportDefinition definition, ReportFilter filter,
                                 String generatedBy) {
        return switch (definition) {
            case EMPLOYEE_LIST -> employeeList(filter, generatedBy);
            case DEPARTMENT_REPORT -> departmentReport(generatedBy);
            case ATTENDANCE_SUMMARY -> attendanceSummary(filter, generatedBy);
            case LATE_REPORT -> lateReport(filter, generatedBy);
            case ABSENCE_REPORT -> absenceReport(filter, generatedBy);
            case LEAVE_REPORT -> leaveReport(filter, generatedBy);
            case LEAVE_BALANCE -> leaveBalance(filter, generatedBy);
            case OVERTIME_REPORT -> overtimeReport(filter, generatedBy);
            case PAYROLL_REPORT -> payrollReport(filter, generatedBy);
            case SALARY_REPORT -> salaryReport(filter, generatedBy);
            case PERFORMANCE_REPORT -> performanceReport(filter, generatedBy);
            case TRAINING_REPORT -> trainingReport(filter, generatedBy);
            case ASSET_REPORT -> assetReport(filter, generatedBy);
            case TURNOVER_REPORT -> turnoverReport(filter, generatedBy);
        };
    }

    /** Company display name from Settings, used in export headers. */
    public String companyName() {
        return Sql.query("SELECT setting_value FROM app_settings WHERE setting_key = ?",
                rs -> rs.getString(1), "company.name")
                .stream().findFirst().filter(name -> name != null && !name.isBlank())
                .orElse(COMPANY_FALLBACK);
    }

    // ------------------------------------------------------------------
    // Employees
    // ------------------------------------------------------------------

    private ReportResult employeeList(ReportFilter filter, String generatedBy) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendDepartment(where, params, filter.departmentId(), "e.department_id");
        appendStatus(where, params, filter.status(), "e.status");
        if (filter.keywordLike() != null) {
            where.append(" AND (e.full_name LIKE ? OR e.employee_code LIKE ?)");
            params.add(filter.keywordLike());
            params.add(filter.keywordLike());
        }
        List<Object[]> rows = Sql.query("""
                SELECT e.employee_code, e.full_name, COALESCE(d.dept_name, '-'),
                       COALESCE(p.position_name, '-'), e.employment_type,
                       e.join_date, e.basic_salary, e.status
                FROM employees e
                LEFT JOIN departments d ON d.id = e.department_id
                LEFT JOIN positions p ON p.id = e.position_id
                """ + where + " ORDER BY e.employee_code",
                this::mapStandardEmployeeRow, params.toArray());
        return result(ReportDefinition.EMPLOYEE_LIST, filter, rows, null, generatedBy);
    }

    // ------------------------------------------------------------------
    // Departments
    // ------------------------------------------------------------------

    private ReportResult departmentReport(String generatedBy) {
        List<Object[]> rows = Sql.query("""
                SELECT d.dept_code, d.dept_name, COALESCE(m.full_name, '-'),
                       (SELECT COUNT(*) FROM positions p WHERE p.department_id = d.id),
                       COUNT(e.id),
                       COALESCE(SUM(CASE WHEN e.status = 'ACTIVE' THEN 1 END), 0),
                       COALESCE(SUM(CASE WHEN e.status = 'ACTIVE' THEN e.basic_salary END), 0)
                FROM departments d
                LEFT JOIN employees e ON e.department_id = d.id
                LEFT JOIN employees m ON m.id = d.manager_id
                GROUP BY d.id, d.dept_code, d.dept_name, m.full_name
                ORDER BY d.dept_name
                """,
                rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        decimal(rs.getBigDecimal(4)), decimal(rs.getBigDecimal(5)),
                        decimal(rs.getBigDecimal(6)), decimal(rs.getBigDecimal(7))});
        Object[] totals = sumColumns(rows, 3, 4, 5, 6);
        return result(ReportDefinition.DEPARTMENT_REPORT, ReportFilter.between(null, null),
                rows, totals, generatedBy);
    }

    // ------------------------------------------------------------------
    // Attendance
    // ------------------------------------------------------------------

    private ReportResult attendanceSummary(ReportFilter filter, String generatedBy) {
        StringBuilder where = new StringBuilder(
                " WHERE a.attendance_date BETWEEN ? AND ?");
        List<Object> params = new ArrayList<>(List.of(filter.dateFrom(), filter.dateTo()));
        appendDepartment(where, params, filter.departmentId(), "e.department_id");
        List<Object[]> rows = Sql.query("""
                SELECT e.employee_code, e.full_name, COALESCE(d.dept_name, '-'),
                       COALESCE(SUM(a.status = 'PRESENT'), 0),
                       COALESCE(SUM(a.status = 'LATE'), 0),
                       COALESCE(SUM(a.status = 'ABSENT'), 0),
                       COALESCE(SUM(a.status = 'LEAVE'), 0),
                       COALESCE(SUM(a.late_minutes), 0),
                       COALESCE(SUM(a.overtime_hours), 0)
                FROM attendance a
                JOIN employees e ON e.id = a.employee_id
                LEFT JOIN departments d ON d.id = e.department_id
                """ + where + """
                 GROUP BY e.id, e.employee_code, e.full_name, d.dept_name
                 ORDER BY e.full_name
                """,
                rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        decimal(rs.getBigDecimal(4)), decimal(rs.getBigDecimal(5)),
                        decimal(rs.getBigDecimal(6)), decimal(rs.getBigDecimal(7)),
                        decimal(rs.getBigDecimal(8)), decimal(rs.getBigDecimal(9))},
                params.toArray());
        Object[] totals = sumColumns(rows, 3, 4, 5, 6, 7, 8);
        return result(ReportDefinition.ATTENDANCE_SUMMARY, filter, rows, totals, generatedBy);
    }

    private ReportResult lateReport(ReportFilter filter, String generatedBy) {
        StringBuilder where = new StringBuilder(
                " WHERE a.status = 'LATE' AND a.attendance_date BETWEEN ? AND ?");
        List<Object> params = new ArrayList<>(List.of(filter.dateFrom(), filter.dateTo()));
        appendDepartment(where, params, filter.departmentId(), "e.department_id");
        List<Object[]> rows = Sql.query("""
                SELECT a.attendance_date, e.employee_code, e.full_name,
                       COALESCE(d.dept_name, '-'), a.check_in, a.late_minutes
                FROM attendance a
                JOIN employees e ON e.id = a.employee_id
                LEFT JOIN departments d ON d.id = e.department_id
                """ + where + " ORDER BY a.attendance_date DESC, e.full_name",
                this::mapDayRow, params.toArray());
        return result(ReportDefinition.LATE_REPORT, filter, rows, null, generatedBy);
    }

    private ReportResult absenceReport(ReportFilter filter, String generatedBy) {
        StringBuilder where = new StringBuilder(
                " WHERE a.status = 'ABSENT' AND a.attendance_date BETWEEN ? AND ?");
        List<Object> params = new ArrayList<>(List.of(filter.dateFrom(), filter.dateTo()));
        appendDepartment(where, params, filter.departmentId(), "e.department_id");
        List<Object[]> rows = Sql.query("""
                SELECT a.attendance_date, e.employee_code, e.full_name,
                       COALESCE(d.dept_name, '-'), a.status, COALESCE(a.remarks, '')
                FROM attendance a
                JOIN employees e ON e.id = a.employee_id
                LEFT JOIN departments d ON d.id = e.department_id
                """ + where + " ORDER BY a.attendance_date DESC, e.full_name",
                this::mapDayRow, params.toArray());
        return result(ReportDefinition.ABSENCE_REPORT, filter, rows, null, generatedBy);
    }

    // ------------------------------------------------------------------
    // Leave
    // ------------------------------------------------------------------

    private ReportResult leaveReport(ReportFilter filter, String generatedBy) {
        StringBuilder where = new StringBuilder(
                " WHERE lr.start_date <= ? AND lr.end_date >= ?");
        List<Object> params = new ArrayList<>(List.of(filter.dateTo(), filter.dateFrom()));
        appendDepartment(where, params, filter.departmentId(), "e.department_id");
        appendStatus(where, params, filter.status(), "lr.status");
        List<Object[]> rows = Sql.query("""
                SELECT lr.leave_code, e.full_name, COALESCE(d.dept_name, '-'),
                       lt.type_name, lr.start_date, lr.end_date, lr.number_of_days,
                       lr.status, DATE(lr.decided_at)
                FROM leave_requests lr
                JOIN employees e ON e.id = lr.employee_id
                JOIN leave_types lt ON lt.id = lr.leave_type_id
                LEFT JOIN departments d ON d.id = e.department_id
                """ + where + " ORDER BY lr.start_date DESC",
                this::mapLeaveRow, params.toArray());
        Object[] totals = sumColumns(rows, 6);
        return result(ReportDefinition.LEAVE_REPORT, filter, rows, totals, generatedBy);
    }

    private ReportResult leaveBalance(ReportFilter filter, String generatedBy) {
        int year = (filter.dateFrom() != null ? filter.dateFrom() : LocalDate.now()).getYear();
        StringBuilder where = new StringBuilder(" WHERE lb.balance_year = ?");
        List<Object> params = new ArrayList<>(List.of(year));
        appendDepartment(where, params, filter.departmentId(), "e.department_id");
        List<Object[]> rows = Sql.query("""
                SELECT e.employee_code, e.full_name, COALESCE(d.dept_name, '-'),
                       lt.type_name, lb.entitled, lb.carried_forward, lb.used,
                       lb.pending,
                       lb.entitled + lb.carried_forward + lb.adjusted - lb.used - lb.pending
                FROM leave_balances lb
                JOIN employees e ON e.id = lb.employee_id
                JOIN leave_types lt ON lt.id = lb.leave_type_id
                LEFT JOIN departments d ON d.id = e.department_id
                """ + where + " ORDER BY e.full_name, lt.type_name",
                rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        decimal(rs.getBigDecimal(5)), decimal(rs.getBigDecimal(6)),
                        decimal(rs.getBigDecimal(7)), decimal(rs.getBigDecimal(8)),
                        decimal(rs.getBigDecimal(9))},
                params.toArray());
        ReportFilter described = new ReportFilter(LocalDate.of(year, 1, 1),
                LocalDate.of(year, 12, 31), filter.departmentId(),
                filter.departmentName(), null, null);
        return result(ReportDefinition.LEAVE_BALANCE, described, rows, null, generatedBy);
    }

    // ------------------------------------------------------------------
    // Overtime
    // ------------------------------------------------------------------

    private ReportResult overtimeReport(ReportFilter filter, String generatedBy) {
        StringBuilder where = new StringBuilder(
                " WHERE ot.request_date BETWEEN ? AND ?");
        List<Object> params = new ArrayList<>(List.of(filter.dateFrom(), filter.dateTo()));
        appendDepartment(where, params, filter.departmentId(), "e.department_id");
        appendStatus(where, params, filter.status(), "ot.status");
        List<Object[]> rows = Sql.query("""
                SELECT ot.overtime_code, e.full_name, COALESCE(d.dept_name, '-'),
                       ot.request_date, ot.hours,
                       COALESCE(ot.rate_per_hour, 0), COALESCE(ot.amount, 0), ot.status
                FROM overtime_requests ot
                JOIN employees e ON e.id = ot.employee_id
                LEFT JOIN departments d ON d.id = e.department_id
                """ + where + " ORDER BY ot.request_date DESC, e.full_name",
                rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        localDate(rs.getDate(4)),
                        decimal(rs.getBigDecimal(5)), decimal(rs.getBigDecimal(6)),
                        decimal(rs.getBigDecimal(7)), rs.getString(8)},
                params.toArray());
        Object[] totals = sumColumns(rows, 4, 6);
        return result(ReportDefinition.OVERTIME_REPORT, filter, rows, totals, generatedBy);
    }

    // ------------------------------------------------------------------
    // Payroll
    // ------------------------------------------------------------------

    private ReportResult payrollReport(ReportFilter filter, String generatedBy) {
        StringBuilder where = new StringBuilder(
                " WHERE pp.start_date BETWEEN ? AND ?");
        List<Object> params = new ArrayList<>(List.of(filter.dateFrom(), filter.dateTo()));
        appendDepartment(where, params, filter.departmentId(), "e.department_id");
        appendStatus(where, params, filter.status(), "pr.status");
        List<Object[]> rows = Sql.query("""
                SELECT pp.period_name, e.employee_code, e.full_name,
                       COALESCE(d.dept_name, '-'),
                       pr.basic_salary, pr.total_allowance, pr.total_bonus,
                       pr.total_overtime, pr.gross_salary, pr.total_deduction,
                       pr.net_salary, pr.currency, pr.status
                FROM payrolls pr
                JOIN payroll_periods pp ON pp.id = pr.payroll_period_id
                JOIN employees e ON e.id = pr.employee_id
                LEFT JOIN departments d ON d.id = e.department_id
                """ + where + " ORDER BY pp.period_year DESC, pp.period_month DESC, e.full_name",
                rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        decimal(rs.getBigDecimal(5)), decimal(rs.getBigDecimal(6)),
                        decimal(rs.getBigDecimal(7)), decimal(rs.getBigDecimal(8)),
                        decimal(rs.getBigDecimal(9)), decimal(rs.getBigDecimal(10)),
                        decimal(rs.getBigDecimal(11)), rs.getString(12), rs.getString(13)},
                params.toArray());
        Object[] totals = sumColumns(rows, 4, 5, 6, 7, 8, 9, 10);
        return result(ReportDefinition.PAYROLL_REPORT, filter, rows, totals, generatedBy);
    }

    // ------------------------------------------------------------------
    // Salary
    // ------------------------------------------------------------------

    private ReportResult salaryReport(ReportFilter filter, String generatedBy) {
        StringBuilder where = new StringBuilder(
                " WHERE e.status IN ('ACTIVE', 'INACTIVE', 'RESIGNED', 'TERMINATED', 'RETIRED')");
        List<Object> params = new ArrayList<>();
        appendDepartment(where, params, filter.departmentId(), "e.department_id");
        if (filter.keywordLike() != null) {
            where.append(" AND (e.full_name LIKE ? OR e.employee_code LIKE ?)");
            params.add(filter.keywordLike());
            params.add(filter.keywordLike());
        }
        List<Object[]> rows = Sql.query("""
                SELECT e.employee_code, e.full_name, COALESCE(d.dept_name, '-'),
                       COALESCE(p.position_name, '-'), e.employment_type,
                       e.join_date, e.basic_salary
                FROM employees e
                LEFT JOIN departments d ON d.id = e.department_id
                LEFT JOIN positions p ON p.id = e.position_id
                """ + where + " ORDER BY e.basic_salary DESC, e.full_name",
                rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), localDate(rs.getDate(6)), decimal(rs.getBigDecimal(7))},
                params.toArray());
        Object[] totals = new Object[rows.isEmpty() ? 0 : rows.get(0).length];
        if (totals.length > 0) {
            totals[0] = "TOTAL";
            totals[totals.length - 1] = sumOf(rows, 6);
        }
        return result(ReportDefinition.SALARY_REPORT, filter, rows, totals, generatedBy);
    }

    // ------------------------------------------------------------------
    // Performance
    // ------------------------------------------------------------------

    private ReportResult performanceReport(ReportFilter filter, String generatedBy) {
        StringBuilder where = new StringBuilder(
                " WHERE pr.period_start <= ? AND pr.period_end >= ?");
        List<Object> params = new ArrayList<>(List.of(filter.dateTo(), filter.dateFrom()));
        appendDepartment(where, params, filter.departmentId(), "e.department_id");
        List<Object[]> rows = Sql.query("""
                SELECT pr.review_code, e.full_name, COALESCE(d.dept_name, '-'),
                       pr.period_start, pr.period_end, pr.overall_score,
                       pr.stage, pr.status
                FROM performance_reviews pr
                JOIN employees e ON e.id = pr.employee_id
                LEFT JOIN departments d ON d.id = e.department_id
                """ + where + " ORDER BY pr.period_start DESC, e.full_name",
                rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3),
                        localDate(rs.getDate(4)), localDate(rs.getDate(5)),
                        rs.getBigDecimal(6) == null ? "-" : decimal(rs.getBigDecimal(6)),
                        rs.getString(7).replace('_', ' '), rs.getString(8)},
                params.toArray());
        return result(ReportDefinition.PERFORMANCE_REPORT, filter, rows, null, generatedBy);
    }

    // ------------------------------------------------------------------
    // Training
    // ------------------------------------------------------------------

    private ReportResult trainingReport(ReportFilter filter, String generatedBy) {
        List<Object[]> rows = Sql.query("""
                SELECT tp.program_name, COALESCE(tp.trainer_name, '-'),
                       MIN(ts.start_datetime), MAX(ts.end_datetime),
                       tp.cost,
                       COUNT(DISTINCT et.employee_id),
                       COALESCE(SUM(et.result IN ('COMPLETED', 'PASSED')), 0)
                FROM training_programs tp
                JOIN training_sessions ts ON ts.training_program_id = tp.id
                     AND ts.start_datetime BETWEEN ? AND ?
                LEFT JOIN employee_trainings et ON et.training_program_id = tp.id
                GROUP BY tp.id, tp.program_name, tp.trainer_name, tp.cost
                ORDER BY MIN(ts.start_datetime)
                """,
                rs -> new Object[]{
                        rs.getString(1), rs.getString(2),
                        toLocalDateTime(rs.getTimestamp(3)),
                        toLocalDateTime(rs.getTimestamp(4)),
                        rs.getBigDecimal(5) == null ? BigDecimal.ZERO : decimal(rs.getBigDecimal(5)),
                        decimal(rs.getBigDecimal(6)), decimal(rs.getBigDecimal(7))},
                filter.dateFrom().atStartOfDay(), filter.dateTo().atTime(23, 59, 59));
        Object[] totals = sumColumns(rows, 5, 6);
        return result(ReportDefinition.TRAINING_REPORT, filter, rows, totals, generatedBy);
    }

    // ------------------------------------------------------------------
    // Assets
    // ------------------------------------------------------------------

    private ReportResult assetReport(ReportFilter filter, String generatedBy) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendStatus(where, params, filter.status(), "a.status");
        if (filter.keywordLike() != null) {
            where.append(" AND (a.asset_name LIKE ? OR a.asset_code LIKE ? OR a.serial_number LIKE ?)");
            params.add(filter.keywordLike());
            params.add(filter.keywordLike());
            params.add(filter.keywordLike());
        }
        List<Object[]> rows = Sql.query("""
                SELECT a.asset_code, a.asset_name, a.category,
                       COALESCE(a.serial_number, '-'), a.purchase_date,
                       a.purchase_cost, a.status,
                       COALESCE(e.full_name, '-'), aa.assigned_date
                FROM assets a
                LEFT JOIN asset_assignments aa ON aa.asset_id = a.id AND aa.status = 'ASSIGNED'
                LEFT JOIN employees e ON e.id = aa.employee_id
                """ + where + " ORDER BY a.asset_code",
                rs -> new Object[]{
                        rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        localDate(rs.getDate(5)),
                        rs.getBigDecimal(6) == null ? BigDecimal.ZERO : decimal(rs.getBigDecimal(6)),
                        rs.getString(7), rs.getString(8), localDate(rs.getDate(9))},
                params.toArray());
        Object[] totals = sumColumns(rows, 5);
        return result(ReportDefinition.ASSET_REPORT, filter, rows, totals, generatedBy);
    }

    // ------------------------------------------------------------------
    // Turnover
    // ------------------------------------------------------------------

    private ReportResult turnoverReport(ReportFilter filter, String generatedBy) {
        YearMonth start = YearMonth.from(filter.dateFrom());
        YearMonth end = YearMonth.from(filter.dateTo());

        Map<YearMonth, Long> joined = monthCounts(
                "SELECT DATE_FORMAT(join_date, '%Y-%m'), COUNT(*) FROM employees "
                        + "WHERE join_date BETWEEN ? AND ? GROUP BY 1",
                filter.dateFrom(), filter.dateTo().plusDays(1));
        Map<YearMonth, Long> resigned = monthCounts(
                "SELECT DATE_FORMAT(last_working_date, '%Y-%m'), COUNT(*) FROM resignations "
                        + "WHERE status IN ('APPROVED', 'PROCESSED') "
                        + "AND last_working_date BETWEEN ? AND ? GROUP BY 1",
                filter.dateFrom(), filter.dateTo().plusDays(1));
        Map<YearMonth, Long> terminated = monthCounts(
                "SELECT DATE_FORMAT(termination_date, '%Y-%m'), COUNT(*) FROM terminations "
                        + "WHERE termination_date BETWEEN ? AND ? GROUP BY 1",
                filter.dateFrom(), filter.dateTo().plusDays(1));
        long headcountBeforeRange = Sql.count(
                "SELECT COUNT(*) FROM employees WHERE join_date < ? AND status = 'ACTIVE'",
                filter.dateFrom());

        List<Object[]> rows = new ArrayList<>();
        long headcount = headcountBeforeRange;
        for (YearMonth month = start; !month.isAfter(end); month = month.plusMonths(1)) {
            long hires = joined.getOrDefault(month, 0L);
            long exits = resigned.getOrDefault(month, 0L) + terminated.getOrDefault(month, 0L);
            headcount = headcount + hires - exits;
            rows.add(new Object[]{
                    month.toString(), decimal(hires), decimal(resigned.getOrDefault(month, 0L)),
                    decimal(terminated.getOrDefault(month, 0L)),
                    decimal(hires - exits), decimal(headcount)});
        }
        Object[] totals = sumColumns(rows, 1, 2, 3, 4);
        return result(ReportDefinition.TURNOVER_REPORT, filter, rows, totals, generatedBy);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Object[] mapStandardEmployeeRow(ResultSet rs) throws SQLException {
        return new Object[]{
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getString(5), localDate(rs.getDate(6)), decimal(rs.getBigDecimal(7)),
                rs.getString(8)};
    }

    private Object[] mapDayRow(ResultSet rs) throws SQLException {
        return new Object[]{
                localDate(rs.getDate(1)), rs.getString(2), rs.getString(3), rs.getString(4),
                rs.getTime(5) == null ? null : rs.getTime(5).toLocalTime(),
                decimal(rs.getBigDecimal(6))};
    }

    private Object[] mapLeaveRow(ResultSet rs) throws SQLException {
        return new Object[]{
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                localDate(rs.getDate(5)), localDate(rs.getDate(6)),
                decimal(rs.getBigDecimal(7)), rs.getString(8), localDate(rs.getDate(9))};
    }

    private Map<YearMonth, Long> monthCounts(String sql, LocalDate from, LocalDate to) {
        List<Object[]> raw = Sql.query(sql,
                rs -> new Object[]{rs.getString(1), rs.getLong(2)}, from, to);
        Map<YearMonth, Long> counts = new HashMap<>();
        for (Object[] row : raw) {
            counts.put(YearMonth.parse((String) row[0]), (Long) row[1]);
        }
        return counts;
    }

    /** Sums numeric columns across rows into a TOTAL row (labelled first column). */
    private Object[] sumColumns(List<Object[]> rows, int... columns) {
        if (rows.isEmpty()) {
            return null;
        }
        int width = rows.get(0).length;
        Object[] totals = new Object[width];
        totals[0] = "TOTAL";
        for (int index : columns) {
            totals[index] = sumOf(rows, index);
        }
        return totals;
    }

    private BigDecimal sumOf(List<Object[]> rows, int column) {
        BigDecimal sum = BigDecimal.ZERO;
        for (Object[] row : rows) {
            Object value = column < row.length ? row[column] : null;
            if (value instanceof BigDecimal decimal) {
                sum = sum.add(decimal);
            } else if (value instanceof Number number) {
                sum = sum.add(BigDecimal.valueOf(number.doubleValue()));
            }
        }
        return sum;
    }

    private void appendDepartment(StringBuilder where, List<Object> params,
                                  Long departmentId, String column) {
        if (departmentId != null) {
            where.append(" AND ").append(column).append(" = ?");
            params.add(departmentId);
        }
    }

    private void appendStatus(StringBuilder where, List<Object> params,
                              String status, String column) {
        if (status != null && !status.isBlank()) {
            where.append(" AND ").append(column).append(" = ?");
            params.add(status.trim());
        }
    }

    private ReportResult result(ReportDefinition definition, ReportFilter filter,
                                List<Object[]> rows, Object[] totals, String generatedBy) {
        return new ReportResult(definition.title(), filter.describe(),
                List.of(definition.columns()), rows, totals, generatedBy,
                LocalDateTime.now(), companyName());
    }

    private static BigDecimal decimal(Number value) {
        return value == null ? BigDecimal.ZERO : new BigDecimal(value.toString());
    }

    private static LocalDate localDate(java.sql.Date date) {
        return date == null ? null : date.toLocalDate();
    }

    private static LocalDateTime toLocalDateTime(java.sql.Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
