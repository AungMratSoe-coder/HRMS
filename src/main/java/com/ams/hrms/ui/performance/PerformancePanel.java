package com.ams.hrms.ui.performance;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.SearchField;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.PerformanceController;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.PerformanceCriterion;
import com.ams.hrms.model.PerformanceReview;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.service.PerformanceService;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

/**
 * Performance module (spec section 22): reviews tab with the full
 * MANAGER_REVIEW &rarr; EMPLOYEE_FEEDBACK &rarr; FINALIZED workflow plus a
 * criteria tab managing the weighted rating catalogue.
 */
public class PerformancePanel extends JPanel {

    private final PerformanceController controller =
            new PerformanceController(ServiceRegistry.performanceService());

    // --- reviews tab ---
    private final SearchField searchField = new SearchField("Search employee or code...");
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[]{
            "All Statuses", "DRAFT", "IN_PROGRESS", "COMPLETED", "CANCELLED"});
    private final SecureButton newReviewButton =
            new SecureButton("New Review", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.PERFORMANCE_MANAGE);
    private final HrmsTable reviewTable = HrmsTable.builder(
                    "ID", "Code", "Employee", "Reviewer", "Period",
                    "Overall", "Stage", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 85)
            .fixedColumn(4, 190)
            .fixedColumn(5, 70)
            .fixedColumn(6, 150)
            .badgeColumn(6)
            .badgeColumn(7)
            .contextMenu(this::buildReviewMenu)
            .build();
    private List<PerformanceReview> loadedReviews = List.of();

    // --- criteria tab ---
    private final JLabel weightSummary = new JLabel(" ");
    private final SecureButton newCriterionButton =
            new SecureButton("New Criterion", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.PERFORMANCE_MANAGE);
    private final HrmsTable criterionTable = HrmsTable.builder(
                    "ID", "Code", "Name", "Weight %", "Description", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 80)
            .fixedColumn(3, 80)
            .fixedColumn(5, 90)
            .contextMenu(this::buildCriterionMenu)
            .build();
    private List<PerformanceCriterion> loadedCriteria = List.of();

    public PerformancePanel() {
        super(new BorderLayout());
        setOpaque(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Reviews", buildReviewsTab());
        tabs.addTab("Criteria", buildCriteriaTab());
        add(tabs, BorderLayout.CENTER);

        wireEvents();
        refreshReviews();
        refreshCriteria();
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private JPanel buildReviewsTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10", "[grow,fill][][][fill]"));
        toolbar.setOpaque(false);
        toolbar.add(searchField);
        toolbar.add(statusFilter, "width 130!");
        toolbar.add(new JLabel(""), "width 4");
        toolbar.add(newReviewButton);

        tab.add(toolbar, BorderLayout.NORTH);
        tab.add(new JScrollPane(reviewTable), BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildCriteriaTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10"));
        toolbar.setOpaque(false);
        toolbar.add(weightSummary, "gapright 20");
        toolbar.add(newCriterionButton, "pushx, alignx right");

        tab.add(toolbar, BorderLayout.NORTH);
        tab.add(new JScrollPane(criterionTable), BorderLayout.CENTER);
        return tab;
    }

    private void wireEvents() {
        searchField.onTextChanged(text -> refreshReviews());
        statusFilter.addActionListener(event -> refreshReviews());
        newReviewButton.addActionListener(event -> openReviewDialog(
                ReviewDialog.Mode.CREATE, null));
        newCriterionButton.addActionListener(event -> openCriterionDialog(null));
    }

    // ------------------------------------------------------------------
    // Reviews
    // ------------------------------------------------------------------

    private void refreshReviews() {
        String status = statusFilter.getSelectedIndex() <= 0
                ? "" : String.valueOf(statusFilter.getSelectedItem());
        controller.loadReviews(searchField.getText(), status, reviews -> {
            loadedReviews = reviews;
            List<Object[]> rows = new ArrayList<>();
            for (var review : reviews) {
                rows.add(new Object[]{
                        review.getId(),
                        review.getReviewCode(),
                        review.getEmployeeCode() + " - " + review.getEmployeeName(),
                        review.getReviewerName() == null ? "-" : review.getReviewerName(),
                        review.getPeriodStart() + " to " + review.getPeriodEnd(),
                        review.getOverallScore() == null ? "-"
                                : review.getOverallScore().toPlainString() + " / 5",
                        review.getStage(),
                        review.getStatus()});
            }
            reviewTable.setRows(rows);
        });
    }

    private PerformanceReview selectedReview() {
        Object value = reviewTable.selectedValue(0);
        if (value == null) {
            return null;
        }
        long target = ((Number) value).longValue();
        return loadedReviews.stream()
                .filter(review -> review.getId() != null && review.getId() == target)
                .findFirst().orElse(null);
    }

    private void openReviewDialog(ReviewDialog.Mode mode, PerformanceReview existing) {
        UiThread.executeAsync("Load review dialog data",
                () -> new Object[]{
                        ServiceRegistry.employeeService().findAll(
                                new com.ams.hrms.repository.EmployeeRepository.Filter(
                                        "", null, null, null)),
                        ServiceRegistry.performanceService().activeCriteria(),
                        mode == ReviewDialog.Mode.CREATE
                                ? null : ServiceRegistry.performanceService()
                                        .findById(existing.getId())},
                result -> {
                    Object[] parts = (Object[]) result;
                    @SuppressWarnings("unchecked")
                    var employees = (List<Employee>) parts[0];
                    @SuppressWarnings("unchecked")
                    var criteria = (List<PerformanceCriterion>) parts[1];
                    var review = (PerformanceReview) parts[2];
                    if (review != null) {
                        criteria.removeIf(criterion -> criterion.isActive() == false
                                && scoredIds(review).contains(criterion.getId()) == false);
                    }

                    ReviewDialog dialog =
                            new ReviewDialog(swingWindow(), mode, employees, criteria, review);
                    if (dialog.showDialog() == ReviewDialog.Result.SAVED) {
                        refreshReviews();
                    }
                });
    }

    private List<Long> scoredIds(PerformanceReview review) {
        return review.getItems().stream()
                .map(item -> item.getCriteriaId()).toList();
    }

    private void submitToFeedback() {
        var review = selectedReview();
        if (review == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Submit",
                "Submit " + review.getReviewCode() + " ("
                        + review.getEmployeeName() + ") for employee feedback?");
        if (!confirmed) {
            return;
        }
        controller.submitToFeedback(review.getId(), this::refreshReviews,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void finalizeReview() {
        var review = selectedReview();
        if (review == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Finalize",
                "Finalize " + review.getReviewCode() + "? The weighted overall score "
                        + "is computed and the record is locked permanently.");
        if (!confirmed) {
            return;
        }
        controller.finalize(review.getId(), overall -> {
            Dialogs.info(swingWindow(), "Finalized",
                    "Overall score: " + overall.toPlainString() + " / 5.");
            refreshReviews();
        }, error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void cancelSelected() {
        var review = selectedReview();
        if (review == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Cancel Review",
                "Cancel " + review.getReviewCode() + "?");
        if (!confirmed) {
            return;
        }
        controller.cancel(review.getId(), this::refreshReviews,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private JPopupMenu buildReviewMenu() {
        JPopupMenu menu = new JPopupMenu();
        var review = selectedReview();
        boolean manage = SecurityService.can(Permissions.PERFORMANCE_MANAGE);
        String stage = review == null ? "" : review.getStage();
        String status = review == null ? "" : review.getStatus();

        JMenuItem editDraft = new JMenuItem("Edit Draft Header");
        editDraft.setEnabled(manage && "DRAFT".equals(status));
        editDraft.addActionListener(event ->
                openReviewDialog(ReviewDialog.Mode.DRAFT, selectedReview()));

        JMenuItem score = new JMenuItem("Score Criteria");
        score.setEnabled((manage || SecurityService.can(Permissions.PERFORMANCE_VIEW))
                && review != null && !"CANCELLED".equals(status)
                && ("MANAGER_REVIEW".equals(stage) || !review.getItems().isEmpty()));
        score.addActionListener(event ->
                openReviewDialog(ReviewDialog.Mode.SCORE, selectedReview()));

        JMenuItem submit = new JMenuItem("Submit for Feedback");
        submit.setEnabled(manage && PerformanceReview.STAGE_MANAGER_REVIEW.equals(stage)
                && !"CANCELLED".equals(status));
        submit.addActionListener(event -> submitToFeedback());

        JMenuItem feedback = new JMenuItem("Record Feedback...");
        feedback.setEnabled(review != null
                && PerformanceReview.STAGE_EMPLOYEE_FEEDBACK.equals(stage));
        feedback.addActionListener(event ->
                openReviewDialog(ReviewDialog.Mode.FEEDBACK, selectedReview()));

        JMenuItem finalizeItem = new JMenuItem("Finalize");
        finalizeItem.setEnabled(manage
                && PerformanceReview.STAGE_EMPLOYEE_FEEDBACK.equals(stage));
        finalizeItem.addActionListener(event -> finalizeReview());

        JMenuItem cancel = new JMenuItem("Cancel Review");
        cancel.setEnabled(manage && ("DRAFT".equals(status)
                || "IN_PROGRESS".equals(status)));
        cancel.addActionListener(event -> cancelSelected());

        menu.add(editDraft);
        menu.add(score);
        menu.add(submit);
        menu.add(feedback);
        menu.add(finalizeItem);
        menu.addSeparator();
        menu.add(cancel);
        return menu;
    }

    // ------------------------------------------------------------------
    // Criteria
    // ------------------------------------------------------------------

    private void refreshCriteria() {
        controller.loadCriteria(criteria -> {
            loadedCriteria = criteria;
            List<Object[]> rows = new ArrayList<>();
            java.math.BigDecimal total = java.math.BigDecimal.ZERO;
            for (var criterion : criteria) {
                if (criterion.isActive()) {
                    total = total.add(criterion.getWeight());
                }
                rows.add(new Object[]{
                        criterion.getId(),
                        criterion.getCode(),
                        criterion.getName(),
                        criterion.getWeight().toPlainString(),
                        criterion.getDescription() == null ? "-" : criterion.getDescription(),
                        criterion.isActive() ? "ACTIVE" : "INACTIVE"});
            }
            criterionTable.setRows(rows);
            weightSummary.setText("Active weight total: "
                    + total.toPlainString() + "%"
                    + (total.compareTo(java.math.BigDecimal.valueOf(100)) == 0
                            ? "" : "  (should sum to 100%)"));
        });
    }

    private PerformanceCriterion selectedCriterion() {
        Object value = criterionTable.selectedValue(0);
        if (value == null) {
            return null;
        }
        long target = ((Number) value).longValue();
        return loadedCriteria.stream()
                .filter(criterion -> criterion.getId() != null
                        && criterion.getId() == target)
                .findFirst().orElse(null);
    }

    private void openCriterionDialog(PerformanceCriterion existing) {
        CriterionDialog dialog = new CriterionDialog(swingWindow(), existing);
        if (dialog.showDialog() == CriterionDialog.Result.SAVED) {
            refreshCriteria();
        }
    }

    private JPopupMenu buildCriterionMenu() {
        JPopupMenu menu = new JPopupMenu();
        var criterion = selectedCriterion();
        boolean manage = SecurityService.can(Permissions.PERFORMANCE_MANAGE);

        JMenuItem edit = new JMenuItem("Edit Criterion");
        edit.setEnabled(criterion != null && manage);
        edit.addActionListener(event -> openCriterionDialog(selectedCriterion()));

        JMenuItem deactivate = new JMenuItem("Deactivate");
        deactivate.setEnabled(criterion != null && criterion.isActive() && manage);
        deactivate.addActionListener(event -> changeCriterionActive(false));

        JMenuItem activate = new JMenuItem("Activate");
        activate.setEnabled(criterion != null && !criterion.isActive() && manage);
        activate.addActionListener(event -> changeCriterionActive(true));

        menu.add(edit);
        menu.addSeparator();
        menu.add(activate);
        menu.add(deactivate);
        return menu;
    }

    private void changeCriterionActive(boolean active) {
        var criterion = selectedCriterion();
        if (criterion == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(),
                active ? "Activate" : "Deactivate",
                (active ? "Activate" : "Deactivate") + " criterion '"
                        + criterion.getName() + "'?");
        if (!confirmed) {
            return;
        }
        UiThread.runAsync("Update criterion", () ->
                ServiceRegistry.performanceService()
                        .setCriterionActive(criterion.getId(), active));
        UiThread.runLater(this::refreshCriteria);
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
