package com.ams.hrms.ui.training;

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
import com.ams.hrms.controller.TrainingController;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.TrainingProgram;
import com.ams.hrms.model.TrainingSession;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Enrollment dialog (spec section 23): puts an employee into a program,
 * optionally pinned to one of its live sessions.
 */
public class EnrollmentDialog extends JDialog {

    public enum Result {
        SAVED, CANCELLED
    }

    private final TrainingController controller =
            new TrainingController(com.ams.hrms.config.ServiceRegistry.trainingService());

    private final TrainingProgram program;
    private final List<Employee> employees = new ArrayList<>();
    private final List<TrainingSession> sessions = new ArrayList<>();

    private FormField employeeField;
    private FormField sessionField;

    private final JLabel errorBanner = new JLabel();
    private final JButton saveButton = new ModernButton("Enroll", "check");
    private Result result = Result.CANCELLED;

    public EnrollmentDialog(java.awt.Window owner, TrainingProgram program,
                            List<Employee> employees, List<TrainingSession> sessions) {
        super(owner, "Enroll into '" + program.getName() + "'",
                ModalityType.APPLICATION_MODAL);
        this.program = program;
        this.employees.addAll(employees);
        this.sessions.addAll(sessions);

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.CENTER);
        add(buildButtons(), BorderLayout.SOUTH);

        setSize(460, 360);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        getRootPane().setDefaultButton(saveButton);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 10", "[grow,fill]"));

        JLabel titleLabel = new JLabel(program.getName() + " ("
                + (program.getCapacity() == null
                        ? "unlimited seats" : program.getCapacity() + " seat(s), "
                                + program.getEnrolledCount() + " taken") + ")");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        List<String> employeeDisplays = new ArrayList<>();
        for (var employee : employees) {
            employeeDisplays.add(employee.getCode() + " - " + employee.getFullName());
        }
        employeeField = FormField.custom("Employee", true,
                new javax.swing.JComboBox<>(employeeDisplays.toArray(new String[0])));

        List<String> sessionDisplays = new ArrayList<>();
        sessionDisplays.add("Not pinned to a session");
        for (var session : sessions) {
            sessionDisplays.add(session.getStartDateTime().toLocalDate() + " "
                    + session.getStartDateTime().toLocalTime()
                    + " @ " + (session.getLocation() == null ? "-" : session.getLocation()));
        }
        sessionField = FormField.custom("Session", false,
                new javax.swing.JComboBox<>(sessionDisplays.toArray(new String[0])));

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVisible(false);

        form.add(titleLabel, "wrap unrelated");
        form.add(employeeField);
        form.add(sessionField);
        form.add(errorBanner);
        return form;
    }

    // ------------------------------------------------------------------
    // Submit
    // ------------------------------------------------------------------

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

    @SuppressWarnings("unchecked")
    private void submit() {
        errorBanner.setVisible(false);

        var employeeCombo = (javax.swing.JComboBox<String>) employeeField.editor();
        var sessionCombo = (javax.swing.JComboBox<String>) sessionField.editor();
        if (employeeCombo.getSelectedIndex() < 0) {
            showError("Employee is required.");
            return;
        }

        com.ams.hrms.model.EmployeeTraining enrollment =
                new com.ams.hrms.model.EmployeeTraining();
        enrollment.setProgramId(program.getId());
        enrollment.setEmployeeId(employees.get(employeeCombo.getSelectedIndex()).getId());
        int sessionIndex = sessionCombo.getSelectedIndex();
        enrollment.setSessionId(sessionIndex > 0 ? sessions.get(sessionIndex - 1).getId() : null);
        enrollment.setNotes(null);

        saveButton.setEnabled(false);
        controller.enroll(enrollment,
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
