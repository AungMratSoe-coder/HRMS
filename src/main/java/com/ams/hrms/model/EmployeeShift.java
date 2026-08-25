package com.ams.hrms.model;

import java.time.LocalDate;

/** One employee-shift assignment with effective dating. */
public class EmployeeShift {

    private Long id;
    private long employeeId;
    private String employeeDisplay;
    private long shiftId;
    private String shiftName;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo; // null = current
    private String assignedByName;

    public boolean isCurrent() {
        return effectiveTo == null;
    }

    public boolean activeOn(LocalDate date) {
        return !date.isBefore(effectiveFrom)
                && (effectiveTo == null || !date.isAfter(effectiveTo));
    }

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

    public String getEmployeeDisplay() {
        return employeeDisplay;
    }

    public void setEmployeeDisplay(String employeeDisplay) {
        this.employeeDisplay = employeeDisplay;
    }

    public long getShiftId() {
        return shiftId;
    }

    public void setShiftId(long shiftId) {
        this.shiftId = shiftId;
    }

    public String getShiftName() {
        return shiftName;
    }

    public void setShiftName(String shiftName) {
        this.shiftName = shiftName;
    }

    public LocalDate getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveFrom(LocalDate effectiveFrom) {
        this.effectiveFrom = effectiveFrom;
    }

    public LocalDate getEffectiveTo() {
        return effectiveTo;
    }

    public void setEffectiveTo(LocalDate effectiveTo) {
        this.effectiveTo = effectiveTo;
    }

    public String getAssignedByName() {
        return assignedByName;
    }

    public void setAssignedByName(String assignedByName) {
        this.assignedByName = assignedByName;
    }
}
