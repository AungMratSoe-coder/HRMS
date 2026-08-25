package com.ams.hrms.ui.training;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.SearchField;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.TrainingController;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.EmployeeTraining;
import com.ams.hrms.model.TrainingProgram;
import com.ams.hrms.model.TrainingSession;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.service.TrainingRules;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

/**
 * Training module (spec section 23): programs, sessions and per-employee
 * enrollments/results, following the shared toolbar/table/context-menu
 * pattern of the other modules.
 */
public class TrainingPanel extends JPanel {

    private final TrainingController controller =
            new TrainingController(ServiceRegistry.trainingService());

    // --- programs tab ---
    private final SearchField programSearch = new SearchField("Search name, code, trainer...");
    private final JComboBox<String> programStatusFilter = new JComboBox<>(withAll(
            TrainingRules.PROGRAM_STATUSES));
    private final SecureButton newProgramButton =
            new SecureButton("New Program", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.TRAINING_MANAGE);
    private final HrmsTable programTable = HrmsTable.builder(
                    "ID", "Code", "Program", "Trainer", "Cost",
                    "Capacity", "Enrolled", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 85)
            .fixedColumn(4, 95)
            .fixedColumn(5, 80)
            .fixedColumn(6, 80)
            .badgeColumn(7)
            .contextMenu(this::buildProgramMenu)
            .build();
    private List<TrainingProgram> loadedPrograms = List.of();

    // --- sessions tab ---
    private final JComboBox<String> sessionProgramFilter = new JComboBox<>();
    private final List<TrainingProgram> sessionPrograms = new ArrayList<>();
    private final SecureButton newSessionButton =
            new SecureButton("New Session", "calendar", ModernButton.Variant.PRIMARY,
                    Permissions.TRAINING_MANAGE);
    private final HrmsTable sessionTable = HrmsTable.builder(
                    "ID", "Program", "Start", "End", "Hours", "Location", "Status")
            .hiddenColumn(0)
            .fixedColumn(2, 145)
            .fixedColumn(3, 145)
            .fixedColumn(4, 65)
            .fixedColumn(6, 105)
            .badgeColumn(6)
            .contextMenu(this::buildSessionMenu)
            .build();
    private List<TrainingSession> loadedSessions = List.of();

    // --- enrollments tab ---
    private final JComboBox<String> enrollmentProgramFilter = new JComboBox<>();
    private final List<TrainingProgram> enrollmentPrograms = new ArrayList<>();
    private final SecureButton enrollButton =
            new SecureButton("Enroll Employee", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.TRAINING_MANAGE);
    private final HrmsTable enrollmentTable = HrmsTable.builder(
                    "ID", "Employee", "Session", "Result", "Score", "Completed", "Notes")
            .hiddenColumn(0)
            .fixedColumn(2, 170)
            .fixedColumn(3, 100)
            .fixedColumn(4, 70)
            .fixedColumn(5, 95)
            .badgeColumn(3)
            .contextMenu(this::buildEnrollmentMenu)
            .build();
    private List<EmployeeTraining> loadedEnrollments = List.of();

    public TrainingPanel() {
        super(new BorderLayout());
        setOpaque(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Programs", buildProgramsTab());
        tabs.addTab("Sessions", buildSessionsTab());
        tabs.addTab("Enrollments", buildEnrollmentsTab());
        add(tabs, BorderLayout.CENTER);

        wireEvents();
        refreshPrograms();
        loadProgramPickers();
        refreshEnrollments();
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private static String[] withAll(java.util.Collection<String> statuses) {
        List<String> sorted = new ArrayList<>(statuses);
        java.util.Collections.sort(sorted);
        List<String> options = new ArrayList<>();
        options.add("All Statuses");
        options.addAll(sorted);
        return options.toArray(new String[0]);
    }

    private JPanel buildProgramsTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10", "[grow,fill][][][fill]"));
        toolbar.setOpaque(false);
        toolbar.add(programSearch);
        toolbar.add(programStatusFilter, "width 130!");
        toolbar.add(new JLabel(""), "width 4");
        toolbar.add(newProgramButton);

        tab.add(toolbar, BorderLayout.NORTH);
        tab.add(new JScrollPane(programTable), BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildSessionsTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10", "[][][grow,fill][]"));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("Program:"), "gapright 4");
        toolbar.add(sessionProgramFilter, "width 280!");
        toolbar.add(new JLabel(""), "growx");
        toolbar.add(newSessionButton);

        tab.add(toolbar, BorderLayout.NORTH);
        tab.add(new JScrollPane(sessionTable), BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildEnrollmentsTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10", "[][][grow,fill][]"));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("Program:"), "gapright 4");
        toolbar.add(enrollmentProgramFilter, "width 280!");
        toolbar.add(new JLabel(""), "growx");
        toolbar.add(enrollButton);

        tab.add(toolbar, BorderLayout.NORTH);
        tab.add(new JScrollPane(enrollmentTable), BorderLayout.CENTER);
        return tab;
    }

    private void wireEvents() {
        programSearch.onTextChanged(text -> refreshPrograms());
        programStatusFilter.addActionListener(event -> refreshPrograms());
        newProgramButton.addActionListener(event -> openProgramDialog(null));

        sessionProgramFilter.addActionListener(event -> refreshSessions());
        newSessionButton.addActionListener(event -> openSessionDialog(null));

        enrollmentProgramFilter.addActionListener(event -> refreshEnrollments());
        enrollButton.addActionListener(event -> openEnrollmentDialog());
    }

    /** Both session and enrollment tabs pick a program from the same live list. */
    private void loadProgramPickers() {
        UiThread.executeAsync("Load program pickers",
                () -> ServiceRegistry.trainingService().findPrograms(null, null),
                programs -> {
                    sessionProgramFilter.removeAllItems();
                    enrollmentProgramFilter.removeAllItems();
                    sessionPrograms.clear();
                    enrollmentPrograms.clear();

                    sessionProgramFilter.addItem("All Programs");
                    enrollmentProgramFilter.addItem("All Programs");
                    for (var program : programs) {
                        sessionPrograms.add(program);
                        enrollmentPrograms.add(program);
                        String display = program.getCode() + " - " + program.getName();
                        sessionProgramFilter.addItem(display);
                        enrollmentProgramFilter.addItem(display);
                    }
                    refreshSessions();
                });
    }

    private TrainingProgram pickerProgram(JComboBox<String> combo,
                                          List<TrainingProgram> source) {
        int index = combo.getSelectedIndex();
        return index <= 0 ? null
                : source.get(index - 1);
    }

    // ------------------------------------------------------------------
    // Programs
    // ------------------------------------------------------------------

    private void refreshPrograms() {
        String status = programStatusFilter.getSelectedIndex() <= 0
                ? "" : String.valueOf(programStatusFilter.getSelectedItem());
        controller.loadPrograms(programSearch.getText(), status, programs -> {
            loadedPrograms = programs;
            List<Object[]> rows = new ArrayList<>();
            for (var program : programs) {
                rows.add(new Object[]{
                        program.getId(),
                        program.getCode(),
                        program.getName(),
                        program.getTrainerName() == null ? "-" : program.getTrainerName(),
                        program.getCost() == null ? "-" : program.getCost().toPlainString(),
                        program.getCapacity() == null ? "∞" : program.getCapacity(),
                        program.getEnrolledCount(),
                        program.getStatus()});
            }
            programTable.setRows(rows);
        });
    }

    private TrainingProgram selectedProgram() {
        return findById(programTable, 0, loadedPrograms, TrainingProgram::getId);
    }

    private void openProgramDialog(TrainingProgram existing) {
        ProgramDialog dialog = new ProgramDialog(swingWindow(), existing);
        if (dialog.showDialog() == ProgramDialog.Result.SAVED) {
            refreshPrograms();
            loadProgramPickers();
        }
    }

    private void transitionProgram(TrainingProgram program, String target) {
        boolean confirmed = Dialogs.confirm(swingWindow(), "Update Program",
                "Set program '" + program.getCode() + "' (" + program.getName()
                        + ") to " + target + "?");
        if (!confirmed) {
            return;
        }
        controller.setProgramStatus(program.getId(), target, () -> {
            refreshPrograms();
            loadProgramPickers();
        }, error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private JPopupMenu buildProgramMenu() {
        JPopupMenu menu = new JPopupMenu();
        var program = selectedProgram();
        boolean manage = SecurityService.can(Permissions.TRAINING_MANAGE);
        String status = program == null ? "" : program.getStatus();

        JMenuItem edit = new JMenuItem("Edit Program");
        edit.setEnabled(program != null && manage
                && TrainingRules.programAcceptsEnrollment(status));
        edit.addActionListener(event -> openProgramDialog(selectedProgram()));

        JMenuItem start = new JMenuItem("Start (ONGOING)");
        start.setEnabled(manage && "PLANNED".equals(status));
        start.addActionListener(event ->
                transitionProgram(selectedProgram(), "ONGOING"));

        JMenuItem complete = new JMenuItem("Complete");
        complete.setEnabled(manage && ("PLANNED".equals(status)
                || "ONGOING".equals(status)));
        complete.addActionListener(event ->
                transitionProgram(selectedProgram(), "COMPLETED"));

        JMenuItem cancel = new JMenuItem("Cancel Program");
        cancel.setEnabled(manage && TrainingRules.programAcceptsEnrollment(status));
        cancel.addActionListener(event ->
                transitionProgram(selectedProgram(), "CANCELLED"));

        menu.add(edit);
        menu.addSeparator();
        menu.add(start);
        menu.add(complete);
        menu.add(cancel);
        return menu;
    }

    // ------------------------------------------------------------------
    // Sessions
    // ------------------------------------------------------------------

    private void refreshSessions() {
        TrainingProgram program = pickerProgram(sessionProgramFilter, sessionPrograms);
        controller.loadSessions(program == null ? null : program.getId(), null, sessions -> {
            loadedSessions = sessions;
            List<Object[]> rows = new ArrayList<>();
            for (var session : sessions) {
                rows.add(new Object[]{
                        session.getId(),
                        session.getProgramName(),
                        session.getStartDateTime().format(java.time.format
                                .DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        session.getEndDateTime().format(java.time.format
                                .DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        session.getDurationHours() == null
                                ? "-" : session.getDurationHours().toPlainString(),
                        session.getLocation() == null ? "-" : session.getLocation(),
                        session.getStatus()});
            }
            sessionTable.setRows(rows);
        });
    }

    private TrainingSession selectedSession() {
        return findById(sessionTable, 0, loadedSessions, TrainingSession::getId);
    }

    private void openSessionDialog(TrainingSession existing) {
        TrainingProgram program = existing == null
                ? pickerProgram(sessionProgramFilter, sessionPrograms)
                : loadedPrograms.stream()
                        .filter(candidate -> candidate.getId() == existing.getProgramId())
                        .findFirst().orElse(null);
        if (program == null) {
            Dialogs.info(swingWindow(), "Pick a program",
                    "Choose a program filter first (sessions belong to a program).");
            return;
        }
        SessionDialog dialog = new SessionDialog(swingWindow(), program, existing);
        if (dialog.showDialog() == SessionDialog.Result.SAVED) {
            refreshSessions();
        }
    }

    private void transitionSession(TrainingSession session, String target) {
        boolean confirmed = Dialogs.confirm(swingWindow(), "Update Session",
                "Set the " + session.getProgramName() + " session on "
                        + session.getStartDateTime().toLocalDate() + " to " + target + "?");
        if (!confirmed) {
            return;
        }
        controller.setSessionStatus(session.getId(), target, this::refreshSessions,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private JPopupMenu buildSessionMenu() {
        JPopupMenu menu = new JPopupMenu();
        var session = selectedSession();
        boolean manage = SecurityService.can(Permissions.TRAINING_MANAGE);
        boolean live = session != null
                && TrainingRules.sessionAcceptsReference(session.getStatus());

        JMenuItem edit = new JMenuItem("Edit Session");
        edit.setEnabled(manage && live);
        edit.addActionListener(event -> openSessionDialog(selectedSession()));

        JMenuItem start = new JMenuItem("Start (ONGOING)");
        start.setEnabled(manage && "SCHEDULED".equals(
                session == null ? "" : session.getStatus()));
        start.addActionListener(event ->
                transitionSession(selectedSession(), "ONGOING"));

        JMenuItem complete = new JMenuItem("Complete");
        complete.setEnabled(manage && live);
        complete.addActionListener(event ->
                transitionSession(selectedSession(), "COMPLETED"));

        JMenuItem cancel = new JMenuItem("Cancel Session");
        cancel.setEnabled(manage && live);
        cancel.addActionListener(event ->
                transitionSession(selectedSession(), "CANCELLED"));

        menu.add(edit);
        menu.addSeparator();
        menu.add(start);
        menu.add(complete);
        menu.add(cancel);
        return menu;
    }

    // ------------------------------------------------------------------
    // Enrollments
    // ------------------------------------------------------------------

    private void refreshEnrollments() {
        TrainingProgram program = pickerProgram(enrollmentProgramFilter, enrollmentPrograms);
        controller.loadEnrollments(program == null ? null : program.getId(), null,
                null, enrollments -> {
                    loadedEnrollments = enrollments;
                    List<Object[]> rows = new ArrayList<>();
                    for (var enrollment : enrollments) {
                        rows.add(new Object[]{
                                enrollment.getId(),
                                enrollment.getEmployeeCode() + " - "
                                        + enrollment.getEmployeeName(),
                                enrollment.getSessionSummary() == null
                                        ? "-" : enrollment.getSessionSummary(),
                                enrollment.getResult(),
                                enrollment.getScore() == null
                                        ? "-" : enrollment.getScore().toPlainString(),
                                enrollment.getCompletionDate() == null
                                        ? "-" : enrollment.getCompletionDate(),
                                enrollment.getNotes() == null ? "-" : enrollment.getNotes()});
                    }
                    enrollmentTable.setRows(rows);
                });
    }

    private EmployeeTraining selectedEnrollment() {
        return findById(enrollmentTable, 0, loadedEnrollments, EmployeeTraining::getId);
    }

    private void openEnrollmentDialog() {
        TrainingProgram program = pickerProgram(enrollmentProgramFilter, enrollmentPrograms);
        if (program == null || !TrainingRules.programAcceptsEnrollment(program.getStatus())) {
            Dialogs.info(swingWindow(), "Pick an active program",
                    "Enrollment needs a PLANNED or ONGOING program.");
            return;
        }
        UiThread.executeAsync("Load enrollment dialog data",
                () -> new Object[]{
                        ServiceRegistry.employeeService().findAll(
                                new com.ams.hrms.repository.EmployeeRepository.Filter(
                                        "", null, null, null)),
                        ServiceRegistry.trainingService().findSessions(
                                program.getId(), null)},
                result -> {
                    Object[] parts = (Object[]) result;
                    @SuppressWarnings("unchecked")
                    var employees = (List<Employee>) parts[0];
                    var sessions = (List<TrainingSession>) parts[1];

                    EnrollmentDialog dialog =
                            new EnrollmentDialog(swingWindow(), program, employees, sessions);
                    if (dialog.showDialog() == EnrollmentDialog.Result.SAVED) {
                        refreshEnrollments();
                        refreshPrograms();
                    }
                });
    }

    private void recordResult() {
        var enrollment = selectedEnrollment();
        if (enrollment == null) {
            return;
        }
        TrainingResultDialog dialog = new TrainingResultDialog(swingWindow(), enrollment);
        if (dialog.showDialog() == TrainingResultDialog.Result.SAVED) {
            refreshEnrollments();
            refreshPrograms();
        }
    }

    private void unenrollSelected() {
        var enrollment = selectedEnrollment();
        if (enrollment == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Unenroll",
                "Remove " + enrollment.getEmployeeName() + " from '"
                        + enrollment.getProgramName() + "'?");
        if (!confirmed) {
            return;
        }
        controller.unenroll(enrollment.getId(), () -> {
            refreshEnrollments();
            refreshPrograms();
        }, error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private JPopupMenu buildEnrollmentMenu() {
        JPopupMenu menu = new JPopupMenu();
        var enrollment = selectedEnrollment();
        boolean manage = SecurityService.can(Permissions.TRAINING_MANAGE);

        JMenuItem record = new JMenuItem("Record Result...");
        record.setEnabled(enrollment != null && manage);
        record.addActionListener(event -> recordResult());

        JMenuItem unenroll = new JMenuItem("Unenroll");
        unenroll.setEnabled(enrollment != null && manage
                && EmployeeTraining.RESULT_ENROLLED.equals(enrollment.getResult()));
        unenroll.addActionListener(event -> unenrollSelected());

        menu.add(record);
        menu.add(unenroll);
        return menu;
    }

    // ------------------------------------------------------------------
    // Shared helpers
    // ------------------------------------------------------------------

    private <T> T findById(HrmsTable table, int idColumn, List<T> source,
                           java.util.function.Function<T, Long> idGetter) {
        Object value = table.selectedValue(idColumn);
        if (value == null) {
            return null;
        }
        long target = ((Number) value).longValue();
        for (T item : source) {
            Long id = idGetter.apply(item);
            if (id != null && id == target) {
                return item;
            }
        }
        return null;
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
