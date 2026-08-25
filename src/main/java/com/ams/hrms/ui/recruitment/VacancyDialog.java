package com.ams.hrms.ui.recruitment;

import java.awt.BorderLayout;
import java.awt.Font;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;

import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.controller.RecruitmentController;
import com.ams.hrms.model.Department;
import com.ams.hrms.model.JobVacancy;
import com.ams.hrms.model.Position;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * Create/edit vacancy dialog (spec section 14): position envelope, headcount,
 * employment type, opening/closing dates and salary range.
 */
public class VacancyDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final RecruitmentController controller =
            new RecruitmentController(com.ams.hrms.config.ServiceRegistry.recruitmentService());

    private final List<Department> departments = new ArrayList<>();
    private final List<Position> positions = new ArrayList<>();
    private final JobVacancy existing;

    private FormField titleField;
    private FormField departmentField;
    private FormField positionField;
    private FormField employmentTypeField;
    private FormField salaryMinField;
    private FormField salaryMaxField;
    private FormField openingDateField;
    private FormField closingDateField;
    private FormField descriptionField;
    private FormField requirementsField;
    private JSpinner headcountSpinner;

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    public VacancyDialog(java.awt.Window owner, List<Department> departments,
                         List<Position> positions, JobVacancy existing) {
        super(owner, existing == null ? "New Vacancy" : "Edit Vacancy",
                ModalityType.APPLICATION_MODAL);
        this.departments.addAll(departments);
        this.positions.addAll(positions);
        this.existing = existing;
        saveButton.setText(existing == null ? "Open Vacancy" : "Save Changes");

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(520, 720);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
        populate();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 2, insets 24 28 12 28, gap 10", "[grow,fill][grow,fill]"));

        JLabel titleLabel = new JLabel(
                existing == null ? "Open a job vacancy" : "Edit " + existing.getVacancyCode());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        titleField = FormField.textField("Job Title", true);
        departmentField = FormField.custom("Department", true,
                buildDepartmentCombo());
        positionField = FormField.custom("Position", true,
                buildPositionCombo());

        headcountSpinner = new JSpinner(new javax.swing.SpinnerNumberModel(1, 1, 999, 1));
        FormField headcountField = FormField.custom("Headcount", true, headcountSpinner);
        employmentTypeField = FormField.comboBox("Employment Type",
                new String[]{"FULL_TIME", "PART_TIME", "CONTRACT", "INTERN", "PROBATION"}, true);

        salaryMinField = FormField.textField("Salary Min", false);
        salaryMaxField = FormField.textField("Salary Max", false);
        openingDateField = FormField.datePicker("Opening Date", true);
        closingDateField = FormField.datePicker("Closing Date", false);
        descriptionField = FormField.textArea("Job Description", false);
        requirementsField = FormField.textArea("Requirements", false);

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "span 2, wrap unrelated");
        form.add(titleField, "span 2");
        form.add(departmentField);
        form.add(positionField);
        form.add(headcountField);
        form.add(employmentTypeField);
        form.add(salaryMinField);
        form.add(salaryMaxField);
        form.add(openingDateField);
        form.add(closingDateField);
        form.add(descriptionField, "span 2");
        form.add(requirementsField, "span 2");
        form.add(errorBanner, "span 2");
        return form;
    }

    private javax.swing.JComboBox<String> buildDepartmentCombo() {
        List<String> displays = new ArrayList<>();
        for (var department : departments) {
            displays.add(department.getName());
        }
        return new javax.swing.JComboBox<>(displays.toArray(new String[0]));
    }

    private javax.swing.JComboBox<String> buildPositionCombo() {
        List<String> displays = new ArrayList<>();
        for (var position : positions) {
            displays.add(position.getName());
        }
        return new javax.swing.JComboBox<>(displays.toArray(new String[0]));
    }

    /** Fills the fields when editing an existing vacancy. */
    private void populate() {
        if (existing == null) {
            openingDateField.setDate(LocalDate.now());
            return;
        }
        titleField.setText(existing.getTitle());
        headcountSpinner.setValue(existing.getHeadcount());
        employmentTypeField.setText(existing.getEmploymentType());
        salaryMinField.setText(existing.getSalaryMin() == null
                ? "" : existing.getSalaryMin().toPlainString());
        salaryMaxField.setText(existing.getSalaryMax() == null
                ? "" : existing.getSalaryMax().toPlainString());
        openingDateField.setDate(existing.getOpeningDate());
        closingDateField.setDate(existing.getClosingDate());
        descriptionField.setText(existing.getJobDescription());
        requirementsField.setText(existing.getRequirements());

        for (int i = 0; i < departments.size(); i++) {
            if (departments.get(i).getId() != null
                    && departments.get(i).getId() == existing.getDepartmentId()) {
                ((javax.swing.JComboBox<?>) departmentField.editor()).setSelectedIndex(i);
            }
        }
        for (int i = 0; i < positions.size(); i++) {
            if (positions.get(i).getId() != null
                    && positions.get(i).getId() == existing.getPositionId()) {
                ((javax.swing.JComboBox<?>) positionField.editor()).setSelectedIndex(i);
            }
        }
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new MigLayout("insets 14 28, gap 10, alignx right", "push[][]"));
        buttons.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                Palette.color(Role.CARD_BORDER)));

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
        clearErrors();

        int departmentIndex = ((javax.swing.JComboBox<String>) departmentField.editor())
                .getSelectedIndex();
        int positionIndex = ((javax.swing.JComboBox<String>) positionField.editor())
                .getSelectedIndex();

        List<String> localErrors = new ArrayList<>();
        if (departmentIndex < 0) {
            localErrors.add("Department is required.");
        }
        if (positionIndex < 0) {
            localErrors.add("Position is required.");
        }

        BigDecimal salaryMin = Validators.parseMoney(localErrors,
                salaryMinField.getText(), "Salary min");
        BigDecimal salaryMax = Validators.parseMoney(localErrors,
                salaryMaxField.getText(), "Salary max");

        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        JobVacancy vacancy = existing == null ? new JobVacancy() : existing;
        vacancy.setTitle(titleField.getText());
        vacancy.setDepartmentId(departments.get(departmentIndex).getId());
        vacancy.setPositionId(positions.get(positionIndex).getId());
        vacancy.setHeadcount((Integer) headcountSpinner.getValue());
        vacancy.setEmploymentType(employmentTypeField.getText());
        vacancy.setSalaryMin(salaryMin);
        vacancy.setSalaryMax(salaryMax);
        vacancy.setOpeningDate(openingDateField.getDate());
        vacancy.setClosingDate(closingDateField.getDate());
        vacancy.setJobDescription(descriptionField.getText());
        vacancy.setRequirements(requirementsField.getText());

        saveButton.setEnabled(false);
        controller.saveVacancy(vacancy,
                () -> {
                    result = Result.SAVED;
                    dispose();
                },
                error -> {
                    saveButton.setEnabled(true);
                    if (error instanceof com.ams.hrms.exception.HrmsException hrmsException) {
                        showError(hrmsException.getUserMessage());
                    } else {
                        com.ams.hrms.exception.ErrorHandler.handle(error);
                    }
                });
    }

    private void clearErrors() {
        errorBanner.setVisible(false);
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

