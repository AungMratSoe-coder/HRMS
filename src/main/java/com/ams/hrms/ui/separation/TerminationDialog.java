package com.ams.hrms.ui.separation;

import java.awt.BorderLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ams.hrms.component.DatePickerField;
import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.controller.SeparationController;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.Termination;
import com.ams.hrms.service.SeparationRules;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Termination dialog (spec section 26): employee, date, category, rehire
 * eligibility, reason and notes. Recording runs the exit checklist
 * immediately - the action is confirmed twice by design.
 */
public class TerminationDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final SeparationController controller =
            new SeparationController(com.ams.hrms.config.ServiceRegistry.separationService());

    private final List<Employee> employees = new ArrayList<>();

    private FormField employeeField;
    private DatePickerField terminationDatePicker;
    private FormField categoryField;
    private FormField reasonField;
    private FormField notesField;
    private JCheckBox eligibleRehireBox;

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Record Termination", "warning");
    private Result result = Result.CANCELLED;

    public TerminationDialog(java.awt.Window owner, List<Employee> activeEmployees) {
        super(owner, "New Termination", ModalityType.APPLICATION_MODAL);
        this.employees.addAll(activeEmployees);

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(470, 600);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
        populate();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel("Terminate an employee (effective immediately)");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setForeground(Palette.color(Role.DANGER));

        List<String> displays = new ArrayList<>();
        for (var employee : employees) {
            displays.add(employee.getCode() + " - " + employee.getFullName());
        }
        employeeField = FormField.custom("Employee", true,
                new javax.swing.JComboBox<>(displays.toArray(new String[0])));

        terminationDatePicker = new DatePickerField();
        FormField dateWrapper =
                FormField.custom("Termination Date", true, terminationDatePicker);

        List<String> categories = new ArrayList<>(SeparationRules.TERMINATION_CATEGORIES);
        java.util.Collections.sort(categories);
        categoryField = FormField.comboBox("Reason Category",
                categories.toArray(new String[0]), true);

        reasonField = FormField.textArea("Reason", false);
        notesField = FormField.textArea("Notes", false);
        eligibleRehireBox = new JCheckBox("Eligible for rehire");

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(employeeField);
        form.add(dateWrapper);
        form.add(categoryField);
        form.add(reasonField, "height 70!");
        form.add(notesField, "height 60!");
        form.add(eligibleRehireBox);
        form.add(errorBanner);
        return form;
    }

    private void populate() {
        terminationDatePicker.setDate(LocalDate.now());
        categoryField.setText("OTHER");
        eligibleRehireBox.setSelected(true);
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
        errorBanner.setVisible(false);

        var combo = (javax.swing.JComboBox<String>) employeeField.editor();
        if (combo.getSelectedIndex() < 0) {
            showError("Employee is required.");
            return;
        }
        if (terminationDatePicker.getDate() == null) {
            showError("Termination date is required.");
            return;
        }

        Termination termination = new Termination();
        termination.setEmployeeId(employees.get(combo.getSelectedIndex()).getId());
        termination.setTerminationDate(terminationDatePicker.getDate());
        termination.setReasonCategory(categoryField.getText());
        termination.setReason(reasonField.getText());
        termination.setNotes(notesField.getText());
        termination.setEligibleRehire(eligibleRehireBox.isSelected());

        saveButton.setEnabled(false);
        controller.terminate(termination,
                summary -> {
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
