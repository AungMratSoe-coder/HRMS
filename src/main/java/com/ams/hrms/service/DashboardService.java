package com.ams.hrms.service;

import java.math.BigDecimal;
import java.util.List;

import com.ams.hrms.dto.CategoryCount;
import com.ams.hrms.dto.DashboardData;
import com.ams.hrms.dto.DashboardStats;
import com.ams.hrms.dto.PayrollTrendPoint;
import com.ams.hrms.dto.TrendDay;
import com.ams.hrms.dto.TypeUsage;
import com.ams.hrms.repository.DashboardRepository;

/**
 * Aggregates dashboard data in one pass (spec section 9). Pure read
 * composition - no business rules, safe to call from background threads.
 */
public class DashboardService {

    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    public DashboardData load() {
        DashboardStats stats = loadStats();
        List<CategoryCount> byDepartment = repository.employeesByDepartment();
        List<CategoryCount> byStatus = repository.employeesByStatus();
        List<TypeUsage> leaveUsage = repository.leaveUsageByType();
        List<TrendDay> attendanceTrend = repository.attendanceTrendLast14Days();
        List<PayrollTrendPoint> payrollTrend = repository.payrollCostTrend();

        String lastPeriod = null;
        BigDecimal lastGross = null;
        BigDecimal lastNet = null;
        var latest = repository.latestPayroll();
        if (latest.isPresent()) {
            PayrollTrendPoint point = latest.get();
            lastPeriod = point.periodLabel();
            lastGross = point.gross();
            lastNet = repository.latestPayrollNet(point.periodLabel());
        }
        String currency = repository.payrollCurrency();

        return new DashboardData(stats, byDepartment, byStatus, leaveUsage,
                attendanceTrend, payrollTrend, lastPeriod, lastGross, lastNet, currency);
    }

    private DashboardStats loadStats() {
        var workforce = repository.workforceTotals();
        var today = repository.todayAttendance();
        return new DashboardStats(
                workforce.total(),
                workforce.active(),
                workforce.newThisMonth(),
                repository.onLeaveToday(),
                today.present(),
                today.late(),
                today.absent(),
                repository.pendingLeaveRequests());
    }
}
