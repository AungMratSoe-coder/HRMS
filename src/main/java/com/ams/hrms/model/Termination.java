package com.ams.hrms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Employee termination record (spec section 26); effective immediately. */
public class Termination {

    private Long id;
    private String terminationCode;
    private long employeeId;
    private LocalDate terminationDate;
    private String reasonCategory;
    private String reason;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private boolean eligibleRehire;
    private String notes;

    private String employeeCode;
    private String employeeName;
    private String approvedByName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTerminationCode() {
        return terminationCode;
    }

    public void setTerminationCode(String terminationCode) {
        this.terminationCode = terminationCode;
    }

    public long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(long employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDate getTerminationDate() {
        return terminationDate;
    }

    public void setTerminationDate(LocalDate terminationDate) {
        this.terminationDate = terminationDate;
    }

    public String getReasonCategory() {
        return reasonCategory;
    }

    public void setReasonCategory(String reasonCategory) {
        this.reasonCategory = reasonCategory;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(Long approvedBy) {
        this.approvedBy = approvedBy;
    }

    public LocalDateTime getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(LocalDateTime approvedAt) {
        this.approvedAt = approvedAt;
    }

    public boolean isEligibleRehire() {
        return eligibleRehire;
    }

    public void setEligibleRehire(boolean eligibleRehire) {
        this.eligibleRehire = eligibleRehire;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
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

    public String getApprovedByName() {
        return approvedByName;
    }

    public void setApprovedByName(String approvedByName) {
        this.approvedByName = approvedByName;
    }
}
