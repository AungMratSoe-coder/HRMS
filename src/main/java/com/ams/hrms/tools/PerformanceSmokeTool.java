package com.ams.hrms.tools;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.PerformanceCriterion;
import com.ams.hrms.model.PerformanceReview;
import com.ams.hrms.model.PerformanceReviewItem;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.PerformanceScoreCalculator;
import com.ams.hrms.service.PerformanceService;

/**
 * Development-only Phase 18 verification against the live database: draft
 * creation, duplicate-period guard, partial-score submit block, full scoring
 * &rarr; feedback &rarr; finalize with weighted overall verification,
 * immutability after finalization, cancel path, criterion weight guard and
 * RBAC denial for FINANCE. Idempotent cleanup afterwards.
 */
public final class PerformanceSmokeTool {

    private static int failures;
    private static long draftReviewId;
    private static long cancelReviewId;
    private static long newCriterionId;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        PerformanceService performance = ServiceRegistry.performanceService();

        purgeArtifacts();
        authService.login("admin", "Admin@123");

        Long employeeId = new Sql().scalarLong(
                "SELECT id FROM employees WHERE employee_code = 'EMP-0003'");
        Long reviewerId = new Sql().scalarLong(
                "SELECT id FROM employees WHERE employee_code = 'EMP-0001'");
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);

        // --- draft ------------------------------------------------------------
        PerformanceReview review = new PerformanceReview();
        review.setEmployeeId(employeeId);
        review.setReviewerId(reviewerId);
        review.setPeriodStart(start);
        review.setPeriodEnd(end);
        check("create DRAFT review with generated code", () -> {
            draftReviewId = performance.createReview(review);
            return performance.findById(draftReviewId).getReviewCode() != null
                    && PerformanceReview.STAGE_MANAGER_REVIEW
                            .equals(performance.findById(draftReviewId).getStage());
        });

        check("duplicate employee+period rejected",
                () -> {
                    try {
                        performance.createReview(review);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        check("submit blocked until every active criterion is scored",
                () -> {
                    try {
                        performance.submitToFeedback(draftReviewId);
                        return false;
                    } catch (ValidationException expected) {
                        return expected.getErrors().get(0).contains("Missing");
                    }
                });

        // --- scoring ----------------------------------------------------------
        List<PerformanceCriterion> active = performance.activeCriteria();
        List<PerformanceReviewItem> scores = new ArrayList<>();
        for (int i = 0; i < active.size(); i++) {
            scores.add(withScore(blankItem(active.get(i).getId()),
                    BigDecimal.valueOf(1 + (i % 4)).add(BigDecimal.valueOf(0.5))));
        }

        check("invalid score rejected",
                () -> {
                    try {
                        var broken = new ArrayList<>(scores);
                        broken.set(0, withScore(broken.get(0), BigDecimal.valueOf(9)));
                        performance.saveScores(draftReviewId, broken);
                        return false;
                    } catch (ValidationException expected) {
                        return true;
                    }
                });

        check("save all scores", () -> {
            performance.saveScores(draftReviewId, scores);
            return performance.findById(draftReviewId).getItems().size() == active.size();
        });

        // --- feedback + finalize ----------------------------------------------
        BigDecimal expectedOverall = PerformanceScoreCalculator.weightedOverall(
                scores.stream()
                        .map(item -> new PerformanceScoreCalculator.ScoredWeight(
                                item.getScore(),
                                active.stream()
                                        .filter(c -> c.getId() == item.getCriteriaId())
                                        .findFirst().orElseThrow().getWeight()))
                        .collect(Collectors.toList()));

        check("feedback blocked before submission",
                () -> {
                    try {
                        performance.recordFeedback(draftReviewId, "early feedback");
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        check("submit moves to EMPLOYEE_FEEDBACK", () -> {
            performance.submitToFeedback(draftReviewId);
            var stored = performance.findById(draftReviewId);
            return PerformanceReview.STAGE_EMPLOYEE_FEEDBACK.equals(stored.getStage())
                    && PerformanceReview.STATUS_IN_PROGRESS.equals(stored.getStatus());
        });

        check("employee feedback recorded", () -> {
            performance.recordFeedback(draftReviewId, "Agree with the assessment.");
            return "Agree with the assessment."
                    .equals(performance.findById(draftReviewId).getEmployeeComments());
        });

        check("finalize computes weighted overall and locks", () -> {
            BigDecimal overall = performance.finalizeReview(draftReviewId);
            var stored = performance.findById(draftReviewId);
            return overall.compareTo(expectedOverall) == 0
                    && PerformanceReview.STAGE_FINALIZED.equals(stored.getStage())
                    && PerformanceReview.STATUS_COMPLETED.equals(stored.getStatus())
                    && stored.getFinalizedAt() != null
                    && stored.getOverallScore().compareTo(expectedOverall) == 0;
        });

        check("finalized reviews are immutable",
                () -> {
                    try {
                        performance.saveScores(draftReviewId, scores);
                        return false;
                    } catch (BusinessException expected) {
                        return true;
                    }
                });

        // --- cancel path on a second review --------------------------------------
        PerformanceReview second = new PerformanceReview();
        second.setEmployeeId(employeeId);
        second.setReviewerId(reviewerId);
        second.setPeriodStart(LocalDate.of(2026, 7, 1));
        second.setPeriodEnd(LocalDate.of(2026, 12, 31));
        check("second period can be created then cancelled", () -> {
            cancelReviewId = performance.createReview(second);
            performance.cancel(cancelReviewId);
            return PerformanceReview.STATUS_CANCELLED
                    .equals(performance.findById(cancelReviewId).getStatus());
        });

        // --- criteria management ----------------------------------------------
        check("criterion create + duplicate code rejected + deactivate", () -> {
            PerformanceCriterion criterion = new PerformanceCriterion();
            criterion.setCode("SMOKE");
            criterion.setName("Smoke Criterion");
            criterion.setWeight(BigDecimal.TEN);
            newCriterionId = performance.saveCriterion(criterion);

            boolean duplicateBlocked;
            try {
                performance.saveCriterion(criterion);
                duplicateBlocked = false;
            } catch (ValidationException expected) {
                duplicateBlocked = true;
            }
            performance.setCriterionActive(newCriterionId, false);
            return duplicateBlocked && !performance.allCriteria().stream()
                    .filter(c -> c.getId() == newCriterionId)
                    .findFirst().orElseThrow().isActive();
        });

        // --- RBAC: FINANCE has no PERFORMANCE permissions ------------------------
        authService.logout();
        authService.login("finance", "Finance@123");
        check("finance user denied performance access at service gate",
                () -> {
                    try {
                        performance.findAll(null, null);
                        return false;
                    } catch (AuthorizationException expected) {
                        return true;
                    }
                });
        authService.logout();

        // --- cleanup ---------------------------------------------------------------
        authService.login("admin", "Admin@123");
        purgeArtifacts();
        System.out.println("cleanup: smoke reviews + criterion removed");

        DatabaseConfig.close();
        if (failures > 0) {
            System.out.println("RESULT: " + failures + " FAILURE(S)");
            System.exit(1);
        }
        System.out.println("RESULT: ALL CHECKS PASSED");
        System.exit(0);
    }

    private static PerformanceReviewItem blankItem(long criteriaId) {
        PerformanceReviewItem item = new PerformanceReviewItem();
        item.setCriteriaId(criteriaId);
        item.setComments("smoke score");
        return item;
    }

    private static PerformanceReviewItem withScore(PerformanceReviewItem source,
                                                   BigDecimal score) {
        PerformanceReviewItem item = new PerformanceReviewItem();
        item.setCriteriaId(source.getCriteriaId());
        item.setScore(score);
        item.setComments(source.getComments());
        return item;
    }

    /** Removes smoke-created reviews and the smoke criterion row. */
    private static void purgeArtifacts() {
        if (newCriterionId > 0) {
            new Sql().executeUpdate(
                    "DELETE FROM performance_criteria WHERE id = ?", newCriterionId);
            newCriterionId = 0;
        }
        Long employeeId = new Sql().first(
                "SELECT id FROM employees WHERE employee_code = 'EMP-0003'",
                rs -> rs.getLong(1)).orElse(0L);
        if (employeeId > 0) {
            new Sql().executeUpdate(
                    "DELETE FROM performance_reviews WHERE employee_id = ?", employeeId);
        }
    }

    private static void check(String label, BooleanCheck action) {
        try {
            boolean passed = action.run();
            System.out.println((passed ? "OK   " : "FAIL ") + label);
            if (!passed) {
                failures++;
            }
        } catch (Exception e) {
            System.out.println("FAIL " + label + " -> unexpected "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            failures++;
        }
    }

    @FunctionalInterface
    private interface BooleanCheck {
        boolean run() throws Exception;
    }
}
