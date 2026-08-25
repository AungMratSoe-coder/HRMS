package com.ams.hrms.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** An employee's enrollment and result for a training program (spec section 23). */
public class EmployeeTraining {

    public static final String RESULT_ENROLLED = "ENROLLED";
    public static final String RESULT_ATTENDED = "ATTENDED";
    public static final String RESULT_COMPLETED = "COMPLETED";
    public static final String RESULT_PASSED = "PASSED";
    public static final String RESULT_FAILED = "FAILED";
    public static final String RESULT_NO_SHOW = "NO_SHOW";

    private Long id;
    private long programId;
    private Long sessionId;
    private long employeeId;
    private String result;
    private BigDecimal score;
    private Long certificateDocumentId;
    private LocalDate completionDate;
    private String notes;

    private String employeeCode;
    private String employeeName;
    private String programName;
    private String sessionSummary;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getProgramId() {
        return programId;
    }

    public void setProgramId(long programId) {
        this.programId = programId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(long employeeId) {
        this.employeeId = employeeId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public Long getCertificateDocumentId() {
        return certificateDocumentId;
    }

    public void setCertificateDocumentId(Long certificateDocumentId) {
        this.certificateDocumentId = certificateDocumentId;
    }

    public LocalDate getCompletionDate() {
        return completionDate;
    }

    public void setCompletionDate(LocalDate completionDate) {
        this.completionDate = completionDate;
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

    public String getProgramName() {
        return programName;
    }

    public void setProgramName(String programName) {
        this.programName = programName;
    }

    public String getSessionSummary() {
        return sessionSummary;
    }

    public void setSessionSummary(String sessionSummary) {
        this.sessionSummary = sessionSummary;
    }

    /** Terminal results freeze the record. */
    public boolean isDecided() {
        return RESULT_PASSED.equals(result) || RESULT_FAILED.equals(result)
                || RESULT_NO_SHOW.equals(result);
    }
}
