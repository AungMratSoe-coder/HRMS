package com.ams.hrms.ui.training;

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
import com.ams.hrms.controller.TrainingController;
import com.ams.hrms.model.TrainingProgram;
import com.ams.hrms.model.TrainingSession;
import com.ams.hrms.service.TrainingRules;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.validator.Validators;

import net.miginfocom.swing.MigLayout;

/**
 * Create/edit training session dialog (spec section 23): start/end date-time
 * with a live computed duration, location.
 */
public class SessionDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final TrainingController controller =
            new TrainingController(com.ams.hrms.config.ServiceRegistry.trainingService());

    private final TrainingProgram program;
    private final TrainingSession existing;

    private DatePickerField startDatePicker;
    private FormField startTimeField;
    private DatePickerField endDatePicker;
    private FormField endTimeField;
    private FormField locationField;
    private final JLabel durationLabel = new JLabel(" ");

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Save", "check");
    private Result result = Result.CANCELLED;

    public SessionDialog(java.awt.Window owner, TrainingProgram program,
                         TrainingSession existing) {
        super(owner, existing == null ? "New Session" : "Edit Session",
                ModalityType.APPLICATION_MODAL);
        this.program = program;
        this.existing = existing;

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(460, 520);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
        wireLiveDuration();
        populate();
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 2, insets 24 28 12 28, gap 10", "[grow,fill][grow,fill]"));

        JLabel titleLabel = new JLabel("Session for '" + program.getName() + "'");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        startDatePicker = new DatePickerField();
        startTimeField = FormField.textField("Start Time (HH:mm)", true);
        startTimeField.setText("09:00");
        endDatePicker = new DatePickerField();
        endTimeField = FormField.textField("End Time (HH:mm)", true);
        endTimeField.setText("17:00");
        locationField = FormField.textField("Location", false);

        durationLabel.setFont(durationLabel.getFont().deriveFont(Font.PLAIN, 11f));
        durationLabel.setForeground(Palette.color(Role.TEXT_MUTED));

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "span 2, wrap unrelated");
        FormField startWrapper = FormField.custom("Start Date", true, startDatePicker);
        form.add(startWrapper);
        form.add(startTimeField);
        FormField endWrapper = FormField.custom("End Date", true, endDatePicker);
        form.add(endWrapper);
        form.add(endTimeField);
        form.add(locationField, "span 2");
        form.add(durationLabel, "span 2");
        form.add(errorBanner, "span 2");
        return form;
    }

    /** Recomputes the shown duration whenever a boundary changes. */
    private void wireLiveDuration() {
        Runnable refreshDuration = () -> {
            LocalDateTime start = combine(startDatePicker.getDate(), startTimeField.getText());
            LocalDateTime end = combine(endDatePicker.getDate(), endTimeField.getText());
            BigDecimal hours = TrainingRules.durationHours(start, end);
            durationLabel.setText(hours == null
                    ? " " : "Duration: " + hours.toPlainString() + " hour(s)");
        };
        startDatePicker.addDateChangedListener(event -> refreshDuration.run());
        endDatePicker.addDateChangedListener(event -> refreshDuration.run());
        startTimeField.editor().addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent event) {
                refreshDuration.run();
            }
        });
        endTimeField.editor().addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyReleased(java.awt.event.KeyEvent event) {
                refreshDuration.run();
            }
        });
    }

    private void populate() {
        if (existing == null) {
            LocalDate today = LocalDate.now();
            startDatePicker.setDate(today);
            endDatePicker.setDate(today);
            return;
        }
        startDatePicker.setDate(existing.getStartDateTime().toLocalDate());
        startTimeField.setText(existing.getStartDateTime().toLocalTime().toString());
        endDatePicker.setDate(existing.getEndDateTime().toLocalDate());
        endTimeField.setText(existing.getEndDateTime().toLocalTime().toString());
        locationField.setText(existing.getLocation() == null ? "" : existing.getLocation());
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
        LocalDate startDate = startDatePicker.getDate();
        LocalDate endDate = endDatePicker.getDate();
        LocalTime start = Validators.parseTime(localErrors,
                startTimeField.getText(), "Start time");
        LocalTime end = Validators.parseTime(localErrors,
                endTimeField.getText(), "End time");
        if (!localErrors.isEmpty()) {
            showError(String.join(" ", localErrors));
            return;
        }
        if (startDate == null || endDate == null) {
            showError("Start and end dates are required.");
            return;
        }

        TrainingSession session = existing == null ? new TrainingSession() : existing;
        session.setProgramId(program.getId());
        session.setStartDateTime(LocalDateTime.of(startDate, start));
        session.setEndDateTime(LocalDateTime.of(endDate, end));
        session.setLocation(locationField.getText());

        saveButton.setEnabled(false);
        controller.saveSession(session,
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

    private static LocalDateTime combine(LocalDate date, String timeText) {
        if (date == null) {
            return null;
        }
        try {
            return LocalDateTime.of(date, LocalTime.parse(timeText.trim()));
        } catch (Exception e) {
            return null;
        }
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
