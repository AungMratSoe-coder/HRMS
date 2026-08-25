package com.ams.hrms.ui.leave;

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
import com.ams.hrms.controller.LeaveController;
import com.ams.hrms.model.EmployeeLeaveRequest;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * New leave request dialog (spec section 18): employee, type, date range and
 * reason; shows the live available balance for the chosen type/year.
 */
public class LeaveRequestDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final LeaveController controller =
            new LeaveController(com.ams.hrms.config.ServiceRegistry.leaveService());

    private final List<com.ams.hrms.model.Employee> employees = new ArrayList<>();
    private final List<com.ams.hrms.repository.LeaveRepository.LeaveTypeOption> types = new ArrayList<>();

    private FormField employeeField;
    private FormField typeField;
    private FormField startDateField;
    private FormField endDateField;
    private FormField reasonField;
    private JLabel balanceLabel;
    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Submit Request", "check");
    private Result result = Result.CANCELLED;

    public LeaveRequestDialog(java.awt.Window owner,
                              List<com.ams.hrms.model.Employee> employees,
                              List<com.ams.hrms.repository.LeaveRepository.LeaveTypeOption> types) {
        super(owner, "New Leave Request", ModalityType.APPLICATION_MODAL);
        this.employees.addAll(employees);
        this.types.addAll(types);

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(460, 540);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel("Request leave");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        employeeField = FormField.custom("Employee *", true, buildEmployeeCombo());
        typeField = FormField.custom("Leave Type *", true, buildTypeCombo());
        startDateField = FormField.datePicker("Start Date *", true);
        endDateField = FormField.datePicker("End Date *", true);
        reasonField = FormField.textArea("Reason *", true);

        balanceLabel = new JLabel(" ");
        balanceLabel.setFont(balanceLabel.getFont().deriveFont(Font.PLAIN, 11f));
        balanceLabel.setForeground(Palette.color(Role.TEXT_MUTED));

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(employeeField);
        form.add(typeField);
        form.add(startDateField);
        form.add(endDateField);
        form.add(reasonField, "height 70!");
        form.add(balanceLabel);
        form.add(errorBanner);

        wireLiveBalance();
        return form;
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

    private javax.swing.JComboBox<String> buildTypeCombo() {
        List<String> displays = new ArrayList<>();
        for (var type : types) {
            displays.add(type.name());
        }
        javax.swing.JComboBox<String> combo =
                new javax.swing.JComboBox<>(displays.toArray(new String[0]));
        if (combo.getItemCount() > 0) {
            combo.setSelectedIndex(-1);
        }
        return combo;
    }

    /** Refreshes the "available days" line when type/dates change. */
    @SuppressWarnings("unchecked")
    private void wireLiveBalance() {
        Runnable refreshBalance = () -> {
            int employeeIndex = ((javax.swing.JComboBox<String>) employeeField.editor())
                    .getSelectedIndex();
            int typeIndex = ((javax.swing.JComboBox<String>) typeField.editor())
                    .getSelectedIndex();
            LocalDate start = startDateField.getDate();
            if (employeeIndex < 0 || typeIndex < 0 || start == null) {
                balanceLabel.setText(" ");
                return;
            }
            long employeeId = employees.get(employeeIndex).getId();
            long typeId = types.get(typeIndex).id();
            controller.availableDays(employeeId, typeId, start.getYear(),
                    available -> balanceLabel.setText(
                            "Available: " + available.toPlainString() + " day(s)"));
        };
        ((javax.swing.JComboBox<String>) employeeField.editor())
                .addActionListener(event -> refreshBalance.run());
        ((javax.swing.JComboBox<String>) typeField.editor())
                .addActionListener(event -> refreshBalance.run());
        ((com.ams.hrms.component.DatePickerField) startDateField.editor())
                .addDateChangedListener(event -> refreshBalance.run());
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

    @SuppressWarnings("unchecked")
    private void submit() {
        clearErrors();

        int employeeIndex = ((javax.swing.JComboBox<String>) employeeField.editor())
                .getSelectedIndex();
        int typeIndex = ((javax.swing.JComboBox<String>) typeField.editor()).getSelectedIndex();
        LocalDate start = startDateField.getDate();
        LocalDate end = endDateField.getDate();

        List<String> localErrors = new ArrayList<>();
        if (employeeIndex < 0) {
            localErrors.add("Employee is required.");
        }
        if (typeIndex < 0) {
            localErrors.add("Leave type is required.");
        }
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        EmployeeLeaveRequest request = new EmployeeLeaveRequest();
        request.setEmployeeId(employees.get(employeeIndex).getId());
        request.setLeaveTypeId(types.get(typeIndex).id());
        request.setStartDate(start);
        request.setEndDate(end);
        request.setReason(reasonField.getText());

        saveButton.setEnabled(false);
        controller.submit(request,
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

    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
