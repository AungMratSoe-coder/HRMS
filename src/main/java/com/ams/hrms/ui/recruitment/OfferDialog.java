package com.ams.hrms.ui.recruitment;

import java.awt.BorderLayout;
import java.awt.Font;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.controller.RecruitmentController;
import com.ams.hrms.model.JobApplication;
import com.ams.hrms.model.JobOffer;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * New offer dialog (spec section 14): drafts an offer for an application in
 * the INTERVIEW stage with a passed interview; the service enforces the
 * position salary envelope and headcount.
 */
public class OfferDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final RecruitmentController controller =
            new RecruitmentController(com.ams.hrms.config.ServiceRegistry.recruitmentService());

    private final JobApplication application;

    private FormField offeredSalaryField;
    private FormField offerDateField;
    private FormField expiryDateField;
    private FormField joiningDateField;
    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Create Draft Offer", "check");
    private Result result = Result.CANCELLED;

    public OfferDialog(java.awt.Window owner, JobApplication application) {
        super(owner, "New Offer", ModalityType.APPLICATION_MODAL);
        this.application = application;

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

        JLabel titleLabel = new JLabel("Offer for " + application.getCandidateName()
                + " - " + application.getVacancyTitle());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        offeredSalaryField = FormField.textField("Offered Salary *", true);
        offerDateField = FormField.datePicker("Offer Date *", true);
        expiryDateField = FormField.datePicker("Expiry Date", false);
        joiningDateField = FormField.datePicker("Joining Date", false);

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(offeredSalaryField);
        form.add(offerDateField);
        form.add(expiryDateField);
        form.add(joiningDateField);
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
        clearErrors();

        List<String> localErrors = new ArrayList<>();
        BigDecimal salary = Validators.parseMoney(localErrors,
                offeredSalaryField.getText(), "Offered salary");
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }
        LocalDate offerDate = offerDateField.getDate();
        if (offerDate == null) {
            showError("Offer date is required.");
            return;
        }

        JobOffer offer = new JobOffer();
        offer.setApplicationId(application.getId());
        offer.setOfferedSalary(salary);
        offer.setOfferDate(offerDate);
        offer.setExpiryDate(expiryDateField.getDate());
        offer.setJoiningDate(joiningDateField.getDate());

        saveButton.setEnabled(false);
        controller.createOffer(offer,
                () -> {
                    result = Result.SAVED;
                    dispose();
                },
                error -> {
                    saveButton.setEnabled(true);
                    Exception exception = error instanceof Exception e ? e
                            : new IllegalStateException(error);
                    // Validation errors name the actual rule (e.g. "a PASSED
                    // interview is required"); other HRMS errors already carry
                    // a user-friendly message.
                    if (exception instanceof com.ams.hrms.exception.ValidationException validation) {
                        showError(String.join(" ", validation.getErrors()));
                    } else if (exception instanceof com.ams.hrms.exception.HrmsException hrmsException) {
                        showError(hrmsException.getUserMessage());
                    } else {
                        com.ams.hrms.exception.ErrorHandler.handle(error);
                    }
                });
    }

    private void clearErrors() {
        errorBanner.setVisible(false);
    }

    private void showError(String message) {
        String escaped = message.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
        errorBanner.setText("<html><div style='width:380px'>" + escaped + "</div></html>");
        errorBanner.setVisible(true);
        revalidate();
        repaint();
    }

    public Result showDialog() {
        setVisible(true);
        return result;
    }
}

