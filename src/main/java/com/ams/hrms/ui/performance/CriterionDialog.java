package com.ams.hrms.ui.performance;

import java.awt.BorderLayout;
import java.awt.Font;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.controller.PerformanceController;
import com.ams.hrms.model.PerformanceCriterion;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * Create/edit performance criterion (spec section 22). Weights are advisory
 * percentages; the panel shows a warning when active weights drift from 100.
 */
public class CriterionDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final PerformanceController controller =
            new PerformanceController(com.ams.hrms.config.ServiceRegistry.performanceService());

    private final PerformanceCriterion existing;

    private FormField codeField;
    private FormField nameField;
    private FormField weightField;
    private FormField descriptionField;
    private JCheckBox activeCheckBox;

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    public CriterionDialog(java.awt.Window owner, PerformanceCriterion existing) {
        super(owner, existing == null ? "New Criterion" : "Edit Criterion",
                ModalityType.APPLICATION_MODAL);
        this.existing = existing;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(440, 440);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
        populate();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel(existing == null
                ? "New review criterion" : "Edit criterion");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        codeField = FormField.textField("Criteria Code", true);
        nameField = FormField.textField("Criteria Name", true);
        weightField = FormField.textField("Weight %", true);
        descriptionField = FormField.textArea("Description", false);
        activeCheckBox = new JCheckBox("Active");

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(codeField);
        form.add(nameField);
        form.add(weightField);
        form.add(descriptionField, "height 70!");
        form.add(activeCheckBox);
        form.add(errorBanner);
        return form;
    }

    private void populate() {
        if (existing == null) {
            weightField.setText("10");
            activeCheckBox.setSelected(true);
            return;
        }
        codeField.setText(existing.getCode());
        codeField.editor().setEnabled(false);
        nameField.setText(existing.getName());
        weightField.setText(existing.getWeight() == null
                ? "" : existing.getWeight().toPlainString());
        descriptionField.setText(existing.getDescription());
        activeCheckBox.setSelected(existing.isActive());
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
        BigDecimal weight = Validators.parseMoney(localErrors,
                weightField.getText(), "Weight");
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        PerformanceCriterion criterion = existing == null
                ? new PerformanceCriterion() : existing;
        criterion.setCode(codeField.getText().toUpperCase());
        criterion.setName(nameField.getText());
        criterion.setWeight(weight);
        criterion.setDescription(descriptionField.getText());
        criterion.setActive(existing == null || activeCheckBox.isSelected());

        saveButton.setEnabled(false);
        controller.saveCriterion(criterion,
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
