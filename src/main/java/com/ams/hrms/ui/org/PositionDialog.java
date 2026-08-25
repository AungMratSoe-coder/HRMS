package com.ams.hrms.ui.org;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.PositionController;
import com.ams.hrms.exception.ErrorHandler;
import com.ams.hrms.exception.HrmsException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Department;
import com.ams.hrms.model.Position;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * Create/Edit dialog for positions (spec section 13). Departments are picked
 * from active departments; salary fields accept blank (optional envelope).
 * Money text is parsed locally so format errors show in the banner without a
 * server round-trip; range checks still run in the service.
 */
public class PositionDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final PositionController controller =
            new PositionController(ServiceRegistry.positionService());

    private final Position position;
    private final boolean isNew;
    private final List<Department> departmentOptions = new ArrayList<>();

    private FormField codeField;
    private FormField nameField;
    private FormField departmentField;
    private FormField minSalaryField;
    private FormField maxSalaryField;
    private FormField descriptionField;
    private javax.swing.JComboBox<String> statusCombo;
    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    public PositionDialog(java.awt.Window owner, Position existing, List<Department> departments) {
        super(owner, existing == null ? "New Position" : "Edit Position",
                ModalityType.APPLICATION_MODAL);
        this.position = existing == null ? new Position() : existing;
        this.isNew = existing == null;
        for (Department department : departments) {
            if ("ACTIVE".equals(department.getStatus())) {
                departmentOptions.add(department);
            }
        }

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(470, 660);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10",
                "[grow,fill]",
                "[][][][][][][][grow][]"));

        JLabel titleLabel = new JLabel(isNew ? "Create position" : "Edit position");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        codeField = FormField.textField("Code *", true);
        nameField = FormField.textField("Position Name *", true);
        departmentField = FormField.custom("Department *", true, buildDepartmentCombo());
        minSalaryField = FormField.textField("Minimum Salary", false);
        maxSalaryField = FormField.textField("Maximum Salary", false);
        descriptionField = FormField.textArea("Description", false);

        if (!isNew) {
            codeField.setText(position.getCode());
            nameField.setText(position.getName());
            if (position.getMinSalary() != null) {
                minSalaryField.setText(position.getMinSalary().toPlainString());
            }
            if (position.getMaxSalary() != null) {
                maxSalaryField.setText(position.getMaxSalary().toPlainString());
            }
            descriptionField.setText(position.getDescription());
            selectDepartment(position.getDepartmentId());

            statusCombo = new javax.swing.JComboBox<>(new String[]{"ACTIVE", "INACTIVE"});
            statusCombo.setSelectedItem(position.getStatus());
        } else {
            selectDepartment(null);
        }

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVerticalTextPosition(javax.swing.SwingConstants.TOP);
        errorBanner.setVisible(false);

        form.add(titleLabel);
        form.add(codeField);
        form.add(nameField);
        form.add(departmentField);

        JPanel salaryRow = new JPanel(new MigLayout("insets 0, gap 10", "[grow][grow]"));
        salaryRow.setOpaque(false);
        salaryRow.add(minSalaryField, "grow");
        salaryRow.add(maxSalaryField, "grow");
        form.add(salaryRow, "growx");

        form.add(descriptionField, "height 76!");
        if (!isNew) {
            form.add(wrapLabeled("Status", statusCombo));
        }
        form.add(errorBanner);
        return form;
    }

    private javax.swing.JComboBox<String> buildDepartmentCombo() {
        String[] displays = new String[departmentOptions.size()];
        for (int i = 0; i < departmentOptions.size(); i++) {
            Department department = departmentOptions.get(i);
            displays[i] = department.getCode() + " - " + department.getName();
        }
        return new javax.swing.JComboBox<>(displays);
    }

    @SuppressWarnings("unchecked")
    private void selectDepartment(Long departmentId) {
        javax.swing.JComboBox<String> combo = (javax.swing.JComboBox<String>) departmentField.editor();
        if (departmentId != null) {
            for (int i = 0; i < departmentOptions.size(); i++) {
                if (departmentOptions.get(i).getId().equals(departmentId)) {
                    combo.setSelectedIndex(i);
                    return;
                }
            }
        }
        if (combo.getItemCount() > 0) {
            combo.setSelectedIndex(-1); // force an explicit choice
        }
    }

    private JPanel wrapLabeled(String label, javax.swing.JComponent editor) {
        JPanel wrapper = new JPanel(new MigLayout("wrap 1, insets 0, gap 3", "[grow,fill]", "[][]"));
        wrapper.setOpaque(false);
        JLabel fieldLabel = new JLabel(label);
        fieldLabel.setFont(fieldLabel.getFont().deriveFont(Font.PLAIN, 12f));
        fieldLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        wrapper.add(fieldLabel);
        wrapper.add(editor, "height 30!");
        return wrapper;
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new MigLayout("insets 14 28, gap 10, alignx right", "push[][]"));
        buttons.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Palette.color(Role.CARD_BORDER)));

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

    private void submit() {
        clearErrors();

        List<String> localErrors = new ArrayList<>();
        java.math.BigDecimal min = Validators.parseMoney(localErrors,
                minSalaryField.getText(), "Minimum salary");
        java.math.BigDecimal max = Validators.parseMoney(localErrors,
                maxSalaryField.getText(), "Maximum salary");

        int selectedDept = ((javax.swing.JComboBox<?>) departmentField.editor()).getSelectedIndex();
        if (selectedDept < 0) {
            localErrors.add("Department is required.");
        }

        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        position.setCode(codeField.getText());
        position.setName(nameField.getText());
        position.setDepartmentId(departmentOptions.get(selectedDept).getId());
        position.setDescription(descriptionField.getText());
        position.setMinSalary(min);
        position.setMaxSalary(max);
        if (!isNew && statusCombo != null) {
            position.setStatus(String.valueOf(statusCombo.getSelectedItem()));
        }

        saveButton.setEnabled(false);
        controller.save(position,
                id -> {
                    result = Result.SAVED;
                    dispose();
                },
                error -> {
                    saveButton.setEnabled(true);
                    if (error instanceof ValidationException ve) {
                        showError(String.join(" ", ve.getErrors()));
                    } else if (error instanceof HrmsException he) {
                        showError(he.getUserMessage());
                    } else {
                        ErrorHandler.handle(error);
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
    }

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    public Result showDialog() {
        setVisible(true);
        return result;
    }

    public Position position() {
        return position;
    }
}
