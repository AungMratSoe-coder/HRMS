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
import com.ams.hrms.model.Asset;
import com.ams.hrms.model.Employee;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Assign-asset dialog (spec section 24): employee, assigned date, optional
 * due return date and notes; the service pairs the assignment with the
 * asset status flip transactionally.
 */
public class AssignDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final AssetController controller =
            new AssetController(com.ams.hrms.config.ServiceRegistry.assetService());

    private final Asset asset;
    private final List<Employee> employees = new ArrayList<>();

    private FormField employeeField;
    private DatePickerField assignedDatePicker;
    private DatePickerField dueReturnPicker;
    private FormField notesField;

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Assign Asset", "check");
    private Result result = Result.CANCELLED;

    public AssignDialog(java.awt.Window owner, Asset asset, List<Employee> employees) {
        super(owner, "Assign Asset", ModalityType.APPLICATION_MODAL);
        this.asset = asset;
        this.employees.addAll(employees);

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(460, 480);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel(asset.getCode() + " - " + asset.getName());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        List<String> displays = new ArrayList<>();
        for (var employee : employees) {
            displays.add(employee.getCode() + " - " + employee.getFullName());
        }
        employeeField = FormField.custom("Employee", true,
                new javax.swing.JComboBox<>(displays.toArray(new String[0])));

        assignedDatePicker = new DatePickerField();
        assignedDatePicker.setDate(LocalDate.now());
        FormField assignedWrapper = FormField.custom("Assigned Date", true, assignedDatePicker);

        dueReturnPicker = new DatePickerField();
        FormField dueWrapper = FormField.custom("Due Return Date", false, dueReturnPicker);

        notesField = FormField.textArea("Notes", false);

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(employeeField);
        form.add(assignedWrapper);
        form.add(dueWrapper);
        form.add(notesField, "height 70!");
        form.add(errorBanner);
        return form;
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

        saveButton.setEnabled(false);
        controller.assign(
                asset.getId(),
                employees.get(combo.getSelectedIndex()).getId(),
                assignedDatePicker.getDate(),
                dueReturnPicker.getDate(),
                notesField.getText(),
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
