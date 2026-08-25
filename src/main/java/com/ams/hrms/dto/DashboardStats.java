package com.ams.hrms.dto;

/**
 * Headline workforce metrics for the dashboard stat cards (spec section 9).
 */
public record DashboardStats(
        long totalEmployees,
        long activeEmployees,
        long newEmployeesThisMonth,
        long onLeaveToday,
        long presentToday,
        long lateToday,
        long absentToday,
        long pendingLeaveRequests) {
}
