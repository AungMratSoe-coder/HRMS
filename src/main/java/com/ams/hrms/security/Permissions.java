package com.ams.hrms.security;

import java.util.Arrays;
import java.util.Optional;

/**
 * Application permission codes. Names match the {@code permissions} table
 * seed exactly; unknown database codes are ignored at login (logged) so new
 * permissions can be added via migration without breaking older builds.
 */
public enum Permissions {

    // Employees & documents
    EMPLOYEE_VIEW, EMPLOYEE_CREATE, EMPLOYEE_UPDATE, EMPLOYEE_DELETE,
    EMPLOYEE_PHOTO_UPLOAD, DOCUMENT_MANAGE,

    // Organization
    DEPARTMENT_VIEW, DEPARTMENT_CREATE, DEPARTMENT_UPDATE,
    POSITION_VIEW, POSITION_CREATE, POSITION_UPDATE,

    // Shifts
    SHIFT_VIEW, SHIFT_MANAGE, SHIFT_ASSIGN,

    // Attendance
    ATTENDANCE_VIEW, ATTENDANCE_CREATE, ATTENDANCE_UPDATE, ATTENDANCE_CORRECTION_APPROVE,

    // Leave
    LEAVE_VIEW, LEAVE_REQUEST, LEAVE_APPROVE, LEAVE_CANCEL,

    // Overtime
    OVERTIME_VIEW, OVERTIME_REQUEST, OVERTIME_APPROVE,

    // Payroll
    PAYROLL_VIEW, PAYROLL_CALCULATE, PAYROLL_REVIEW, PAYROLL_APPROVE, PAYROLL_MARK_PAID,
    PAYSLIP_VIEW, PAYSLIP_GENERATE,

    // Recruitment
    RECRUITMENT_VIEW, RECRUITMENT_MANAGE, INTERVIEW_MANAGE, OFFER_MANAGE,

    // Onboarding
    ONBOARDING_MANAGE,

    // Performance
    PERFORMANCE_VIEW, PERFORMANCE_MANAGE,

    // Training
    TRAINING_VIEW, TRAINING_MANAGE,

    // Assets
    ASSET_VIEW, ASSET_ASSIGN, ASSET_MANAGE,

    // Reports
    REPORT_VIEW, REPORT_EXPORT,

    // Separation
    SEPARATION_MANAGE,

    // System
    USER_MANAGE, SETTINGS_MANAGE, AUDIT_LOG_VIEW;

    /**
     * Maps a database permission code to the enum; empty when the code is
     * unknown to this build.
     */
    public static Optional<Permissions> fromCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(permission -> permission.name().equals(code.trim()))
                .findFirst();
    }
}
