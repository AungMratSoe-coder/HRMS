package com.ams.hrms.ui.recruitment;

import java.awt.BorderLayout;
import java.awt.Font;
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
import com.ams.hrms.model.Candidate;
import com.ams.hrms.model.JobVacancy;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * New application dialog (spec section 14): pairs an active candidate with an
 * open vacancy and records a cover letter.
 */
public class ApplicationDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final RecruitmentController controller =
            new RecruitmentController(com.ams.hrms.config.ServiceRegistry.recruitmentService());

    private final List<Candidate> candidates = new ArrayList<>();
    private final List<JobVacancy> vacancies = new ArrayList<>();

    private FormField candidateField;
    private FormField vacancyField;
    private FormField coverLetterField;
    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Submit Application", "check");
    private Result result = Result.CANCELLED;

    public ApplicationDialog(java.awt.Window owner, List<Candidate> candidates,
                             List<JobVacancy> vacancies) {
        super(owner, "New Application", ModalityType.APPLICATION_MODAL);
        this.candidates.addAll(candidates);
        this.vacancies.addAll(vacancies);

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(480, 520);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel("Apply candidate to vacancy");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        candidateField = FormField.custom("Candidate", true, buildCandidateCombo());
        vacancyField = FormField.custom("Vacancy (OPEN)", true, buildVacancyCombo());
        coverLetterField = FormField.textArea("Cover Letter", false);

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(candidateField);
        form.add(vacancyField);
        form.add(coverLetterField, "height 90!");
        form.add(errorBanner);
        return form;
    }

    private javax.swing.JComboBox<String> buildCandidateCombo() {
        List<String> displays = new ArrayList<>();
        for (var candidate : candidates) {
            displays.add(candidate.getCandidateCode() + " - " + candidate.getFullName());
        }
        return new javax.swing.JComboBox<>(displays.toArray(new String[0]));
    }

    private javax.swing.JComboBox<String> buildVacancyCombo() {
        List<String> displays = new ArrayList<>();
        for (var vacancy : vacancies) {
            displays.add(vacancy.getVacancyCode() + " - " + vacancy.getTitle()
                    + " (" + vacancy.remainingHeadcount() + " seat(s))");
        }
        return new javax.swing.JComboBox<>(displays.toArray(new String[0]));
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

        int candidateIndex = ((javax.swing.JComboBox<String>) candidateField.editor())
                .getSelectedIndex();
        int vacancyIndex = ((javax.swing.JComboBox<String>) vacancyField.editor())
                .getSelectedIndex();

        if (candidateIndex < 0 || vacancyIndex < 0) {
            showError("Candidate and vacancy are required.");
            return;
        }

        saveButton.setEnabled(false);
        controller.apply(
                candidates.get(candidateIndex).getId(),
                vacancies.get(vacancyIndex).getId(),
                coverLetterField.getText(),
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

