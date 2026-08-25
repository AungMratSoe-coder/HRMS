package com.ams.hrms.ui.performance;

import java.awt.BorderLayout;
import java.awt.Font;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;

import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.controller.PerformanceController;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.PerformanceCriterion;
import com.ams.hrms.model.PerformanceReview;
import com.ams.hrms.model.PerformanceReviewItem;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Performance review dialog (spec section 22) with four modes:
 * CREATE drafts a new review; DRAFT edits an existing header; SCORE records
 * the 1-5 criterion ratings with comments; FEEDBACK captures employee
 * comments during the feedback stage.
 */
public class ReviewDialog extends JDialog {

    public enum Mode {
        CREATE, DRAFT, SCORE, FEEDBACK
    }

    public enum Result {
        SAVED, CANCELLED
    }

    private final PerformanceController controller =
            new PerformanceController(com.ams.hrms.config.ServiceRegistry.performanceService());

    private final Mode mode;
    private final List<Employee> employees = new ArrayList<>();
    private final List<PerformanceCriterion> criteria = new ArrayList<>();
    private final PerformanceReview existing;

    private FormField employeeField;
    private FormField reviewerField;
    private FormField periodStartField;
    private FormField periodEndField;
    private FormField managerCommentsField;
    private FormField employeeCommentsField;

    /** Per-criterion score editors for SCORE mode, keyed by criteria id. */
    private final Map<Long, JSpinner> scoreSpinners = new HashMap<>();
    private final Map<Long, JTextField> scoreComments = new HashMap<>();

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    public ReviewDialog(java.awt.Window owner, Mode mode, List<Employee> employees,
                        List<PerformanceCriterion> criteria, PerformanceReview existing) {
        super(owner, titleFor(mode), ModalityType.APPLICATION_MODAL);
        this.mode = mode;
        this.employees.addAll(employees);
        this.criteria.addAll(criteria);
        this.existing = existing;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(mode == Mode.SCORE ? 560 : 500, mode == Mode.SCORE ? 640 : 520);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
        populate();
    }

    private static String titleFor(Mode mode) {
        return switch (mode) {
            case CREATE -> "New Performance Review";
            case DRAFT -> "Edit Draft Review";
            case SCORE -> "Score Criteria";
            case FEEDBACK -> "Employee Feedback";
        };
    }

    // ------------------------------------------------------------------
    // Form
    // ------------------------------------------------------------------

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 2, insets 24 28 12 28, gap 10", "[grow,fill][grow,fill]"));

        JLabel titleLabel = new JLabel(switch (mode) {
            case CREATE -> "Start a review period";
            case DRAFT -> existing == null ? "" : "Edit " + existing.getReviewCode();
            case SCORE -> existing == null ? "" : "Rate "
                    + existing.getEmployeeName() + " - " + existing.getReviewCode();
            case FEEDBACK -> existing == null ? "" : "Feedback on "
                    + existing.getReviewCode();
        });
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "span 2, wrap unrelated");
        switch (mode) {
            case CREATE, DRAFT -> buildHeaderFields(form);
            case SCORE -> buildScoreGrid(form);
            case FEEDBACK -> buildFeedbackFields(form);
        }
        form.add(errorBanner, "span 2");
        return form;
    }

    @SuppressWarnings("unchecked")
    private void buildHeaderFields(JPanel form) {
        employeeField = FormField.custom("Employee", true, buildEmployeeCombo());
        reviewerField = FormField.custom("Reviewer", false,
                buildReviewerCombo());
        periodStartField = FormField.datePicker("Period Start", true);
        periodEndField = FormField.datePicker("Period End", true);
        managerCommentsField = FormField.textArea("Manager Comments", false);

        form.add(employeeField, "span 2");
        form.add(reviewerField, "span 2");
        form.add(periodStartField);
        form.add(periodEndField);
        form.add(managerCommentsField, "span 2");
    }

    private javax.swing.JComboBox<String> buildEmployeeCombo() {
        List<String> displays = new ArrayList<>();
        for (var employee : employees) {
            displays.add(employee.getCode() + " - " + employee.getFullName());
        }
        javax.swing.JComboBox<String> combo =
                new javax.swing.JComboBox<>(displays.toArray(new String[0]));
        if (combo.getItemCount() > 0) {
            combo.setSelectedIndex(-1);
        }
        return combo;
    }

    private javax.swing.JComboBox<String> buildReviewerCombo() {
        List<String> displays = new ArrayList<>();
        for (var employee : employees) {
            displays.add(employee.getCode() + " - " + employee.getFullName());
        }
        return new javax.swing.JComboBox<>(displays.toArray(new String[0]));
    }

    /** One spinner + comment row per active criterion. */
    private void buildScoreGrid(JPanel form) {
        form.remove(errorBanner);
        JPanel grid = new JPanel(new MigLayout(
                "wrap 3, insets 0, gap 8",
                "[grow,fill][90!][grow,fill]"));

        JLabel nameHeader = new JLabel("Criterion");
        JLabel scoreHeader = new JLabel("Score (1-5)");
        JLabel noteHeader = new JLabel("Comments");
        for (JLabel header : new JLabel[]{nameHeader, scoreHeader, noteHeader}) {
            header.setFont(header.getFont().deriveFont(Font.BOLD, 12f));
        }
        grid.add(nameHeader);
        grid.add(scoreHeader);
        grid.add(noteHeader);

        Map<Long, PerformanceReviewItem> scored = new HashMap<>();
        if (existing != null) {
            for (var item : existing.getItems()) {
                scored.put(item.getCriteriaId(), item);
            }
        }
        boolean readOnly = existing != null && !existing.isEditableDraft();

        for (PerformanceCriterion criterion : criteria) {
            if (!criterion.isActive()) {
                continue;
            }
            JLabel nameLabel = new JLabel(criterion.getName() + " ("
                    + criterion.getWeight().toPlainString() + "%)");

            JSpinner spinner = new JSpinner(new javax.swing.SpinnerNumberModel(
                    3.0, 1.0, 5.0, 0.5));
            JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0.0");
            spinner.setEditor(editor);
            PerformanceReviewItem prior = scored.get(criterion.getId());
            if (prior != null) {
                spinner.setValue(prior.getScore().doubleValue());
            }

            JTextField commentField = new JTextField();
            if (prior != null && prior.getComments() != null) {
                commentField.setText(prior.getComments());
            }

            scoreSpinners.put(criterion.getId(), spinner);
            scoreComments.put(criterion.getId(), commentField);
            grid.add(nameLabel);
            grid.add(spinner);
            grid.add(commentField);
        }
        if (readOnly) {
            for (var spinner : scoreSpinners.values()) {
                spinner.setEnabled(false);
            }
            for (var field : scoreComments.values()) {
                field.setEditable(false);
            }
        }

        JScrollPane scroll = new JScrollPane(grid);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        form.add(scroll, "span 2, height 380::");
        saveButton.setText(readOnly ? "Close" : "Save Scores");
        form.add(errorBanner, "span 2");
    }

    private void buildFeedbackFields(JPanel form) {
        employeeCommentsField = FormField.textArea("Your comments", true);
        if (existing != null && existing.getEmployeeComments() != null) {
            employeeCommentsField.setText(existing.getEmployeeComments());
        }
        form.add(employeeCommentsField, "span 2");
    }

    private void populate() {
        if (mode == Mode.CREATE || mode == Mode.DRAFT) {
            periodStartField.setDate(LocalDate.now().withDayOfYear(1));
            periodEndField.setDate(LocalDate.now());
        }
        if (existing == null) {
            return;
        }
        if (mode == Mode.DRAFT) {
            selectByEmployeeId(existing.getEmployeeId(), employeeField);
            selectByEmployeeId(existing.getReviewerId(), reviewerField);
            periodStartField.setDate(existing.getPeriodStart());
            periodEndField.setDate(existing.getPeriodEnd());
            managerCommentsField.setText(existing.getManagerComments());
        } else if (mode == Mode.FEEDBACK) {
            saveButton.setText("Submit Feedback");
        }
    }

    @SuppressWarnings("unchecked")
    private void selectByEmployeeId(Long employeeId, FormField target) {
        if (employeeId == null || target == null || target.editor() == null) {
            return;
        }
        var combo = (javax.swing.JComboBox<String>) target.editor();
        for (int i = 0; i < employees.size(); i++) {
            Long id = employees.get(i).getId();
            if (id != null && id == employeeId) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new MigLayout("insets 14 28, gap 10, alignx right", "push[][]"));
        buttons.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                Palette.color(Role.CARD_BORDER)));

        saveButton.setText(switch (mode) {
            case CREATE -> "Create Draft";
            default -> saveButton.getText();
        });
        JButton cancel = new ModernButton("Cancel", ModernButton.Variant.OUTLINE);
        cancel.addActionListener(event -> dispose());
        saveButton.addActionListener(event -> submit());
        buttons.add(cancel);
        buttons.add(saveButton);
        return buttons;
    }

    // ------------------------------------------------------------------
    // Submit
    // ------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private void submit() {
        errorBanner.setVisible(false);
        switch (mode) {
            case CREATE -> submitCreate();
            case DRAFT -> submitDraft();
            case SCORE -> submitScores();
            case FEEDBACK -> submitFeedback();
        }
    }

    private int requireIndex(FormField field, List<String> errors, String label) {
        var combo = (javax.swing.JComboBox<String>) field.editor();
        int index = combo.getSelectedIndex();
        if (index < 0) {
            errors.add(label + " is required.");
        }
        return index;
    }

    private void submitCreate() {
        List<String> localErrors = new ArrayList<>();
        int employeeIndex = requireIndex(employeeField, localErrors, "Employee");
        int reviewerIndex = reviewerField.editor() instanceof javax.swing.JComboBox
                ? ((javax.swing.JComboBox<String>) reviewerField.editor()).getSelectedIndex()
                : -1;
        if (periodStartField.getDate() == null) {
            localErrors.add("Period start is required.");
        }
        if (periodEndField.getDate() == null) {
            localErrors.add("Period end is required.");
        }
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        PerformanceReview review = new PerformanceReview();
        review.setEmployeeId(employees.get(employeeIndex).getId());
        review.setReviewerId(reviewerIndex >= 0
                ? employees.get(reviewerIndex).getId() : null);
        review.setPeriodStart(periodStartField.getDate());
        review.setPeriodEnd(periodEndField.getDate());
        review.setManagerComments(managerCommentsField.getText());

        saveButton.setEnabled(false);
        controller.createReview(review,
                id -> {
                    result = Result.SAVED;
                    dispose();
                },
                error -> resumeOn(error));
    }

    private void submitDraft() {
        List<String> localErrors = new ArrayList<>();
        int employeeIndex = requireIndex(employeeField, localErrors, "Employee");
        int reviewerIndex = ((javax.swing.JComboBox<String>) reviewerField.editor())
                .getSelectedIndex();
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        existing.setEmployeeId(employees.get(employeeIndex).getId());
        existing.setReviewerId(reviewerIndex >= 0
                ? employees.get(reviewerIndex).getId() : null);
        existing.setPeriodStart(periodStartField.getDate());
        existing.setPeriodEnd(periodEndField.getDate());
        existing.setManagerComments(managerCommentsField.getText());

        saveButton.setEnabled(false);
        controller.updateDraft(existing,
                () -> {
                    result = Result.SAVED;
                    dispose();
                },
                this::resumeOn);
    }

    private void submitScores() {
        List<PerformanceReviewItem> items = new ArrayList<>();
        for (var entry : scoreSpinners.entrySet()) {
            PerformanceReviewItem item = new PerformanceReviewItem();
            item.setCriteriaId(entry.getKey());
            item.setScore(BigDecimal.valueOf(
                    ((Number) entry.getValue().getValue()).doubleValue()));
            JTextField commentField = scoreComments.get(entry.getKey());
            item.setComments(commentField == null ? null : commentField.getText());
            items.add(item);
        }

        saveButton.setEnabled(false);
        controller.saveScores(existing.getId(), items,
                () -> {
                    result = Result.SAVED;
                    dispose();
                },
                this::resumeOn);
    }

    private void submitFeedback() {
        saveButton.setEnabled(false);
        controller.recordFeedback(existing.getId(), employeeCommentsField.getText(),
                () -> {
                    result = Result.SAVED;
                    dispose();
                },
                this::resumeOn);
    }

    private void resumeOn(Exception error) {
        saveButton.setEnabled(true);
        if (error instanceof com.ams.hrms.exception.HrmsException hrmsException) {
            showError(hrmsException.getUserMessage());
        } else {
            com.ams.hrms.exception.ErrorHandler.handle(error);
        }
    }

    private void showError(String message) {
        errorBanner.setText(message);
        errorBanner.setVisible(true);
        revalidate();
        repaint();
    }

    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
