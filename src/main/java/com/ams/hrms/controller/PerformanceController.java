package com.ams.hrms.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Consumer;

import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.PerformanceCriterion;
import com.ams.hrms.model.PerformanceReview;
import com.ams.hrms.model.PerformanceReviewItem;
import com.ams.hrms.service.PerformanceService;
import com.ams.hrms.util.UiThread;

/** View-controller for the Performance module; all calls run off the EDT. */
public class PerformanceController {

    private final PerformanceService performanceService;

    public PerformanceController(PerformanceService performanceService) {
        this.performanceService = performanceService;
    }

    // ------------------------------------------------------------------
    // Queries
    // ------------------------------------------------------------------

    public void loadReviews(String keyword, String status,
                            Consumer<List<PerformanceReview>> onSuccess) {
        UiThread.executeAsync("Load performance reviews",
                () -> performanceService.findAll(keyword, status), onSuccess);
    }

    public void loadReview(long reviewId, Consumer<PerformanceReview> onSuccess) {
        UiThread.executeAsync("Load review detail",
                () -> performanceService.findById(reviewId), onSuccess);
    }

    public void loadCriteria(Consumer<List<PerformanceCriterion>> onSuccess) {
        UiThread.executeAsync("Load criteria",
                () -> performanceService.allCriteria(), onSuccess);
    }

    public void loadEmployees(Consumer<List<Employee>> onSuccess) {
        UiThread.executeAsync("Load employees",
                () -> ServiceRegistry.employeeService().findAll(
                        new com.ams.hrms.repository.EmployeeRepository.Filter(
                                "", null, null, null)), onSuccess);
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    public void createReview(PerformanceReview review, Consumer<Long> onSuccess,
                             Consumer<Exception> onError) {
        UiThread.executeAsync("Create review",
                () -> performanceService.createReview(review), onSuccess, onError);
    }

    public void updateDraft(PerformanceReview review, Runnable onDone,
                            Consumer<Exception> onError) {
        UiThread.executeAsync("Update draft review",
                () -> {
                    performanceService.updateDraft(review);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void saveScores(long reviewId, List<PerformanceReviewItem> items,
                           Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Save scores",
                () -> {
                    performanceService.saveScores(reviewId, items);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void submitToFeedback(long reviewId, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Submit to feedback",
                () -> {
                    performanceService.submitToFeedback(reviewId);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void recordFeedback(long reviewId, String comments, Runnable onDone,
                               Consumer<Exception> onError) {
        UiThread.executeAsync("Record feedback",
                () -> {
                    performanceService.recordFeedback(reviewId, comments);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void finalize(long reviewId, Consumer<BigDecimal> onSuccess,
                         Consumer<Exception> onError) {
        UiThread.executeAsync("Finalize review",
                () -> performanceService.finalizeReview(reviewId), onSuccess, onError);
    }

    public void cancel(long reviewId, Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Cancel review",
                () -> {
                    performanceService.cancel(reviewId);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void saveCriterion(PerformanceCriterion criterion, Runnable onDone,
                              Consumer<Exception> onError) {
        UiThread.executeAsync("Save criterion",
                () -> {
                    performanceService.saveCriterion(criterion);
                    return null;
                },
                result -> onDone.run(), onError);
    }

    public void setCriterionActive(long criterionId, boolean active,
                                   Runnable onDone, Consumer<Exception> onError) {
        UiThread.executeAsync("Update criterion status",
                () -> {
                    performanceService.setCriterionActive(criterionId, active);
                    return null;
                },
                result -> onDone.run(), onError);
    }
}
