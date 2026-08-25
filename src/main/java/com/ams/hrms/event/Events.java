package com.ams.hrms.event;

/**
 * Application event types carried by {@link EventBus}.
 */
public final class Events {

    /** Fired after the main content area switched to a module. */
    public record NavigationChanged(String moduleId) {
    }

    /**
     * Fired when a panel asks the shell to open another module (e.g. the
     * dashboard "Process Now" shortcut opening the Payroll module).
     */
    public record NavigateRequest(String moduleId) {
    }

    /**
     * Fired when a module's data changed so interested panels can reload
     * (e.g. "employees", "leave", "notifications").
     */
    public record DataChanged(String scope) {
    }

    /**
     * Fired when an employee submitted a leave request; the notification
     * module fans this out to LEAVE_APPROVE holders (spec section 41).
     */
    public record LeaveRequested(long requestId, String leaveCode,
                                 long employeeId, String requesterName) {
    }

    /**
     * Fired when a leave request reaches its final decision (approved or
     * rejected); the notification module informs the affected employee.
     */
    public record LeaveDecided(long requestId, String leaveCode, long employeeId,
                               boolean approved, String decidedByName) {
    }

    /**
     * Fired when payroll for a period was calculated ("CALCULATED") or fully
     * paid ("PAID"); the notification module informs PAYROLL_VIEW holders.
     */
    public record PayrollProcessed(String stage, String periodLabel, int recordCount) {
    }

    private Events() {
    }
}
