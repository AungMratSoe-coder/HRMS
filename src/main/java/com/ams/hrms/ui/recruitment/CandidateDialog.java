package com.ams.hrms.ui.recruitment;

import java.awt.BorderLayout;
import java.awt.Font;
import java.io.File;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.controller.RecruitmentController;
import com.ams.hrms.model.Candidate;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * Create/edit candidate dialog (spec section 14): personal data, contact,
 * skills/experience and an optional resume file that is stored through the
 * document storage service.
 */
public class CandidateDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final RecruitmentController controller =
            new RecruitmentController(com.ams.hrms.config.ServiceRegistry.recruitmentService());

    private final Candidate existing;
    private File selectedResumeFile;

    private FormField firstNameField;
    private FormField lastNameField;
    private FormField genderField;
    private FormField dateOfBirthField;
    private FormField phoneField;
    private FormField emailField;
    private FormField addressField;
    private FormField skillsField;
    private FormField experienceField;
    private FormField expectedSalaryField;
    private FormField sourceField;
    private final JLabel resumeLabel = new JLabel("No resume selected");
    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    public CandidateDialog(java.awt.Window owner, Candidate existing) {
        super(owner, existing == null ? "New Candidate" : "Edit Candidate",
                ModalityType.APPLICATION_MODAL);
        this.existing = existing;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(520, 760);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
        populate();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 2, insets 24 28 12 28, gap 10", "[grow,fill][grow,fill]"));

        JLabel titleLabel = new JLabel(existing == null
                ? "Register a candidate" : "Edit " + existing.getCandidateCode());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        firstNameField = FormField.textField("First Name", true);
        lastNameField = FormField.textField("Last Name", true);
        genderField = FormField.comboBox("Gender",
                new String[]{"MALE", "FEMALE", "OTHER"}, false);
        dateOfBirthField = FormField.datePicker("Date of Birth", false);
        phoneField = FormField.textField("Phone", true);
        emailField = FormField.textField("Email", false);
        addressField = FormField.textArea("Address", false);
        skillsField = FormField.textArea("Skills (comma separated)", false);
        experienceField = FormField.textField("Experience (years)", false);
        expectedSalaryField = FormField.textField("Expected Salary", false);
        sourceField = FormField.comboBox("Source",
                new String[]{"WEBSITE", "REFERRAL", "AGENCY", "LINKEDIN", "JOB_FAIR",
                        "WALK_IN", "OTHER"}, true);

        JButton chooseResumeButton =
                new ModernButton("Choose Resume...", "documents", ModernButton.Variant.OUTLINE);
        chooseResumeButton.addActionListener(event -> chooseResumeFile());
        resumeLabel.setFont(resumeLabel.getFont().deriveFont(Font.PLAIN, 11f));
        resumeLabel.setForeground(Palette.color(Role.TEXT_MUTED));

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "span 2, wrap unrelated");
        form.add(firstNameField);
        form.add(lastNameField);
        form.add(genderField);
        form.add(dateOfBirthField);
        form.add(phoneField);
        form.add(emailField);
        form.add(addressField, "span 2");
        form.add(skillsField, "span 2");
        form.add(experienceField);
        form.add(expectedSalaryField);
        form.add(sourceField, "span 2");

        JPanel resumeRow = new JPanel(new MigLayout("insets 0, gap 10"));
        resumeRow.setOpaque(false);
        resumeRow.add(chooseResumeButton);
        resumeRow.add(resumeLabel);
        form.add(resumeRow, "span 2");

        form.add(errorBanner, "span 2");
        return form;
    }

    private void populate() {
        if (existing == null) {
            sourceField.setText("WEBSITE");
            return;
        }
        firstNameField.setText(existing.getFirstName());
        lastNameField.setText(existing.getLastName());
        genderField.setText(existing.getGender() == null ? "" : existing.getGender());
        dateOfBirthField.setDate(existing.getDateOfBirth());
        phoneField.setText(existing.getPhone());
        emailField.setText(existing.getEmail() == null ? "" : existing.getEmail());
        addressField.setText(existing.getAddress());
        skillsField.setText(existing.getSkills());
        experienceField.setText(existing.getExperienceYears() == null
                ? "" : existing.getExperienceYears().toPlainString());
        expectedSalaryField.setText(existing.getExpectedSalary() == null
                ? "" : existing.getExpectedSalary().toPlainString());
        sourceField.setText(existing.getSource());
        if (existing.getResumePath() != null) {
            resumeLabel.setText(existing.getResumePath()
                    .substring(existing.getResumePath().lastIndexOf('/') + 1));
        }
    }

    /** Lets the user pick a resume; stored only when the dialog is saved. */
    private void chooseResumeFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select resume file");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Documents (PDF, Word, Images)", "pdf", "doc", "docx", "jpg", "jpeg", "png"));
        int choice = chooser.showOpenDialog(this);
        if (choice == JFileChooser.APPROVE_OPTION) {
            selectedResumeFile = chooser.getSelectedFile();
            resumeLabel.setText(selectedResumeFile.getName());
        }
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

    @SuppressWarnings("unchecked")
    private void submit() {
        clearErrors();

        List<String> localErrors = new ArrayList<>();
        BigDecimal experience = Validators.parseMoney(localErrors,
                experienceField.getText(), "Experience");
        BigDecimal expectedSalary = Validators.parseMoney(localErrors,
                expectedSalaryField.getText(), "Expected salary");
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        Candidate candidate = existing == null ? new Candidate() : existing;
        candidate.setFirstName(firstNameField.getText());
        candidate.setLastName(lastNameField.getText());
        candidate.setGender(genderField.getText().isEmpty() ? null : genderField.getText());
        candidate.setDateOfBirth(dateOfBirthField.getDate());
        candidate.setPhone(phoneField.getText());
        candidate.setEmail(emailField.getText());
        candidate.setAddress(addressField.getText());
        candidate.setSkills(skillsField.getText());
        candidate.setExperienceYears(experience);
        candidate.setExpectedSalary(expectedSalary);
        candidate.setSource(sourceField.getText());
        candidate.setResumeFile(selectedResumeFile == null
                ? null : selectedResumeFile.toPath());

        saveButton.setEnabled(false);
        controller.saveCandidate(candidate,
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

    private void clearErrors() {
        errorBanner.setVisible(false);
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

