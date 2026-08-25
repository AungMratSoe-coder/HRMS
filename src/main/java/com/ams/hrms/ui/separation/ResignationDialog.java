package com.ams.hrms.ui.separation;

import java.awt.BorderLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ams.hrms.component.DatePickerField;
import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.controller.SeparationController;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.Resignation;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * New resignation dialog (spec section 26): employee, dates with a live
 * notice-period calculation and the reason.
 */
public class ResignationDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final SeparationController controller =
            new SeparationController(com.ams.hrms.config.ServiceRegistry.separationService());

    private final List<Employee> employees = new ArrayList<>();

    private FormField employeeField;
    private DatePickerField resignationDatePicker;
    private DatePickerField lastWorkingPicker;
    private FormField reasonField;

    private final JLabel noticeLabel = new JLabel(" ");
    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Submit Resignation", "check");
    private Result result = Result.CANCELLED;

    public ResignationDialog(java.awt.Window owner, List<Employee> activeEmployees) {
        super(owner, "New Resignation", ModalityType.APPLICATION_MODAL);
        this.employees.addAll(activeEmployees);

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(470, 540);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
        wireLiveNotice();
        populate();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel("Record an employee resignation");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        List<String> displays = new ArrayList<>();
        for (var employee : employees) {
            displays.add(employee.getCode() + " - " + employee.getFullName());
        }
        employeeField = FormField.custom("Employee", true,
                new javax.swing.JComboBox<>(displays.toArray(new String[0])));

        resignationDatePicker = new DatePickerField();
        FormField resignationWrapper =
                FormField.custom("Resignation Date", true, resignationDatePicker);

        lastWorkingPicker = new DatePickerField();
        FormField lastWorkingWrapper =
                FormField.custom("Last Working Date", true, lastWorkingPicker);

        noticeLabel.setFont(noticeLabel.getFont().deriveFont(Font.PLAIN, 11f));
        noticeLabel.setForeground(Palette.color(Role.TEXT_MUTED));

        reasonField = FormField.textArea("Reason", false);

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(employeeField);
        form.add(resignationWrapper);
        form.add(lastWorkingWrapper);
        form.add(noticeLabel);
        form.add(reasonField, "height 80!");
        form.add(errorBanner);
        return form;
    }

    /** Live notice-day count as the dates change. */
    private void wireLiveNotice() {
        Runnable refresh = () -> {
            LocalDate start = resignationDatePicker.getDate();
            LocalDate end = lastWorkingPicker.getDate();
            if (start == null || end == null) {
                noticeLabel.setText(" ");
                return;
            }
            long days = ChronoUnit.DAYS.between(start, end);
            noticeLabel.setText(days < 0
                    ? "Last working date cannot be before the resignation date."
                    : "Notice period: " + days + " day(s)");
        };
        resignationDatePicker.addDateChangedListener(event -> refresh.run());
        lastWorkingPicker.addDateChangedListener(event -> refresh.run());
    }

    private void populate() {
        LocalDate today = LocalDate.now();
        resignationDatePicker.setDate(today);
        lastWorkingPicker.setDate(today.plusDays(30));
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
        if (resignationDatePicker.getDate() == null || lastWorkingPicker.getDate() == null) {
            showError("Resignation date and last working date are required.");
            return;
        }

        Resignation resignation = new Resignation();
        resignation.setEmployeeId(employees.get(combo.getSelectedIndex()).getId());
        resignation.setResignationDate(resignationDatePicker.getDate());
        resignation.setLastWorkingDate(lastWorkingPicker.getDate());
        resignation.setReason(reasonField.getText());

        saveButton.setEnabled(false);
        controller.recordResignation(resignation,
                id -> {
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
