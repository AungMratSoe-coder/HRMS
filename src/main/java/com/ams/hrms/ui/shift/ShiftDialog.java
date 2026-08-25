package com.ams.hrms.ui.shift;

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
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.ShiftController;
import com.ams.hrms.exception.ErrorHandler;
import com.ams.hrms.exception.HrmsException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Shift;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * Create/Edit dialog for shift definitions (spec section 17). Overnight
 * times (end before start) are valid and marked in the list.
 */
public class ShiftDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final ShiftController controller =
            new ShiftController(ServiceRegistry.shiftService());

    private final Shift shift;
    private final boolean isNew;

    private FormField codeField;
    private FormField nameField;
    private FormField startTimeField;
    private FormField endTimeField;
    private FormField graceField;
    private FormField breakField;
    private FormField descriptionField;
    private javax.swing.JComboBox<String> statusCombo;
    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    public ShiftDialog(java.awt.Window owner, Shift existing) {
        super(owner, existing == null ? "New Shift" : "Edit Shift",
                ModalityType.APPLICATION_MODAL);
        this.shift = existing == null ? new Shift() : existing;
        this.isNew = existing == null;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(460, 580);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 2, insets 24 28 12 28, gapx 14, gapy 10",
                "[grow,fill][grow,fill]",
                ""));

        JLabel titleLabel = new JLabel(isNew ? "Create shift" : "Edit shift");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));
        form.add(titleLabel, "span 2, wrap unrelated");

        codeField = FormField.textField("Code *", true);
        nameField = FormField.textField("Shift Name *", true);
        form.add(codeField);
        form.add(nameField);

        startTimeField = FormField.textField("Start Time (HH:mm) *", true);
        endTimeField = FormField.textField("End Time (HH:mm) *", true);
        form.add(startTimeField);
        form.add(endTimeField);

        graceField = FormField.textField("Grace Minutes", false);
        breakField = FormField.textField("Break Minutes", false);
        form.add(graceField);
        form.add(breakField);

        descriptionField = FormField.textArea("Description", false);
        form.add(descriptionField, "span 2, height 64!");

        if (!isNew) {
            statusCombo = new javax.swing.JComboBox<>(new String[]{"ACTIVE", "INACTIVE"});
            statusCombo.setSelectedItem(shift.getStatus());
            JPanel statusWrapper = new JPanel(new MigLayout("wrap 1, insets 0, gap 3"));
            statusWrapper.setOpaque(false);
            JLabel statusLabel = new JLabel("Status");
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
            statusLabel.setForeground(Palette.color(Role.TEXT_MUTED));
            statusWrapper.add(statusLabel);
            statusWrapper.add(statusCombo, "height 30!, width 160!");
            form.add(statusWrapper, "span 2");
        }

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVerticalTextPosition(javax.swing.SwingConstants.TOP);
        errorBanner.setVisible(false);
        form.add(errorBanner, "span 2");

        if (!isNew) {
            codeField.setText(shift.getCode());
            nameField.setText(shift.getName());
            startTimeField.setText(timeString(shift.getStartTime()));
            endTimeField.setText(timeString(shift.getEndTime()));
            graceField.setText(String.valueOf(shift.getGraceMinutes()));
            breakField.setText(String.valueOf(shift.getBreakMinutes()));
            descriptionField.setText(shift.getDescription());
        } else {
            graceField.setText("10");
            breakField.setText("60");
        }
        return form;
    }

    private static String timeString(LocalTime time) {
        return time == null ? "" : time.toString();
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

        LocalTime start = Validators.parseTime(localErrors,
                startTimeField.getText(), "Start time");
        LocalTime end = Validators.parseTime(localErrors,
                endTimeField.getText(), "End time");
        int grace = parseInt(localErrors, graceField.getText(), "Grace minutes", 10);
        int breakMinutes = parseInt(localErrors, breakField.getText(), "Break minutes", 0);
        if (start != null && end != null && start.equals(end)) {
            localErrors.add("Start and end times cannot be identical.");
        }
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        shift.setCode(codeField.getText());
        shift.setName(nameField.getText());
        shift.setStartTime(start);
        shift.setEndTime(end);
        shift.setGraceMinutes(grace);
        shift.setBreakMinutes(breakMinutes);
        shift.setDescription(descriptionField.getText());
        if (!isNew && statusCombo != null) {
            shift.setStatus(String.valueOf(statusCombo.getSelectedItem()));
        }

        saveButton.setEnabled(false);
        controller.saveShift(shift,
                id -> {
                    shift.setId(id);
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

    /** Parses a non-negative integer with a default when blank. */
    private static int parseInt(List<String> errors, String raw, String label, int fallback) {
        String normalized = Validators.normalize(raw);
        if (normalized.isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException e) {
            errors.add(label + " must be a whole number.");
            return fallback;
        }
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

    /** Shows the dialog modally; returns SAVED only after a successful save. */
    public Result showDialog() {
        setVisible(true);
        return result;
    }
}
