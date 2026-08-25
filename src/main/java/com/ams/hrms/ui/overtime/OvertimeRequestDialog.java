package com.ams.hrms.ui.overtime;

import java.awt.BorderLayout;
import java.awt.Font;
import java.time.LocalDate;
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
import com.ams.hrms.controller.OvertimeController;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.OvertimeRequest;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/** New overtime request dialog (spec section 19). */
public class OvertimeRequestDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final OvertimeController controller =
            new OvertimeController(ServiceRegistry.overtimeService());

    private final List<Employee> employees = new ArrayList<>();

    private FormField employeeField;
    private FormField dateField;
    private FormField hoursField;
    private FormField reasonField;
    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Submit Request", "check");
    private Result result = Result.CANCELLED;

    public OvertimeRequestDialog(java.awt.Window owner, List<Employee> employees) {
        super(owner, "New Overtime Request", ModalityType.APPLICATION_MODAL);
        for (var employee : employees) {
            if ("ACTIVE".equals(employee.getStatus())) {
                this.employees.add(employee);
            }
        }

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(440, 460);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel("Request overtime");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        employeeField = FormField.custom("Employee *", true, buildEmployeeCombo());
        dateField = FormField.datePicker("Overtime Date *", true);
        dateField.setDate(LocalDate.now());
        hoursField = FormField.textField("Hours * (0.01 - 12)", true);
        reasonField = FormField.textArea("Reason *", true);

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(employeeField);
        form.add(dateField);
        form.add(hoursField);
        form.add(reasonField, "height 70!");
        form.add(errorBanner);
        return form;
    }

    private javax.swing.JComboBox<String> buildEmployeeCombo() {
        List<String> displays = new ArrayList<>();
        for (Employee employee : employees) {
            displays.add(employee.getCode() + " - " + employee.getFullName());
        }
        javax.swing.JComboBox<String> combo =
                new javax.swing.JComboBox<>(displays.toArray(new String[0]));
        if (combo.getItemCount() > 0) {
            combo.setSelectedIndex(-1);
        }
        return combo;
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

    private void submit() {
        clearErrors();

        int employeeIndex = ((javax.swing.JComboBox<?>) employeeField.editor()).getSelectedIndex();
        LocalDate date = dateField.getDate();
        if (employeeIndex < 0 || date == null) {
            showError("Employee and date are required.");
            return;
        }
        java.math.BigDecimal hours;
        try {
            hours = new java.math.BigDecimal(
                    com.ams.hrms.validator.Validators.normalize(hoursField.getText()));
            if (hours.signum() <= 0 || hours.compareTo(java.math.BigDecimal.valueOf(12)) > 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            showError("Hours must be a number between 0.01 and 12.");
            return;
        }
        String reason = reasonField.getText();
        if (reason == null || reason.isBlank()) {
            showError("A reason is required.");
            return;
        }

        OvertimeRequest request = new OvertimeRequest();
        request.setEmployeeId(employees.get(employeeIndex).getId());
        request.setRequestDate(date);
        request.setHours(hours);
        request.setReason(reason.trim());

        saveButton.setEnabled(false);
        controller.submit(request,
                id -> {
                    result = Result.SAVED;
                    dispose();
                },
                error -> {
                    saveButton.setEnabled(true);
                    if (error instanceof com.ams.hrms.exception.HrmsException he) {
                        showError(he.getUserMessage());
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
    }

    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
