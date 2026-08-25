package com.ams.hrms.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.PerformanceCriterion;
import com.ams.hrms.model.PerformanceReview;
import com.ams.hrms.model.PerformanceReviewItem;
import com.ams.hrms.repository.PerformanceRepository;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.repository.TransactionManager;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.validator.Validators;

/**
 * Performance reviews (spec section 22): weighted criteria scored 1-5 by the
 * manager, employee feedback, then finalization that computes the weighted
 * overall score and freezes the record. Workflow:
 * DRAFT/MANAGER_REVIEW &rarr; IN_PROGRESS/EMPLOYEE_FEEDBACK &rarr;
 * COMPLETED/FINALIZED; CANCELLED exits early. Finalized reviews are
 * immutable history.
 */
public class PerformanceService {

    public static final String DATA_SCOPE = "performance";

    private static final Logger LOG = LoggerFactory.getLogger(PerformanceService.class);

    private static final BigDecimal MIN_SCORE = BigDecimal.ONE;
    private static final BigDecimal MAX_SCORE = BigDecimal.valueOf(5);

    private final PerformanceRepository repository;
    private final AuditService auditService;
    private final EmployeeService employeeService;

    public PerformanceService(PerformanceRepository repository, AuditService auditService,
                              EmployeeService employeeService) {
        this.repository = repository;
        this.auditService = auditService;
        this.employeeService = employeeService;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    /** Listing; plain EMPLOYEE accounts only ever see their own reviews. */
    public List<PerformanceReview> findAll(String keyword, String status) {
        SecurityService.require(Permissions.PERFORMANCE_VIEW);
        return repository.findReviews(keyword, status,
                employeeService.selfScopeEmployeeId());
    }

    /** All reviews of one employee, newest first (profile view). */
    public List<PerformanceReview> findForEmployee(long employeeId) {
        if (!employeeService.isOwnRecord(employeeId)) {
            SecurityService.require(Permissions.PERFORMANCE_VIEW);
            employeeService.requireVisible(employeeId);
        }
        return repository.findReviewsForEmployee(employeeId);
    }

    public PerformanceReview findById(long reviewId) {
        SecurityService.require(Permissions.PERFORMANCE_VIEW);
        PerformanceReview review = requireReview(reviewId);
        if (!employeeService.isOwnRecord(review.getEmployeeId())) {
            employeeService.requireVisible(review.getEmployeeId());
        }
        review.getItems().addAll(repository.findItems(reviewId));
        return review;
    }

    public List<PerformanceCriterion> allCriteria() {
        SecurityService.require(Permissions.PERFORMANCE_VIEW);
        return repository.findAllCriteria();
    }

    public List<PerformanceCriterion> activeCriteria() {
        SecurityService.require(Permissions.PERFORMANCE_VIEW);
        return repository.findActiveCriteria();
    }

    /** Total weight of the active criteria; healthy setups sum to 100. */
    public BigDecimal activeWeightTotal() {
        SecurityService.require(Permissions.PERFORMANCE_VIEW);
        return repository.findActiveCriteria().stream()
                .map(PerformanceCriterion::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ------------------------------------------------------------------
    // Review lifecycle
    // ------------------------------------------------------------------

    /** Creates a DRAFT review for one employee/period; returns the new id. */
    public long createReview(PerformanceReview review) {
        SecurityService.require(Permissions.PERFORMANCE_MANAGE);
        validateHeader(review);

        if (repository.periodExists(review.getEmployeeId(), review.getPeriodStart(),
                review.getPeriodEnd(), null)) {
            throw new ValidationException(List.of(
                    "A review for this employee and period already exists."));
        }

        long id = repository.insertReview(review);
        repository.updateReviewCode(id, "PRV-" + String.format("%04d", id));
        audit("CREATE", id,
                "Created performance review PRV-" + String.format("%04d", id)
                        + " for " + review.getEmployeeName() + " ("
                        + review.getPeriodStart() + " to " + review.getPeriodEnd() + ")");
        publishChange();
        return id;
    }

    /** Edits the header of a review still in the manager's hands. */
    public void updateDraft(PerformanceReview review) {
        SecurityService.require(Permissions.PERFORMANCE_MANAGE);
        PerformanceReview existing = requireReview(review.getId());
        if (!existing.isEditableDraft()) {
            throw new BusinessException("Not editable",
                    "Only reviews in MANAGER_REVIEW can be edited.");
        }
        validateHeader(review);
        if (repository.periodExists(review.getEmployeeId(), review.getPeriodStart(),
                review.getPeriodEnd(), review.getId())) {
            throw new ValidationException(List.of(
                    "A review for this employee and period already exists."));
        }
        repository.updateDraft(review.getId(), review.getReviewerId(),
                review.getPeriodStart(), review.getPeriodEnd(), review.getManagerComments());
        audit("UPDATE", review.getId(),
                "Updated draft header of " + existing.getReviewCode());
        publishChange();
    }

    /**
     * Replaces the criterion scores of a review in MANAGER_REVIEW. Every
     * score must sit inside 1..5 with at most one decimal place.
     */
    public void saveScores(long reviewId, List<PerformanceReviewItem> items) {
        SecurityService.require(Permissions.PERFORMANCE_MANAGE);
        PerformanceReview review = requireReview(reviewId);
        if (!review.isEditableDraft()) {
            throw new BusinessException("Scoring closed",
                    "Scores can only be edited while the review is in MANAGER_REVIEW.");
        }
        validateItems(items);

        TransactionManager.execute(tx -> {
            repository.replaceItems(tx, reviewId, items);
            return null;
        });
        audit("SCORE", reviewId,
                "Recorded " + items.size() + " criterion score(s) for "
                        + review.getReviewCode());
        publishChange();
    }

    /**
     * Manager submits: requires every ACTIVE criterion to be scored, then the
     * review moves to EMPLOYEE_FEEDBACK for the employee's comments.
     */
    public void submitToFeedback(long reviewId) {
        SecurityService.require(Permissions.PERFORMANCE_MANAGE);
        PerformanceReview review = requireReview(reviewId);
        if (!review.isEditableDraft()) {
            throw new BusinessException("Already submitted",
                    "Only reviews in MANAGER_REVIEW can move to employee feedback.");
        }
        Set<Long> activeIds = new HashSet<>(repository.findActiveCriteria().stream()
                .map(PerformanceCriterion::getId).toList());
        Set<Long> scoredIds = new HashSet<>(repository.findScoredCriteriaIds(reviewId));
        List<String> missing = repository.findAllCriteria().stream()
                .filter(criterion -> activeIds.contains(criterion.getId())
                        && !scoredIds.contains(criterion.getId()))
                .map(PerformanceCriterion::getName)
                .toList();
        if (!missing.isEmpty()) {
            throw new ValidationException(List.of(
                    "Score every active criterion first. Missing: "
                            + String.join(", ", missing) + "."));
        }

        repository.updateStageAndStatus(reviewId,
                PerformanceReview.STAGE_EMPLOYEE_FEEDBACK,
                PerformanceReview.STATUS_IN_PROGRESS);
        audit("SUBMIT", reviewId,
                review.getReviewCode() + " submitted for employee feedback");
        publishChange();
    }

    /**
     * Employee comments at the FEEDBACK stage. Allowed for PERFORMANCE_MANAGE
     * holders or the reviewed employee themself (self-service).
     */
    public void recordFeedback(long reviewId, String comments) {
        List<String> errors = new ArrayList<>();
        Validators.required(errors, comments, "Employee comments");
        Validators.maxLength(errors, comments, 2000, "Employee comments");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        PerformanceReview review = repository.findReviewById(reviewId)
                .orElseThrow(() -> new BusinessException("Review not found",
                        "The performance review no longer exists."));

        boolean manager = SessionContext.has(Permissions.PERFORMANCE_MANAGE);
        if (!manager) {
            SecurityService.require(Permissions.PERFORMANCE_VIEW);
            if (!isOwnReview(review.getEmployeeId())) {
                throw new BusinessException("Not your review",
                        "Only the reviewed employee or HR can add feedback.");
            }
        }
        if (!PerformanceReview.STAGE_EMPLOYEE_FEEDBACK.equals(review.getStage())) {
            throw new BusinessException("Wrong stage",
                    "Feedback is recorded while the review is in EMPLOYEE_FEEDBACK.");
        }

        repository.updateEmployeeComments(reviewId, Validators.normalize(comments));
        audit("FEEDBACK", reviewId,
                "Employee feedback recorded on " + review.getReviewCode()
                        + (manager ? " by HR" : " by the employee"));
        publishChange();
    }

    /**
     * Finalizes the review: computes the weighted overall score from the
     * stored items and locks the record permanently.
     */
    public BigDecimal finalizeReview(long reviewId) {
        SecurityService.require(Permissions.PERFORMANCE_MANAGE);
        PerformanceReview review = requireReview(reviewId);
        if (!PerformanceReview.STAGE_EMPLOYEE_FEEDBACK.equals(review.getStage())) {
            throw new BusinessException("Cannot finalize",
                    "Finalization happens after the EMPLOYEE_FEEDBACK stage.");
        }

        List<PerformanceScoreCalculator.ScoredWeight> scored =
                repository.findItems(reviewId).stream()
                        .map(item -> new PerformanceScoreCalculator.ScoredWeight(
                                item.getScore(), item.getCriteriaWeight()))
                        .toList();
        BigDecimal overall = PerformanceScoreCalculator.weightedOverall(scored);
        if (overall == null) {
            throw new BusinessException("Nothing to finalize",
                    "The review has no scored criteria.");
        }

        repository.finalizeReview(reviewId, overall, SessionContext.currentUserId());
        audit("FINALIZE", reviewId,
                review.getReviewCode() + " finalized with overall score "
                        + overall.toPlainString());
        LOG.info("Performance review {} finalized at {}", review.getReviewCode(), overall);
        publishChange();
        return overall;
    }

    /** Cancels a review before finalization; history stays queryable. */
    public void cancel(long reviewId) {
        SecurityService.require(Permissions.PERFORMANCE_MANAGE);
        PerformanceReview review = requireReview(reviewId);
        if (PerformanceReview.STATUS_COMPLETED.equals(review.getStatus())
                || PerformanceReview.STATUS_CANCELLED.equals(review.getStatus())) {
            throw new BusinessException("Already decided",
                    "Completed or cancelled reviews cannot be cancelled again.");
        }
        repository.cancelReview(reviewId);
        audit("CANCEL", reviewId, review.getReviewCode() + " cancelled");
        publishChange();
    }

    // ------------------------------------------------------------------
    // Criterion management
    // ------------------------------------------------------------------

    /** Creates or updates a criterion; affects future reviews only. */
    public long saveCriterion(PerformanceCriterion criterion) {
        SecurityService.require(Permissions.PERFORMANCE_MANAGE);
        validateCriterion(criterion);

        if (criterion.getId() == null) {
            if (repository.criterionCodeExists(criterion.getCode(), null)) {
                throw new ValidationException(List.of(
                        "Criteria code '" + criterion.getCode() + "' is already in use."));
            }
            criterion.setActive(true);
            long id = repository.insertCriterion(criterion);
            audit("CREATE", "PerformanceCriterion", id,
                    "Created criterion '" + criterion.getName() + "' (weight "
                            + criterion.getWeight().toPlainString() + "%)");
            publishChange();
            return id;
        }
        requireCriterion(criterion.getId());
        repository.updateCriterion(criterion);
        audit("UPDATE", "PerformanceCriterion", criterion.getId(),
                "Updated criterion '" + criterion.getName() + "'");
        publishChange();
        return criterion.getId();
    }

    public void setCriterionActive(long criterionId, boolean active) {
        SecurityService.require(Permissions.PERFORMANCE_MANAGE);
        PerformanceCriterion criterion = requireCriterion(criterionId);
        criterion.setActive(active);
        repository.updateCriterion(criterion);
        audit("STATUS_CHANGE", "PerformanceCriterion", criterionId,
                "Criterion '" + criterion.getName() + "' "
                        + (active ? "activated" : "deactivated"));
        publishChange();
    }

    // ------------------------------------------------------------------
    // Validation & helpers
    // ------------------------------------------------------------------

    private void validateHeader(PerformanceReview review) {
        List<String> errors = new ArrayList<>();
        if (review.getEmployeeId() <= 0) {
            errors.add("Employee is required.");
        }
        LocalDate start = review.getPeriodStart();
        LocalDate end = review.getPeriodEnd();
        if (start == null || end == null) {
            errors.add("Review period start and end are required.");
        } else if (end.isBefore(start)) {
            errors.add("Period end cannot be before the period start.");
        } else if (start.plusYears(1).isBefore(end)) {
            errors.add("A review period cannot exceed one year.");
        }
        Validators.maxLength(errors, review.getManagerComments(), 2000, "Manager comments");
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        review.setManagerComments(Validators.normalize(review.getManagerComments()));
    }

    private void validateItems(List<PerformanceReviewItem> items) {
        List<String> errors = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            throw new ValidationException(List.of("At least one score is required."));
        }
        Set<Long> seen = new HashSet<>();
        for (PerformanceReviewItem item : items) {
            if (item.getCriteriaId() <= 0) {
                errors.add("Every score needs a criterion.");
                continue;
            }
            if (!seen.add(item.getCriteriaId())) {
                errors.add("Each criterion may only be scored once.");
            }
            BigDecimal score = item.getScore();
            if (score == null) {
                errors.add("Score is required for every item.");
            } else {
                if (score.scale() > 1) {
                    errors.add("Scores allow at most one decimal place.");
                }
                if (score.compareTo(MIN_SCORE) < 0 || score.compareTo(MAX_SCORE) > 0) {
                    errors.add("Scores must be between 1 and 5.");
                }
            }
            Validators.maxLength(errors, item.getComments(), 500, "Item comments");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    private void validateCriterion(PerformanceCriterion criterion) {
        List<String> errors = new ArrayList<>();
        Validators.required(errors, criterion.getCode(), "Criteria code");
        Validators.pattern(errors, criterion.getCode(), "Criteria code",
                Validators.CODE_PATTERN, "PROD");
        Validators.required(errors, criterion.getName(), "Criteria name");
        Validators.maxLength(errors, criterion.getName(), 100, "Criteria name");
        Validators.maxLength(errors, criterion.getDescription(), 500, "Description");
        if (criterion.getWeight() == null) {
            errors.add("Weight is required.");
        } else if (criterion.getWeight().signum() < 0
                || criterion.getWeight().compareTo(BigDecimal.valueOf(100)) > 0) {
            errors.add("Weight must be between 0 and 100 percent.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        criterion.setCode(Validators.normalize(criterion.getCode()).toUpperCase());
        criterion.setName(Validators.normalize(criterion.getName()));
        criterion.setDescription(Validators.normalize(criterion.getDescription()));
    }

    private PerformanceReview requireReview(long reviewId) {
        return repository.findReviewById(reviewId).orElseThrow(() ->
                new BusinessException("Review not found",
                        "The performance review no longer exists."));
    }

    private PerformanceCriterion requireCriterion(long criterionId) {
        return repository.findCriterionById(criterionId).orElseThrow(() ->
                new BusinessException("Criterion not found",
                        "The performance criterion no longer exists."));
    }

    /** Self-service check via the user email link (same pattern as LeaveService). */
    private boolean isOwnReview(long employeeId) {
        Long linked = new Sql().first(
                "SELECT id FROM employees WHERE email = (SELECT email FROM users WHERE id = ?)",
                rs -> rs.getLong("id"),
                SessionContext.currentUserId()).orElse(null);
        return linked != null && linked == employeeId;
    }

    private void audit(String action, Long entityId, String description) {
        auditService.record(action, "PERFORMANCE", "PerformanceReview", entityId, description);
    }

    private void audit(String action, String entity, Long entityId, String description) {
        auditService.record(action, "PERFORMANCE", entity, entityId, description);
    }

    private void publishChange() {
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        EventBus.publish(new Events.DataChanged("dashboard"));
    }
}
