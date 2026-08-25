package com.ams.hrms.model;

import java.math.BigDecimal;

/** One scored criterion inside a performance review (spec section 22). */
public class PerformanceReviewItem {

    private Long id;
    private long performanceReviewId;
    private long criteriaId;
    private BigDecimal score;
    private String comments;

    private String criteriaName;
    private BigDecimal criteriaWeight;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getPerformanceReviewId() {
        return performanceReviewId;
    }

    public void setPerformanceReviewId(long performanceReviewId) {
        this.performanceReviewId = performanceReviewId;
    }

    public long getCriteriaId() {
        return criteriaId;
    }

    public void setCriteriaId(long criteriaId) {
        this.criteriaId = criteriaId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public String getCriteriaName() {
        return criteriaName;
    }

    public void setCriteriaName(String criteriaName) {
        this.criteriaName = criteriaName;
    }

    public BigDecimal getCriteriaWeight() {
        return criteriaWeight;
    }

    public void setCriteriaWeight(BigDecimal criteriaWeight) {
        this.criteriaWeight = criteriaWeight;
    }
}
