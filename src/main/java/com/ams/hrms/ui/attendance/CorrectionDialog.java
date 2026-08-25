package com.ams.hrms.ui.attendance;

import java.awt.BorderLayout;
import java.awt.Font;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.controller.AttendanceController;
import com.ams.hrms.exception.ErrorHandler;
import com.ams.hrms.exception.HrmsException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.AttendanceRecord;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * HR correction dialog (spec section 16): edit both punch times, recompute
 * everything server-side; a reason is mandatory and audited.
 */
public class CorrectionDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final AttendanceController controller;
    private final AttendanceRecord record;

    private FormField checkInField;
    private FormField checkOutField;
    private FormField reasonField;
    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Apply Correction", "check");
    private Result result = Result.CANCELLED;

    public CorrectionDialog(java.awt.Window owner, AttendanceController controller,
                            AttendanceRecord record) {
        super(owner, "Correct Attendance - " + record.getEmployeeCode(), ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.record = record;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(440, 440);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel("Correction for "
                + record.getAttendanceDate());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        checkInField = FormField.textField("Check In (HH:mm) *", true);
        checkInField.setText(timeText(record.getCheckIn()));
        checkOutField = FormField.textField("Check Out (HH:mm) *", true);
        checkOutField.setText(timeText(record.getCheckOut()));
        reasonField = FormField.textArea("Reason *", true);

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(checkInField);
        form.add(checkOutField);
        form.add(reasonField, "height 70!");
        form.add(errorBanner);
        return form;
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
        List<String> localErrors = new ArrayList<>();

        LocalTime in = Validators.parseTime(localErrors, checkInField.getText(), "Check in");
        LocalTime out = Validators.parseTime(localErrors, checkOutField.getText(), "Check out");
        String reason = reasonField.getText();
        if (reason == null || reason.isBlank()) {
            localErrors.add("A correction reason is required.");
        }
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        saveButton.setEnabled(false);
        controller.correct(record.getId(), in, out, reason.trim(),
                () -> {
                    result = Result.SAVED;
                    dispose();
                },
                error -> {
                    saveButton.setEnabled(true);
                    if (error instanceof HrmsException he) {
                        showError(he.getUserMessage());
                        if (error instanceof ValidationException ve) {
                            showError(String.join(" ", ve.getErrors()));
                        }
                    } else {
                        ErrorHandler.handle(error);
                    }
                });
    }

    private static String timeText(LocalTime time) {
        return time == null ? "" : time.toString().length() == 8
                ? time.toString().substring(0, 5) : time.toString();
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
