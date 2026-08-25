package com.ams.hrms.ui.onboarding;

import java.awt.BorderLayout;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;

import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.controller.OnboardingController;
import com.ams.hrms.model.OnboardingTemplate;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Create/edit onboarding checklist template (spec section 15). Changes feed
 * future hires only; existing checklists are never rewritten.
 */
public class TemplateDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final OnboardingController controller =
            new OnboardingController(com.ams.hrms.config.ServiceRegistry.onboardingService());

    private final OnboardingTemplate existing;

    private FormField nameField;
    private FormField descriptionField;
    private JSpinner orderSpinner;
    private JCheckBox mandatoryCheckBox;
    private JCheckBox activeCheckBox;

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    public TemplateDialog(java.awt.Window owner, OnboardingTemplate existing) {
        super(owner, existing == null ? "New Template" : "Edit Template",
                ModalityType.APPLICATION_MODAL);
        this.existing = existing;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(440, 420);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
        populate();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel(existing == null
                ? "New checklist template" : "Edit template");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        nameField = FormField.textField("Task Name", true);
        descriptionField = FormField.textArea("Description", false);

        orderSpinner = new JSpinner(new javax.swing.SpinnerNumberModel(1, 1, 999, 1));
        FormField orderField = FormField.custom("Order", true, orderSpinner);

        mandatoryCheckBox = new JCheckBox("Mandatory task");
        activeCheckBox = new JCheckBox("Active");

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(nameField);
        form.add(descriptionField, "height 70!");
        form.add(orderField);
        form.add(mandatoryCheckBox);
        form.add(activeCheckBox);
        form.add(errorBanner);
        return form;
    }

    private void populate() {
        if (existing == null) {
            mandatoryCheckBox.setSelected(true);
            activeCheckBox.setSelected(true);
            return;
        }
        nameField.setText(existing.getTaskName());
        descriptionField.setText(existing.getDescription());
        orderSpinner.setValue(existing.getTaskOrder());
        mandatoryCheckBox.setSelected(existing.isMandatory());
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

        OnboardingTemplate template = existing == null ? new OnboardingTemplate() : existing;
        template.setTaskName(nameField.getText());
        template.setDescription(descriptionField.getText());
        template.setTaskOrder((Integer) orderSpinner.getValue());
        template.setMandatory(mandatoryCheckBox.isSelected());
        template.setActive(existing == null || activeCheckBox.isSelected());

        saveButton.setEnabled(false);
        controller.saveTemplate(template,
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
