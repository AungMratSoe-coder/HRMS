package com.ams.hrms.ui.shift;

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
import com.ams.hrms.controller.ShiftController;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.Shift;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Assign-shift dialog: employee + shift pickers and an effective date.
 * Preselects when opened for a specific employee or shift.
 */
public class AssignmentDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final ShiftController controller =
            new ShiftController(ServiceRegistry.shiftService());

    private final List<Employee> employees = new ArrayList<>();
    private final List<Shift> activeShifts = new ArrayList<>();
    private final Long preselectedEmployeeId;
    private final Long preselectedShiftId;

    private FormField employeeField;
    private FormField shiftField;
    private FormField effectiveFromField;
    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Assign", "check");
    private Result result = Result.CANCELLED;

    public AssignmentDialog(java.awt.Window owner, List<Employee> allEmployees,
                            List<Shift> allShifts, Long employeeIdOverride, Long shiftIdOverride) {
        super(owner, "Assign Shift", ModalityType.APPLICATION_MODAL);
        for (Employee employee : allEmployees) {
            if ("ACTIVE".equals(employee.getStatus())) {
                employees.add(employee);
            }
        }
        this.preselectedEmployeeId = employeeIdOverride;
        this.preselectedShiftId = shiftIdOverride;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(440, 380);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10",
                "[grow,fill]"));

        JLabel titleLabel = new JLabel("Assign shift to employee");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        employeeField = FormField.custom("Employee *", true, buildEmployeeCombo());
        shiftField = FormField.custom("Shift *", true, buildShiftCombo());
        effectiveFromField = FormField.datePicker("Effective From *", true);
        effectiveFromField.setDate(LocalDate.now());

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(employeeField);
        form.add(shiftField);
        form.add(effectiveFromField);
        form.add(errorBanner);

        applyPreselection();
        return form;
    }

    private javax.swing.JComboBox<String> buildEmployeeCombo() {
        List<String> displays = new ArrayList<>();
        for (Employee employee : employees) {
            displays.add(employee.getCode() + " - " + employee.getFullName());
        }
        return new javax.swing.JComboBox<>(displays.toArray(new String[0]));
    }

    private javax.swing.JComboBox<String> buildShiftCombo() {
        List<String> displays = new ArrayList<>();
        for (Shift shift : activeShifts) {
            String label = shift.getCode() + " - " + shift.getName()
                    + " (" + ShiftPanel.timeRange(shift) + ")";
            displays.add(label);
        }
        return new javax.swing.JComboBox<>(displays.toArray(new String[0]));
    }

    @SuppressWarnings("unchecked")
    private void applyPreselection() {
        javax.swing.JComboBox<String> employeeCombo =
                (javax.swing.JComboBox<String>) employeeField.editor();
        if (preselectedEmployeeId != null) {
            for (int i = 0; i < employees.size(); i++) {
                if (employees.get(i).getId().equals(preselectedEmployeeId)) {
                    employeeCombo.setSelectedIndex(i);
                    break;
                }
            }
        } else if (employeeCombo.getItemCount() > 0) {
            employeeCombo.setSelectedIndex(-1);
        }

        javax.swing.JComboBox<String> shiftCombo =
                (javax.swing.JComboBox<String>) shiftField.editor();
        if (preselectedShiftId != null) {
            for (int i = 0; i < activeShifts.size(); i++) {
                if (activeShifts.get(i).getId().equals(preselectedShiftId)) {
                    shiftCombo.setSelectedIndex(i);
                    break;
                }
            }
        } else if (shiftCombo.getItemCount() > 0) {
            shiftCombo.setSelectedIndex(-1);
        }
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

        int employeeIndex = ((javax.swing.JComboBox<?>) employeeField.editor()).getSelectedIndex();
        int shiftIndex = ((javax.swing.JComboBox<?>) shiftField.editor()).getSelectedIndex();
        LocalDate from = effectiveFromField.getDate();

        if (employeeIndex < 0 || shiftIndex < 0 || from == null) {
            showError("Employee, shift and an effective date are required.");
            return;
        }

        saveButton.setEnabled(false);
        controller.assign(
                employees.get(employeeIndex).getId(),
                activeShifts.get(shiftIndex).getId(),
                from,
                () -> {
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

    // ------------------------------------------------------------------
    // API
    // ------------------------------------------------------------------

    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
