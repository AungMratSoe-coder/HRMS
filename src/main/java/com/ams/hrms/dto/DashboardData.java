package com.ams.hrms.dto;

import java.math.BigDecimal;
import java.util.List;

/** Everything the dashboard renders, loaded in one pass. */
public record DashboardData(
        DashboardStats stats,
        List<CategoryCount> employeesByDepartment,
        List<CategoryCount> employeesByStatus,
        List<TypeUsage> leaveUsageByType,
        List<TrendDay> attendanceTrend,
        List<PayrollTrendPoint> payrollCostTrend,
        String lastPayrollPeriod,
        BigDecimal lastPayrollGross,
        BigDecimal lastPayrollNet,
        String currency) {

    public String formattedMoney(BigDecimal amount) {
        if (amount == null) {
            return "--";
        }
        return String.format("%,.2f %s", amount, currency == null || currency.isBlank() ? "" : currency);
    }
}
