package com.ams.hrms.model;

import java.time.LocalDate;

/** Assignment of an asset to an employee (spec section 24). */
public class AssetAssignment {

    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_RETURNED = "RETURNED";
    public static final String STATUS_OVERDUE = "OVERDUE";
    public static final String STATUS_LOST = "LOST";

    private Long id;
    private long assetId;
    private long employeeId;
    private LocalDate assignedDate;
    private LocalDate dueReturnDate;
    private LocalDate returnedDate;
    private String conditionOnReturn;
    private String notes;
    private String status;
    private Long assignedBy;

    private String assetCode;
    private String assetName;
    private String employeeCode;
    private String employeeName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getAssetId() {
        return assetId;
    }

    public void setAssetId(long assetId) {
        this.assetId = assetId;
    }

    public long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(long employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDate getAssignedDate() {
        return assignedDate;
    }

    public void setAssignedDate(LocalDate assignedDate) {
        this.assignedDate = assignedDate;
    }

    public LocalDate getDueReturnDate() {
        return dueReturnDate;
    }

    public void setDueReturnDate(LocalDate dueReturnDate) {
        this.dueReturnDate = dueReturnDate;
    }

    public LocalDate getReturnedDate() {
        return returnedDate;
    }

    public void setReturnedDate(LocalDate returnedDate) {
        this.returnedDate = returnedDate;
    }

    public String getConditionOnReturn() {
        return conditionOnReturn;
    }

    public void setConditionOnReturn(String conditionOnReturn) {
        this.conditionOnReturn = conditionOnReturn;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(Long assignedBy) {
        this.assignedBy = assignedBy;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getAssetName() {
        return assetName;
    }

    public void setAssetName(String assetName) {
        this.assetName = assetName;
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

    /** True while the asset is out with the employee. */
    public boolean isOpen() {
        return STATUS_ASSIGNED.equals(status) || STATUS_OVERDUE.equals(status);
    }
}
