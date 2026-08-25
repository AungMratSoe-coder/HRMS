package com.ams.hrms.ui.recruitment;

import java.awt.BorderLayout;
import java.awt.Font;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import com.ams.hrms.controller.RecruitmentController;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.Interview;
import com.ams.hrms.model.JobApplication;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * Interview dialog (spec section 14) with two modes: SCHEDULING creates the
 * next round for an eligible application; RESULT records the outcome of a
 * pending interview (PASS/FAIL/ON_HOLD plus score and notes).
 */
public class InterviewDialog extends JDialog {

    public enum Mode {
        SCHEDULE, RESULT
    }

    public enum Result {
        SAVED, CANCELLED
    }

    private final RecruitmentController controller =
            new RecruitmentController(com.ams.hrms.config.ServiceRegistry.recruitmentService());

    private final Mode mode;
    private final List<JobApplication> applications = new ArrayList<>();
    private final List<Employee> interviewers = new ArrayList<>();
    private final JobApplication fixedApplication;
    private final Interview existingInterview;

    private FormField applicationField;
    private DatePickerField datePickerField;
    private FormField timeField;
    private FormField modeField;
    private FormField interviewerField;
    private FormField notesField;
    private FormField resultField;
    private FormField scoreField;

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    private InterviewDialog(java.awt.Window owner, Mode mode,
                            List<JobApplication> applications, List<Employee> interviewers,
                            JobApplication fixedApplication, Interview existingInterview) {
        super(owner, mode == Mode.SCHEDULE ? "Schedule Interview" : "Record Interview Result",
                ModalityType.APPLICATION_MODAL);
        this.mode = mode;
        this.applications.addAll(applications);
        this.interviewers.addAll(interviewers);
        this.fixedApplication = fixedApplication;
        this.existingInterview = existingInterview;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(480, 560);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
    }

    /** Schedule-mode factory: next round for an eligible application. */
    public static InterviewDialog forScheduling(java.awt.Window owner,
                                                List<JobApplication> applications,
                                                List<Employee> interviewers,
                                                JobApplication preselected) {
        return new InterviewDialog(owner, Mode.SCHEDULE, applications, interviewers,
                preselected, null);
    }

    /** Result-mode factory: record the outcome of a pending interview. */
    public static InterviewDialog forResult(java.awt.Window owner, Interview interview) {
        return new InterviewDialog(owner, Mode.RESULT, List.of(), List.of(), null, interview);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel(mode == Mode.SCHEDULE
                ? "Schedule the next interview round" : "Round "
                        + existingInterview.getInterviewRound() + " - "
                        + existingInterview.getCandidateName());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");

        if (mode == Mode.SCHEDULE) {
            buildScheduleFields(form);
        } else {
            buildResultFields(form);
        }
        form.add(errorBanner);
        return form;
    }

    @SuppressWarnings("unchecked")
    private void buildScheduleFields(JPanel form) {
        List<String> displays = new ArrayList<>();
        for (var application : applications) {
            displays.add(application.getApplicationCode() + " - " + application.getCandidateName()
                    + " (" + application.getVacancyTitle() + ")");
        }
        applicationField = FormField.custom("Application", true,
                new javax.swing.JComboBox<>(displays.toArray(new String[0])));

        datePickerField = new DatePickerField();
        datePickerField.setDate(LocalDate.now());
        FormField dateField = FormField.custom("Interview Date", true, datePickerField);

        timeField = FormField.textField("Start Time (HH:mm)", true);
        timeField.setText("10:00");

        modeField = FormField.comboBox("Mode",
                new String[]{"IN_PERSON", "PHONE", "VIDEO"}, true);

        List<String> interviewerDisplays = new ArrayList<>();
        for (var employee : interviewers) {
            interviewerDisplays.add(employee.getCode() + " - " + employee.getFullName());
        }
        interviewerField = FormField.custom("Interviewer",
                false, new javax.swing.JComboBox<>(
                        interviewerDisplays.toArray(new String[0])));

        notesField = FormField.textArea("Notes", false);

        form.add(applicationField);
        form.add(dateField);
        form.add(timeField);
        form.add(modeField);
        form.add(interviewerField);
        form.add(notesField, "height 70!");

        if (fixedApplication != null) {
            for (int i = 0; i < applications.size(); i++) {
                if (applications.get(i).getId() == fixedApplication.getId()) {
                    ((javax.swing.JComboBox<String>) applicationField.editor()).setSelectedIndex(i);
                }
            }
        }
    }

    private void buildResultFields(JPanel form) {
        resultField = FormField.comboBox("Result", new String[]{"PASS", "FAIL", "ON_HOLD"}, true);
        scoreField = FormField.textField("Score (0-100)", false);
        notesField = FormField.textArea("Notes", false);

        form.add(resultField);
        form.add(scoreField);
        form.add(notesField, "height 70!");
    }

    private JPanel buildButtons() {
        JPanel buttons = new JPanel(new MigLayout("insets 14 28, gap 10, alignx right", "push[][]"));
        buttons.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                Palette.color(Role.CARD_BORDER)));

        saveButton.setText(mode == Mode.SCHEDULE ? "Schedule" : "Save Result");
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

        if (mode == Mode.SCHEDULE) {
            submitSchedule();
        } else {
            submitResult();
        }
    }

    @SuppressWarnings("unchecked")
    private void submitSchedule() {
        int applicationIndex = ((javax.swing.JComboBox<String>) applicationField.editor())
                .getSelectedIndex();
        int modeIndex = ((javax.swing.JComboBox<String>) modeField.editor()).getSelectedIndex();
        int interviewerIndex = ((javax.swing.JComboBox<String>) interviewerField.editor())
                .getSelectedIndex();

        LocalDate date = datePickerField.getDate();

        List<String> localErrors = new ArrayList<>();
        if (applicationIndex < 0) {
            localErrors.add("Application is required.");
        }
        if (date == null) {
            localErrors.add("Interview date is required.");
        }
        LocalTime time = Validators.parseTime(localErrors, timeField.getText(), "Start time");
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        Interview interview = new Interview();
        interview.setApplicationId(applications.get(applicationIndex).getId());
        interview.setInterviewDate(LocalDateTime.of(date, time));
        interview.setMode(modeField.getText());
        interview.setInterviewerId(interviewerIndex >= 0
                ? interviewers.get(interviewerIndex).getId() : null);
        interview.setNotes(notesField.getText());

        saveButton.setEnabled(false);
        controller.scheduleInterview(interview,
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

    private void submitResult() {
        List<String> localErrors = new ArrayList<>();
        BigDecimal score = Validators.parseMoney(localErrors, scoreField.getText(), "Score");
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }

        saveButton.setEnabled(false);
        controller.recordInterviewResult(
                existingInterview.getId(),
                resultField.getText(),
                score == null ? null : score,
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

