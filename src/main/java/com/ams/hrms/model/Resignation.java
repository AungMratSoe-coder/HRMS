package com.ams.hrms.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Employee resignation record (spec section 26). */
public class Resignation {

    public static final String STATUS_SUBMITTED = "SUBMITTED";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_WITHDRAWN = "WITHDRAWN";
    public static final String STATUS_PROCESSED = "PROCESSED";

    private Long id;
    private String resignationCode;
    private long employeeId;
    private LocalDate resignationDate;
    private LocalDate lastWorkingDate;
    private int noticePeriodDays;
    private String reason;
    private String status;
    private Long approvedBy;
    private LocalDateTime approvedAt;
    private String exitInterviewNotes;

    private String employeeCode;
    private String employeeName;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getResignationCode() {
        return resignationCode;
    }

    public void setResignationCode(String resignationCode) {
        this.resignationCode = resignationCode;
    }

    public long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(long employeeId) {
        this.employeeId = employeeId;
    }

    public LocalDate getResignationDate() {
        return resignationDate;
    }

    public void setResignationDate(LocalDate resignationDate) {
        this.resignationDate = resignationDate;
    }

    public LocalDate getLastWorkingDate() {
        return lastWorkingDate;
    }

    public void setLastWorkingDate(LocalDate lastWorkingDate) {
        this.lastWorkingDate = lastWorkingDate;
    }

    public int getNoticePeriodDays() {
        return noticePeriodDays;
    }

    public void setNoticePeriodDays(int noticePeriodDays) {
        this.noticePeriodDays = noticePeriodDays;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public String getExitInterviewNotes() {
        return exitInterviewNotes;
    }

    public void setExitInterviewNotes(String exitInterviewNotes) {
        this.exitInterviewNotes = exitInterviewNotes;
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
}
