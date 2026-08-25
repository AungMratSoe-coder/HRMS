package com.ams.hrms.ui.assets;

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

import com.ams.hrms.component.DatePickerField;
import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.controller.AssetController;
import com.ams.hrms.model.AssetAssignment;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * Return-asset dialog (spec section 24): return date, condition on return
 * and notes; the service routes damaged assets into repair.
 */
public class ReturnDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final AssetController controller =
            new AssetController(com.ams.hrms.config.ServiceRegistry.assetService());

    private final AssetAssignment assignment;

    private DatePickerField returnedDatePicker;
    private FormField conditionField;
    private FormField notesField;

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Confirm Return", "check");
    private Result result = Result.CANCELLED;

    public ReturnDialog(java.awt.Window owner, AssetAssignment assignment) {
        super(owner, "Return Asset", ModalityType.APPLICATION_MODAL);
        this.assignment = assignment;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(440, 420);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel(assignment.getAssetCode() + " - "
                + assignment.getAssetName());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        JLabel holderLabel = new JLabel("From: " + assignment.getEmployeeCode()
                + " - " + assignment.getEmployeeName() + " (assigned "
                + assignment.getAssignedDate() + ")");
        holderLabel.setFont(holderLabel.getFont().deriveFont(Font.PLAIN, 11f));
        holderLabel.setForeground(Palette.color(Role.TEXT_MUTED));

        returnedDatePicker = new DatePickerField();
        FormField dateWrapper = FormField.custom("Returned Date", true, returnedDatePicker);

        conditionField = FormField.comboBox("Condition on Return",
                new String[]{"GOOD", "FAIR", "POOR", "DAMAGED"}, true);
        notesField = FormField.textArea("Notes", false);

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel);
        form.add(holderLabel);
        form.add(dateWrapper);
        form.add(conditionField);
        form.add(notesField, "height 70!");
        form.add(errorBanner);
        return form;
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

    private void submit() {
        errorBanner.setVisible(false);

        List<String> localErrors = new ArrayList<>();
        LocalDate returnedDate = returnedDatePicker.getDate();
        if (returnedDate == null) {
            localErrors.add("Returned date is required.");
        }
        Validators.maxLength(localErrors, notesField.getText(), 500, "Notes");
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        saveButton.setEnabled(false);
        controller.returnAsset(assignment.getId(), returnedDate,
                conditionField.getText(), notesField.getText(),
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
