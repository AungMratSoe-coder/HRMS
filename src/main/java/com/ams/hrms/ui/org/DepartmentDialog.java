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
import com.ams.hrms.controller.DepartmentController;
import com.ams.hrms.exception.ErrorHandler;
import com.ams.hrms.exception.HrmsException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Department;
import com.ams.hrms.repository.EmployeeRepository;
import com.ams.hrms.service.DepartmentService;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Create/Edit dialog for departments (spec section 12). Validation problems
 * appear in an inline banner; saving runs off the EDT and closes the dialog
 * only on success.
 */
public class DepartmentDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final DepartmentController controller =
            new DepartmentController(ServiceRegistry.departmentService());

    private final Department department;
    private final boolean isNew;
    private final List<EmployeeRepository.EmployeeOption> managerOptions = new ArrayList<>();

    private FormField codeField;
    private FormField nameField;
    private FormField descriptionField;
    private FormField managerField;
    private javax.swing.JComboBox<String> statusCombo;
    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    public DepartmentDialog(java.awt.Window owner, Department existing,
                            List<EmployeeRepository.EmployeeOption> managers) {
        super(owner, existing == null ? "New Department" : "Edit Department",
                ModalityType.APPLICATION_MODAL);
        this.department = existing == null ? new Department() : existing;
        this.isNew = existing == null;
        managerOptions.addAll(managers);

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(470, 600);
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
                "[][][][][][grow][]"));

        JLabel titleLabel = new JLabel(isNew ? "Create department" : "Edit department");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        codeField = FormField.textField("Code *", true);
        nameField = FormField.textField("Department Name *", true);
        descriptionField = FormField.textArea("Description", false);
        managerField = buildManagerField();

        if (!isNew) {
            codeField.setText(department.getCode());
            nameField.setText(department.getName());
            descriptionField.setText(department.getDescription());
            selectManager(department.getManagerId());

            statusCombo = new javax.swing.JComboBox<>(new String[]{"ACTIVE", "INACTIVE"});
            statusCombo.setSelectedItem(department.getStatus());
        } else {
            selectManager(null);
        }

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVerticalTextPosition(javax.swing.SwingConstants.TOP);
        errorBanner.setVisible(false);

        form.add(titleLabel);
        form.add(codeField);
        form.add(nameField);
        form.add(descriptionField, "height 84!");
        form.add(managerField);
        if (!isNew) {
            form.add(wrapLabeled("Status", statusCombo));
        }
        form.add(errorBanner);
        return form;
    }

    /** Manager picker: none + all active employees. */
    private FormField buildManagerField() {
        String[] displays = new String[managerOptions.size() + 1];
        displays[0] = "- None -";
        for (int i = 0; i < managerOptions.size(); i++) {
            displays[i + 1] = managerOptions.get(i).display();
        }
        javax.swing.JComboBox<String> combo = new javax.swing.JComboBox<>(displays);
        return FormField.custom("Manager", false, combo);
    }

    private void selectManager(Long managerId) {
        if (managerId == null) {
            ((javax.swing.JComboBox<?>) managerField.editor()).setSelectedIndex(0);
            return;
        }
        for (int i = 0; i < managerOptions.size(); i++) {
            if (managerOptions.get(i).id() == managerId) {
                ((javax.swing.JComboBox<?>) managerField.editor()).setSelectedIndex(i + 1);
                return;
            }
        }
        ((javax.swing.JComboBox<?>) managerField.editor()).setSelectedIndex(0);
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

        department.setCode(codeField.getText());
        department.setName(nameField.getText());
        department.setDescription(descriptionField.getText());
        int selectedManager = ((javax.swing.JComboBox<?>) managerField.editor()).getSelectedIndex();
        department.setManagerId(selectedManager <= 0 ? null
                : managerOptions.get(selectedManager - 1).id());
        if (!isNew && statusCombo != null) {
            department.setStatus(String.valueOf(statusCombo.getSelectedItem()));
        }

        saveButton.setEnabled(false);
        controller.save(department,
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

    public Department department() {
        return department;
    }
}
