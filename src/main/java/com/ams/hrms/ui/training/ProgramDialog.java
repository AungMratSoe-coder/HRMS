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
import com.ams.hrms.model.TrainingProgram;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * Create/edit training program dialog (spec section 23): name, trainer,
 * optional cost and capacity, description.
 */
public class ProgramDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final TrainingController controller =
            new TrainingController(com.ams.hrms.config.ServiceRegistry.trainingService());

    private final TrainingProgram existing;

    private FormField nameField;
    private FormField trainerField;
    private FormField costField;
    private FormField capacityField;
    private FormField descriptionField;

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    public ProgramDialog(java.awt.Window owner, TrainingProgram existing) {
        super(owner, existing == null ? "New Training Program" : "Edit Training Program",
                ModalityType.APPLICATION_MODAL);
        this.existing = existing;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(460, 520);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
        populate();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 2, insets 24 28 12 28, gap 10", "[grow,fill][grow,fill]"));

        JLabel titleLabel = new JLabel(existing == null
                ? "Plan a training program" : "Edit " + existing.getCode());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        nameField = FormField.textField("Program Name", true);
        trainerField = FormField.textField("Trainer", false);
        costField = FormField.textField("Cost", false);
        capacityField = FormField.textField("Capacity (blank = unlimited)", false);
        descriptionField = FormField.textArea("Description", false);

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "span 2, wrap unrelated");
        form.add(nameField, "span 2");
        form.add(trainerField, "span 2");
        form.add(costField);
        form.add(capacityField);
        form.add(descriptionField, "span 2");
        form.add(errorBanner, "span 2");
        return form;
    }

    private void populate() {
        if (existing == null) {
            return;
        }
        nameField.setText(existing.getName());
        trainerField.setText(existing.getTrainerName() == null
                ? "" : existing.getTrainerName());
        costField.setText(existing.getCost() == null ? "" : existing.getCost().toPlainString());
        capacityField.setText(existing.getCapacity() == null
                ? "" : String.valueOf(existing.getCapacity()));
        descriptionField.setText(existing.getDescription());
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
        BigDecimal cost = Validators.parseMoney(localErrors, costField.getText(), "Cost");
        Integer capacity = null;
        if (!capacityField.getText().isBlank()) {
            try {
                capacity = Integer.parseInt(capacityField.getText().trim());
            } catch (NumberFormatException e) {
                localErrors.add("Capacity must be a whole number.");
            }
        }
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        TrainingProgram program = existing == null ? new TrainingProgram() : existing;
        program.setName(nameField.getText());
        program.setTrainerName(trainerField.getText());
        program.setCost(cost);
        program.setCapacity(capacity);
        program.setDescription(descriptionField.getText());

        saveButton.setEnabled(false);
        controller.saveProgram(program,
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
