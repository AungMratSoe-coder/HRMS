package com.ams.hrms.ui.training;

import java.awt.BorderLayout;
import java.awt.Font;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.controller.TrainingController;
import com.ams.hrms.model.EmployeeTraining;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * Records or updates a training result (spec section 23): outcome, optional
 * 0-100 score and notes; terminal outcomes lock the record afterwards.
 */
public class TrainingResultDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final TrainingController controller =
            new TrainingController(com.ams.hrms.config.ServiceRegistry.trainingService());

    private final EmployeeTraining enrollment;

    private FormField resultField;
    private FormField scoreField;
    private FormField notesField;

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save Result", "check");
    private Result result = Result.CANCELLED;

    public TrainingResultDialog(java.awt.Window owner, EmployeeTraining enrollment) {
        super(owner, "Training Result", ModalityType.APPLICATION_MODAL);
        this.enrollment = enrollment;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(440, 460);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
        populate();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel(enrollment.getEmployeeName() + " - "
                + enrollment.getProgramName() + (enrollment.isDecided()
                        ? " (" + enrollment.getResult() + ", locked)" : ""));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        List<String> outcomes = new ArrayList<>(com.ams.hrms.service.TrainingRules
                .ENROLLMENT_RESULTS);
        java.util.Collections.sort(outcomes);
        resultField = FormField.comboBox("Outcome", outcomes.toArray(new String[0]), true);
        scoreField = FormField.textField("Score (0-100)", false);
        notesField = FormField.textArea("Notes", false);

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(resultField);
        form.add(scoreField);
        form.add(notesField, "height 70!");
        form.add(errorBanner);
        return form;
    }

    private void populate() {
        resultField.setText(enrollment.getResult());
        if (enrollment.getScore() != null) {
            scoreField.setText(enrollment.getScore().toPlainString());
        }
        if (enrollment.getNotes() != null) {
            notesField.setText(enrollment.getNotes());
        }
        boolean locked = enrollment.isDecided();
        resultField.editor().setEnabled(!locked);
        scoreField.editor().setEnabled(!locked);
        notesField.editor().setEnabled(!locked);
        saveButton.setEnabled(!locked);
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new MigLayout("insets 14 28, gap 10, alignx right", "push[][]"));
        buttons.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                Palette.color(Role.CARD_BORDER)));

        JButton cancel = new ModernButton("Close", ModernButton.Variant.OUTLINE);
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
        BigDecimal score = Validators.parseMoney(localErrors, scoreField.getText(), "Score");
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        saveButton.setEnabled(false);
        controller.recordResult(
                enrollment.getId(),
                resultField.getText(),
                score == null ? null : score,
                null,
                notesField.getText(),
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
