package com.ams.hrms.repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.ams.hrms.model.PerformanceCriterion;
import com.ams.hrms.model.PerformanceReview;
import com.ams.hrms.model.PerformanceReviewItem;

/** Performance criteria, reviews and review items persistence (spec section 22). */
public class PerformanceRepository {

    // ------------------------------------------------------------------
    // Criteria
    // ------------------------------------------------------------------

    private static final String SELECT_CRITERIA =
            "SELECT id, criteria_code, criteria_name, weight, description, is_active "
                    + "FROM performance_criteria";

    public List<PerformanceCriterion> findAllCriteria() {
        return new Sql().list(SELECT_CRITERIA + " ORDER BY id", this::mapCriterion);
    }

    public List<PerformanceCriterion> findActiveCriteria() {
        return new Sql().list(SELECT_CRITERIA + " WHERE is_active = 1 ORDER BY id",
                this::mapCriterion);
    }

    public Optional<PerformanceCriterion> findCriterionById(long id) {
        return new Sql().first(SELECT_CRITERIA + " WHERE id = ?", this::mapCriterion, id);
    }

    public boolean criterionCodeExists(String code, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM performance_criteria WHERE criteria_code = ? "
                        + "AND (? IS NULL OR id <> ?)",
                code, excludeId, excludeId) > 0;
    }

    public long insertCriterion(PerformanceCriterion criterion) {
        return new Sql().executeInsert(
                "INSERT INTO performance_criteria (criteria_code, criteria_name, weight, "
                        + "description, is_active) VALUES (?, ?, ?, ?, ?)",
                criterion.getCode(), criterion.getName(), criterion.getWeight(),
                criterion.getDescription(), criterion.isActive());
    }

    public void updateCriterion(PerformanceCriterion criterion) {
        new Sql().executeUpdate(
                "UPDATE performance_criteria SET criteria_name = ?, weight = ?, "
                        + "description = ?, is_active = ? WHERE id = ?",
                criterion.getName(), criterion.getWeight(), criterion.getDescription(),
                criterion.isActive(), criterion.getId());
    }

    // ------------------------------------------------------------------
    // Reviews
    // ------------------------------------------------------------------

    private static final String SELECT_REVIEW =
            "SELECT r.id, r.review_code, r.employee_id, r.reviewer_id, r.period_start, "
                    + "r.period_end, r.overall_score, r.manager_comments, r.employee_comments, "
                    + "r.stage, r.status, r.finalized_at, r.finalized_by, "
                    + "e.employee_code, e.full_name AS employee_name, "
                    + "rev.full_name AS reviewer_name "
                    + "FROM performance_reviews r "
                    + "JOIN employees e ON e.id = r.employee_id "
                    + "LEFT JOIN employees rev ON rev.id = r.reviewer_id";

    public List<PerformanceReview> findReviews(String keyword, String status) {
        return findReviews(keyword, status, null);
    }

    /**
     * Listing with filters; {@code restrictToEmployeeId} (self-service scope)
     * limits the result to that employee's reviews.
     */
    public List<PerformanceReview> findReviews(String keyword, String status,
                                               Long restrictToEmployeeId) {
        StringBuilder sql = new StringBuilder(SELECT_REVIEW).append(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (restrictToEmployeeId != null) {
            sql.append(" AND r.employee_id = ?");
            params.add(restrictToEmployeeId);
        }
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (e.full_name LIKE CONCAT('%', ?, '%') "
                    + "OR e.employee_code LIKE CONCAT('%', ?, '%') "
                    + "OR r.review_code LIKE CONCAT('%', ?, '%'))");
            String filter = keyword.trim();
            params.add(filter);
            params.add(filter);
            params.add(filter);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND r.status = ?");
            params.add(status);
        }
        sql.append(" ORDER BY r.id DESC");
        return new Sql().list(sql.toString(), this::mapReview, params.toArray());
    }

    /** All reviews of one employee, newest first (profile view). */
    public List<PerformanceReview> findReviewsForEmployee(long employeeId) {
        return new Sql().list(SELECT_REVIEW + " WHERE r.employee_id = ? ORDER BY r.id DESC",
                this::mapReview, employeeId);
    }

    public Optional<PerformanceReview> findReviewById(long id) {
        return new Sql().first(SELECT_REVIEW + " WHERE r.id = ?", this::mapReview, id);
    }

    public boolean periodExists(long employeeId, LocalDate start, LocalDate end, Long excludeId) {
        return new Sql().scalarLong(
                "SELECT COUNT(*) FROM performance_reviews WHERE employee_id = ? "
                        + "AND period_start = ? AND period_end = ? AND (? IS NULL OR id <> ?)",
                employeeId, start, end, excludeId, excludeId) > 0;
    }

    public long insertReview(PerformanceReview review) {
        return new Sql().executeInsert(
                "INSERT INTO performance_reviews (review_code, employee_id, reviewer_id, "
                        + "period_start, period_end, manager_comments, stage, status) "
                        + "VALUES ('TMP', ?, ?, ?, ?, ?, 'MANAGER_REVIEW', 'DRAFT')",
                review.getEmployeeId(), review.getReviewerId(), review.getPeriodStart(),
                review.getPeriodEnd(), review.getManagerComments());
    }

    public void updateReviewCode(long id, String code) {
        new Sql().executeUpdate(
                "UPDATE performance_reviews SET review_code = ? WHERE id = ?", code, id);
    }

    /** Edits the draft header (reviewer/period/manager comments). */
    public void updateDraft(long id, Long reviewerId, LocalDate periodStart,
                            LocalDate periodEnd, String managerComments) {
        new Sql().executeUpdate(
                "UPDATE performance_reviews SET reviewer_id = ?, period_start = ?, "
                        + "period_end = ?, manager_comments = ? WHERE id = ?",
                reviewerId, periodStart, periodEnd, managerComments, id);
    }

    /** Replaces the scored items for a review (delete + insert in caller's tx). */
    public void replaceItems(Sql sql, long reviewId,
                             List<PerformanceReviewItem> items) {
        sql.executeUpdate(
                "DELETE FROM performance_review_items WHERE performance_review_id = ?",
                reviewId);
        for (var item : items) {
            sql.executeUpdate(
                    "INSERT INTO performance_review_items (performance_review_id, "
                            + "criteria_id, score, comments) VALUES (?, ?, ?, ?)",
                    reviewId, item.getCriteriaId(), item.getScore(), item.getComments());
        }
    }

    public void updateStageAndStatus(long id, String stage, String status) {
        new Sql().executeUpdate(
                "UPDATE performance_reviews SET stage = ?, status = ? WHERE id = ?",
                stage, status, id);
    }

    public void updateEmployeeComments(long id, String comments) {
        new Sql().executeUpdate(
                "UPDATE performance_reviews SET employee_comments = ? WHERE id = ?",
                comments, id);
    }

    public void finalizeReview(long id, BigDecimal overallScore, long finalizedBy) {
        new Sql().executeUpdate(
                "UPDATE performance_reviews SET overall_score = ?, stage = 'FINALIZED', "
                        + "status = 'COMPLETED', finalized_at = NOW(), finalized_by = ? "
                        + "WHERE id = ?",
                overallScore, finalizedBy, id);
    }

    public void cancelReview(long id) {
        new Sql().executeUpdate(
                "UPDATE performance_reviews SET status = 'CANCELLED' WHERE id = ?", id);
    }

    // ------------------------------------------------------------------
    // Review items
    // ------------------------------------------------------------------

    private static final String SELECT_ITEM =
            "SELECT i.id, i.performance_review_id, i.criteria_id, i.score, i.comments, "
                    + "c.criteria_name, c.weight AS criteria_weight "
                    + "FROM performance_review_items i "
                    + "JOIN performance_criteria c ON c.id = i.criteria_id";

    public List<PerformanceReviewItem> findItems(long reviewId) {
        return new Sql().list(SELECT_ITEM + " WHERE i.performance_review_id = ? "
                        + "ORDER BY c.id",
                this::mapItem, reviewId);
    }

    /** Criteria scored on a review (for the completeness check). */
    public List<Long> findScoredCriteriaIds(long reviewId) {
        return new Sql().list(
                "SELECT criteria_id FROM performance_review_items "
                        + "WHERE performance_review_id = ?",
                rs -> rs.getLong(1), reviewId);
    }

    // ------------------------------------------------------------------
    // Mapping
    // ------------------------------------------------------------------

    private PerformanceCriterion mapCriterion(ResultSet rs) throws SQLException {
        PerformanceCriterion criterion = new PerformanceCriterion();
        criterion.setId(rs.getLong("id"));
        criterion.setCode(rs.getString("criteria_code"));
        criterion.setName(rs.getString("criteria_name"));
        criterion.setWeight(rs.getBigDecimal("weight"));
        criterion.setDescription(rs.getString("description"));
        criterion.setActive(rs.getBoolean("is_active"));
        return criterion;
    }

    private PerformanceReview mapReview(ResultSet rs) throws SQLException {
        PerformanceReview review = new PerformanceReview();
        review.setId(rs.getLong("id"));
        review.setReviewCode(rs.getString("review_code"));
        review.setEmployeeId(rs.getLong("employee_id"));
        long reviewerId = rs.getLong("reviewer_id");
        review.setReviewerId(rs.wasNull() ? null : reviewerId);
        review.setPeriodStart(rs.getObject("period_start", LocalDate.class));
        review.setPeriodEnd(rs.getObject("period_end", LocalDate.class));
        review.setOverallScore(rs.getBigDecimal("overall_score"));
        review.setManagerComments(rs.getString("manager_comments"));
        review.setEmployeeComments(rs.getString("employee_comments"));
        review.setStage(rs.getString("stage"));
        review.setStatus(rs.getString("status"));
        review.setFinalizedAt(rs.getObject("finalized_at", LocalDateTime.class));
        long finalizedBy = rs.getLong("finalized_by");
        review.setFinalizedBy(rs.wasNull() ? null : finalizedBy);
        review.setEmployeeCode(rs.getString("employee_code"));
        review.setEmployeeName(rs.getString("employee_name"));
        review.setReviewerName(rs.getString("reviewer_name"));
        return review;
    }

    private PerformanceReviewItem mapItem(ResultSet rs) throws SQLException {
        PerformanceReviewItem item = new PerformanceReviewItem();
        item.setId(rs.getLong("id"));
        item.setPerformanceReviewId(rs.getLong("performance_review_id"));
        item.setCriteriaId(rs.getLong("criteria_id"));
        item.setScore(rs.getBigDecimal("score"));
        item.setComments(rs.getString("comments"));
        item.setCriteriaName(rs.getString("criteria_name"));
        item.setCriteriaWeight(rs.getBigDecimal("criteria_weight"));
        return item;
    }
}
