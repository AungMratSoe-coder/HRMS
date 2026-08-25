package com.ams.hrms.report;

/**
 * Catalog of the standard reports (spec section 27). Each entry declares its
 * columns, the filters it understands (date range, department, keyword
 * search, status) and the selectable status values. The repository switches
 * on these constants; writers and UI are fully metadata-driven.
 */
public enum ReportDefinition {

    EMPLOYEE_LIST("Employee List",
            "All employees with department, position, join date and salary.",
            false, true, true,
            new String[]{"ACTIVE", "INACTIVE", "RESIGNED", "TERMINATED", "RETIRED"},
            ReportColumn.text("Code"),
            ReportColumn.text("Full Name"),
            ReportColumn.text("Department"),
            ReportColumn.text("Position"),
            ReportColumn.text("Type"),
            ReportColumn.date("Join Date"),
            ReportColumn.money("Basic Salary"),
            ReportColumn.text("Status")),

    DEPARTMENT_REPORT("Department Report",
            "Headcount, active staff and basic-salary cost per department.",
            false, false, false, null,
            ReportColumn.text("Code"),
            ReportColumn.text("Department"),
            ReportColumn.text("Manager"),
            ReportColumn.number("Positions"),
            ReportColumn.number("Employees"),
            ReportColumn.number("Active"),
            ReportColumn.money("Total Basic Salary")),

    ATTENDANCE_SUMMARY("Attendance Report",
            "Per-employee attendance totals over a date range.",
            true, true, false, null,
            ReportColumn.text("Code"),
            ReportColumn.text("Employee"),
            ReportColumn.text("Department"),
            ReportColumn.number("Present"),
            ReportColumn.number("Late"),
            ReportColumn.number("Absent"),
            ReportColumn.number("On Leave"),
            ReportColumn.number("Late Minutes"),
            ReportColumn.number("Overtime Hours")),

    LATE_REPORT("Late Report",
            "Individual late arrivals within a date range.",
            true, true, false, null,
            ReportColumn.date("Date"),
            ReportColumn.text("Code"),
            ReportColumn.text("Employee"),
            ReportColumn.text("Department"),
            ReportColumn.time("Check In"),
            ReportColumn.number("Late Minutes")),

    ABSENCE_REPORT("Absence Report",
            "Individual absences within a date range.",
            true, true, false, null,
            ReportColumn.date("Date"),
            ReportColumn.text("Code"),
            ReportColumn.text("Employee"),
            ReportColumn.text("Department"),
            ReportColumn.text("Status"),
            ReportColumn.text("Remarks")),

    LEAVE_REPORT("Leave Report",
            "Leave requests overlapping the selected range.",
            true, true, false,
            new String[]{"PENDING", "APPROVED", "REJECTED", "CANCELLED"},
            ReportColumn.text("Code"),
            ReportColumn.text("Employee"),
            ReportColumn.text("Department"),
            ReportColumn.text("Type"),
            ReportColumn.date("From"),
            ReportColumn.date("To"),
            ReportColumn.number("Days"),
            ReportColumn.text("Status"),
            ReportColumn.date("Decided On")),

    LEAVE_BALANCE("Leave Balance",
            "Entitlement, usage and remaining balance for the year of the start date.",
            false, true, false, null,
            ReportColumn.text("Code"),
            ReportColumn.text("Employee"),
            ReportColumn.text("Department"),
            ReportColumn.text("Leave Type"),
            ReportColumn.number("Entitled"),
            ReportColumn.number("Carried"),
            ReportColumn.number("Used"),
            ReportColumn.number("Pending"),
            ReportColumn.number("Available")),

    OVERTIME_REPORT("Overtime Report",
            "Overtime requests with rates and amounts.",
            true, true, false,
            new String[]{"PENDING", "APPROVED", "REJECTED", "PAID", "CANCELLED"},
            ReportColumn.text("Code"),
            ReportColumn.text("Employee"),
            ReportColumn.text("Department"),
            ReportColumn.date("Date"),
            ReportColumn.number("Hours"),
            ReportColumn.money("Rate / Hour"),
            ReportColumn.money("Amount"),
            ReportColumn.text("Status")),

    PAYROLL_REPORT("Payroll Report",
            "Processed payroll runs whose period starts in the selected range.",
            true, true, false,
            new String[]{"DRAFT", "CALCULATED", "REVIEWED", "APPROVED", "PAID", "CANCELLED"},
            ReportColumn.text("Period"),
            ReportColumn.text("Code"),
            ReportColumn.text("Employee"),
            ReportColumn.text("Department"),
            ReportColumn.money("Basic"),
            ReportColumn.money("Allowances"),
            ReportColumn.money("Bonus"),
            ReportColumn.money("Overtime"),
            ReportColumn.money("Gross"),
            ReportColumn.money("Deductions"),
            ReportColumn.money("Net Pay"),
            ReportColumn.text("Currency"),
            ReportColumn.text("Status")),

    SALARY_REPORT("Salary Report",
            "Current basic salaries across the workforce with totals.",
            false, true, true, null,
            ReportColumn.text("Code"),
            ReportColumn.text("Employee"),
            ReportColumn.text("Department"),
            ReportColumn.text("Position"),
            ReportColumn.text("Type"),
            ReportColumn.date("Join Date"),
            ReportColumn.money("Basic Salary")),

    PERFORMANCE_REPORT("Performance Report",
            "Performance reviews overlapping the selected range.",
            true, true, false, null,
            ReportColumn.text("Code"),
            ReportColumn.text("Employee"),
            ReportColumn.text("Department"),
            ReportColumn.date("Period Start"),
            ReportColumn.date("Period End"),
            ReportColumn.number("Score"),
            ReportColumn.text("Stage"),
            ReportColumn.text("Status")),

    TRAINING_REPORT("Training Report",
            "Training programs with sessions in the selected range.",
            true, false, false, null,
            ReportColumn.text("Program"),
            ReportColumn.text("Trainer"),
            ReportColumn.date("First Session"),
            ReportColumn.date("Last Session"),
            ReportColumn.money("Cost"),
            ReportColumn.number("Enrolled"),
            ReportColumn.number("Completed")),

    ASSET_REPORT("Asset Report",
            "Company assets and their current assignment state.",
            false, false, true,
            new String[]{"AVAILABLE", "ASSIGNED", "UNDER_REPAIR", "RETIRED", "LOST"},
            ReportColumn.text("Code"),
            ReportColumn.text("Name"),
            ReportColumn.text("Category"),
            ReportColumn.text("Serial Number"),
            ReportColumn.date("Purchase Date"),
            ReportColumn.money("Cost"),
            ReportColumn.text("Status"),
            ReportColumn.text("Assigned To"),
            ReportColumn.date("Assigned Date")),

    TURNOVER_REPORT("Employee Turnover Report",
            "Monthly hires, separations and running headcount.",
            true, false, false, null,
            ReportColumn.text("Month"),
            ReportColumn.number("Joined"),
            ReportColumn.number("Resigned"),
            ReportColumn.number("Terminated"),
            ReportColumn.number("Net Change"),
            ReportColumn.number("Active Headcount"));

    private final String title;
    private final String description;
    private final boolean needsDateRange;
    private final boolean supportsDepartment;
    private final boolean supportsKeyword;
    private final String[] statusOptions;
    private final ReportColumn[] columns;

    ReportDefinition(String title, String description, boolean needsDateRange,
                     boolean supportsDepartment, boolean supportsKeyword,
                     String[] statusOptions, ReportColumn... columns) {
        this.title = title;
        this.description = description;
        this.needsDateRange = needsDateRange;
        this.supportsDepartment = supportsDepartment;
        this.supportsKeyword = supportsKeyword;
        this.statusOptions = statusOptions == null ? new String[0] : statusOptions;
        this.columns = columns;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    /** Lowercase identifier used for default file names. */
    public String fileId() {
        return name().toLowerCase().replace('_', '-');
    }

    public boolean needsDateRange() {
        return needsDateRange;
    }

    public boolean supportsDepartment() {
        return supportsDepartment;
    }

    public boolean supportsKeyword() {
        return supportsKeyword;
    }

    /** Selectable status filter values; empty when the report has none. */
    public String[] statusOptions() {
        return statusOptions;
    }

    public ReportColumn[] columns() {
        return columns;
    }
}
