package com.ams.hrms.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Performance review header (spec section 22). Lifecycle:
 * DRAFT/MANAGER_REVIEW &rarr; IN_PROGRESS/EMPLOYEE_FEEDBACK &rarr;
 * COMPLETED/FINALIZED (terminal), with CANCELLED as an early exit.
 */
public class PerformanceReview {

    public static final String STAGE_MANAGER_REVIEW = "MANAGER_REVIEW";
    public static final String STAGE_EMPLOYEE_FEEDBACK = "EMPLOYEE_FEEDBACK";
    public static final String STAGE_FINALIZED = "FINALIZED";

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    private Long id;
    private String reviewCode;
    private long employeeId;
    private Long reviewerId;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private BigDecimal overallScore;
    private String managerComments;
    private String employeeComments;
    private String stage;
    private String status;
    private LocalDateTime finalizedAt;
    private Long finalizedBy;

    private String employeeCode;
    private String employeeName;
    private String reviewerName;

    private final List<PerformanceReviewItem> items = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getReviewCode() {
        return reviewCode;
    }

    public void setReviewCode(String reviewCode) {
        this.reviewCode = reviewCode;
    }

    public long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(long employeeId) {
        this.employeeId = employeeId;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public LocalDate getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(LocalDate periodStart) {
        this.periodStart = periodStart;
    }

    public LocalDate getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(LocalDate periodEnd) {
        this.periodEnd = periodEnd;
    }

    public BigDecimal getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(BigDecimal overallScore) {
        this.overallScore = overallScore;
    }

    public String getManagerComments() {
        return managerComments;
    }

    public void setManagerComments(String managerComments) {
        this.managerComments = managerComments;
    }

    public String getEmployeeComments() {
        return employeeComments;
    }

    public void setEmployeeComments(String employeeComments) {
        this.employeeComments = employeeComments;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getFinalizedAt() {
        return finalizedAt;
    }

    public void setFinalizedAt(LocalDateTime finalizedAt) {
        this.finalizedAt = finalizedAt;
    }

    public Long getFinalizedBy() {
        return finalizedBy;
    }

    public void setFinalizedBy(Long finalizedBy) {
        this.finalizedBy = finalizedBy;
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

    public String getReviewerName() {
        return reviewerName;
    }

    public void setReviewerName(String reviewerName) {
        this.reviewerName = reviewerName;
    }

    public List<PerformanceReviewItem> getItems() {
        return items;
    }

    /** True while the manager can still edit scores. */
    public boolean isEditableDraft() {
        return STAGE_MANAGER_REVIEW.equals(stage)
                && (STATUS_DRAFT.equals(status) || STATUS_IN_PROGRESS.equals(status));
    }
}
