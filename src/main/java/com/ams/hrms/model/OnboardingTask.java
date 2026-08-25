package com.ams.hrms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** One onboarding checklist task for an employee (spec section 15). */
public class OnboardingTask {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_SKIPPED = "SKIPPED";
    public static final String STATUS_WAIVED = "WAIVED";

    private Long id;
    private long employeeId;
    private Long templateTaskId;
    private String taskName;
    private int taskOrder;
    private LocalDate dueDate;
    private String status;
    private LocalDateTime completedAt;
    private Long completedBy;

    private String completedByName;
    private boolean mandatory;
    private String employeeCode;
    private String employeeName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(long employeeId) {
        this.employeeId = employeeId;
    }

    public Long getTemplateTaskId() {
        return templateTaskId;
    }

    public void setTemplateTaskId(Long templateTaskId) {
        this.templateTaskId = templateTaskId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public int getTaskOrder() {
        return taskOrder;
    }

    public void setTaskOrder(int taskOrder) {
        this.taskOrder = taskOrder;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Long getCompletedBy() {
        return completedBy;
    }

    public void setCompletedBy(Long completedBy) {
        this.completedBy = completedBy;
    }

    public String getCompletedByName() {
        return completedByName;
    }

    public void setCompletedByName(String completedByName) {
        this.completedByName = completedByName;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    public void setMandatory(boolean mandatory) {
        this.mandatory = mandatory;
    }

    public String getEmployeeCode() {
        return employeeCode;
    }

    public void setEmployeeCode(String employeeCode) {
        this.employeeCode = employeeCode;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    /** A decided task can no longer be marked complete. */
    public boolean isPending() {
        return STATUS_PENDING.equals(status);
    }
}
