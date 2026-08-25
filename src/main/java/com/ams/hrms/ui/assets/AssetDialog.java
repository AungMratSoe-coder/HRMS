package com.ams.hrms.ui.assets;

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
import com.ams.hrms.controller.AssetController;
import com.ams.hrms.model.Asset;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * Create/edit asset dialog (spec section 24): identity, category, serial,
 * purchase data, warranty and condition.
 */
public class AssetDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private static final String[] CATEGORIES = {
            "LAPTOP", "DESKTOP", "MONITOR", "PHONE", "TABLET",
            "ID_CARD", "VEHICLE", "FURNITURE", "OTHER"};
    private static final String[] CONDITIONS = {"NEW", "GOOD", "FAIR", "POOR", "DAMAGED"};

    private final AssetController controller =
            new AssetController(com.ams.hrms.config.ServiceRegistry.assetService());

    private final Asset existing;

    private FormField nameField;
    private FormField categoryField;
    private FormField serialField;
    private FormField purchaseDateField;
    private FormField purchaseCostField;
    private FormField warrantyField;
    private FormField conditionField;
    private FormField notesField;

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    public AssetDialog(java.awt.Window owner, Asset existing) {
        super(owner, existing == null ? "New Asset" : "Edit Asset",
                ModalityType.APPLICATION_MODAL);
        this.existing = existing;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(500, 620);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
        populate();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 2, insets 24 28 12 28, gap 10", "[grow,fill][grow,fill]"));

        JLabel titleLabel = new JLabel(existing == null
                ? "Register a company asset" : "Edit " + existing.getCode());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        nameField = FormField.textField("Asset Name", true);
        categoryField = FormField.comboBox("Category", CATEGORIES, true);
        serialField = FormField.textField("Serial Number", false);
        purchaseDateField = FormField.datePicker("Purchase Date", false);
        purchaseCostField = FormField.textField("Purchase Cost", false);
        warrantyField = FormField.datePicker("Warranty Expiry", false);
        conditionField = FormField.comboBox("Condition", CONDITIONS, true);
        notesField = FormField.textArea("Notes", false);

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "span 2, wrap unrelated");
        form.add(nameField, "span 2");
        form.add(categoryField);
        form.add(conditionField);
        form.add(serialField, "span 2");
        form.add(purchaseDateField);
        form.add(purchaseCostField);
        form.add(warrantyField, "span 2");
        form.add(notesField, "span 2");
        form.add(errorBanner, "span 2");
        return form;
    }

    private void populate() {
        if (existing == null) {
            categoryField.setText("LAPTOP");
            conditionField.setText("NEW");
            return;
        }
        nameField.setText(existing.getName());
        categoryField.setText(existing.getCategory());
        serialField.setText(existing.getSerialNumber() == null
                ? "" : existing.getSerialNumber());
        purchaseDateField.setDate(existing.getPurchaseDate());
        purchaseCostField.setText(existing.getPurchaseCost() == null
                ? "" : existing.getPurchaseCost().toPlainString());
        warrantyField.setDate(existing.getWarrantyExpiry());
        conditionField.setText(existing.getConditionStatus());
        notesField.setText(existing.getNotes());

        boolean frozen = "RETIRED".equals(existing.getStatus())
                || "LOST".equals(existing.getStatus());
        nameField.editor().setEnabled(!frozen);
        categoryField.editor().setEnabled(!frozen);
        saveButton.setEnabled(!frozen);
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
        BigDecimal cost = Validators.parseMoney(localErrors,
                purchaseCostField.getText(), "Purchase cost");
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        Asset asset = existing == null ? new Asset() : existing;
        asset.setName(nameField.getText());
        asset.setCategory(categoryField.getText());
        asset.setSerialNumber(serialField.getText());
        asset.setPurchaseDate(purchaseDateField.getDate());
        asset.setPurchaseCost(cost);
        asset.setWarrantyExpiry(warrantyField.getDate());
        asset.setConditionStatus(conditionField.getText());
        asset.setNotes(notesField.getText());

        saveButton.setEnabled(false);
        controller.saveAsset(asset,
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
