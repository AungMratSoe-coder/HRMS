package com.ams.hrms.ui.separation;

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
import com.ams.hrms.controller.SeparationController;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.Resignation;
import com.ams.hrms.model.Termination;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.service.SeparationRules;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

/**
 * Separation module (spec section 26): resignation workflow with the
 * transactional exit checklist and immediate terminations.
 */
public class SeparationPanel extends JPanel {

    private final SeparationController controller =
            new SeparationController(ServiceRegistry.separationService());

    // --- resignations tab ---
    private final SearchField resignationSearch = new SearchField("Search employee or code...");
    private final JComboBox<String> resignationStatusFilter = new JComboBox<>(withAll(
            SeparationRules.RESIGNATION_STATUSES));
    private final SecureButton newResignationButton =
            new SecureButton("New Resignation", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.SEPARATION_MANAGE);
    private final HrmsTable resignationTable = HrmsTable.builder(
                    "ID", "Code", "Employee", "Resigned", "Last Working",
                    "Notice (d)", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 85)
            .fixedColumn(3, 95)
            .fixedColumn(4, 95)
            .fixedColumn(5, 80)
            .badgeColumn(6)
            .contextMenu(this::buildResignationMenu)
            .build();
    private List<Resignation> loadedResignations = List.of();

    // --- terminations tab ---
    private final SearchField terminationSearch = new SearchField("Search employee or code...");
    private final SecureButton newTerminationButton =
            new SecureButton("New Termination", "warning", ModernButton.Variant.DANGER,
                    Permissions.SEPARATION_MANAGE);
    private final HrmsTable terminationTable = HrmsTable.builder(
                    "ID", "Code", "Employee", "Date", "Category",
                    "Rehire", "Approved By", "Notes")
            .hiddenColumn(0)
            .fixedColumn(1, 90)
            .fixedColumn(3, 95)
            .fixedColumn(4, 110)
            .fixedColumn(5, 70)
            .fixedColumn(6, 160)
            .contextMenu(this::buildTerminationMenu)
            .build();
    private List<Termination> loadedTerminations = List.of();

    public SeparationPanel() {
        super(new BorderLayout());
        setOpaque(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Resignations", buildResignationsTab());
        tabs.addTab("Terminations", buildTerminationsTab());
        add(tabs, BorderLayout.CENTER);

        wireEvents();
        refreshResignations();
        refreshTerminations();
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

    private JPanel buildResignationsTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10", "[grow,fill][][][fill]"));
        toolbar.setOpaque(false);
        toolbar.add(resignationSearch);
        toolbar.add(resignationStatusFilter, "width 120!");
        toolbar.add(new JLabel(""), "width 4");
        toolbar.add(newResignationButton);

        tab.add(toolbar, BorderLayout.NORTH);
        tab.add(new JScrollPane(resignationTable), BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildTerminationsTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10", "[grow,fill][fill]"));
        toolbar.setOpaque(false);
        toolbar.add(terminationSearch);
        toolbar.add(newTerminationButton);

        tab.add(toolbar, BorderLayout.NORTH);
        tab.add(new JScrollPane(terminationTable), BorderLayout.CENTER);
        return tab;
    }

    private void wireEvents() {
        resignationSearch.onTextChanged(text -> refreshResignations());
        resignationStatusFilter.addActionListener(event -> refreshResignations());
        newResignationButton.addActionListener(event -> openResignationDialog());

        terminationSearch.onTextChanged(text -> refreshTerminations());
        newTerminationButton.addActionListener(event -> openTerminationDialog());
    }

    // ------------------------------------------------------------------
    // Resignations
    // ------------------------------------------------------------------

    private void refreshResignations() {
        String status = resignationStatusFilter.getSelectedIndex() <= 0
                ? "" : String.valueOf(resignationStatusFilter.getSelectedItem());
        controller.loadResignations(resignationSearch.getText(), status, resignations -> {
            loadedResignations = resignations;
            List<Object[]> rows = new ArrayList<>();
            for (var resignation : resignations) {
                rows.add(new Object[]{
                        resignation.getId(),
                        resignation.getResignationCode(),
                        resignation.getEmployeeCode() + " - " + resignation.getEmployeeName(),
                        resignation.getResignationDate(),
                        resignation.getLastWorkingDate(),
                        resignation.getNoticePeriodDays(),
                        resignation.getStatus()});
            }
            resignationTable.setRows(rows);
        });
    }

    private Resignation selectedResignation() {
        return findById(resignationTable, 0, loadedResignations, Resignation::getId);
    }

    private void openResignationDialog() {
        UiThread.executeAsync("Load resignation dialog data",
                () -> ServiceRegistry.employeeService().findAll(
                        new com.ams.hrms.repository.EmployeeRepository.Filter(
                                "", null, null, null)),
                employees -> {
                    List<Employee> active = employees.stream()
                            .filter(employee -> "ACTIVE".equals(employee.getStatus()))
                            .toList();
                    if (active.isEmpty()) {
                        Dialogs.info(swingWindow(), "No active employees",
                                "Resignations are recorded for ACTIVE employees.");
                        return;
                    }
                    ResignationDialog dialog =
                            new ResignationDialog(swingWindow(), active);
                    if (dialog.showDialog() == ResignationDialog.Result.SAVED) {
                        refreshResignations();
                    }
                });
    }

    private void approveSelected() {
        var resignation = selectedResignation();
        if (resignation == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Approve Resignation",
                "Approve resignation " + resignation.getResignationCode() + " of "
                        + resignation.getEmployeeName() + "?");
        if (!confirmed) {
            return;
        }
        controller.approveResignation(resignation.getId(), this::refreshResignations,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    /**
     * Runs the exit checklist: employment status change, shift close, asset
     * return and payroll void - all in one transaction.
     */
    private void processSelected() {
        var resignation = selectedResignation();
        if (resignation == null || !resignation.getStatus()
                .equals(Resignation.STATUS_APPROVED)) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Process Exit Checklist",
                "Process " + resignation.getResignationCode() + " for "
                        + resignation.getEmployeeName() + "?\n\n"
                        + "This sets the employee to RESIGNED, closes open shift "
                        + "assignments, returns company assets and voids draft "
                        + "payroll - in one transaction.");
        if (!confirmed) {
            return;
        }
        controller.processResignation(resignation.getId(), summary -> {
            Dialogs.info(swingWindow(), "Exit Checklist Complete", summary);
            refreshResignations();
        }, error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void closeSelected(String targetStatus) {
        var resignation = selectedResignation();
        if (resignation == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Update Resignation",
                "Set resignation " + resignation.getResignationCode() + " to "
                        + targetStatus + "?");
        if (!confirmed) {
            return;
        }
        controller.closeResignation(resignation.getId(), targetStatus,
                this::refreshResignations,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void recordExitInterview() {
        var resignation = selectedResignation();
        if (resignation == null) {
            return;
        }
        String existing = resignation.getExitInterviewNotes();
        String notes = com.ams.hrms.ui.recruitment.ReasonDialog.show(swingWindow(),
                "Exit Interview",
                "Exit interview notes for " + resignation.getEmployeeName()
                        + (existing == null ? ":" : " (replace current):"));
        if (notes == null) {
            return;
        }
        controller.saveExitInterviewNotes(resignation.getId(), notes,
                this::refreshResignations,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private JPopupMenu buildResignationMenu() {
        JPopupMenu menu = new JPopupMenu();
        var resignation = selectedResignation();
        boolean manage = SecurityService.can(Permissions.SEPARATION_MANAGE);
        String status = resignation == null ? "" : resignation.getStatus();

        JMenuItem approve = new JMenuItem("Approve");
        approve.setEnabled(manage && Resignation.STATUS_SUBMITTED.equals(status));
        approve.addActionListener(event -> approveSelected());

        JMenuItem process = new JMenuItem("Process Exit Checklist...");
        process.setEnabled(manage && Resignation.STATUS_APPROVED.equals(status));
        process.addActionListener(event -> processSelected());

        JMenuItem reject = new JMenuItem("Reject");
        reject.setEnabled(manage && Resignation.STATUS_SUBMITTED.equals(status));
        reject.addActionListener(event ->
                closeSelected(Resignation.STATUS_REJECTED));

        JMenuItem withdraw = new JMenuItem("Withdraw");
        withdraw.setEnabled(manage && Resignation.STATUS_SUBMITTED.equals(status));
        withdraw.addActionListener(event ->
                closeSelected(Resignation.STATUS_WITHDRAWN));

        JMenuItem interview = new JMenuItem("Record Exit Interview...");
        interview.setEnabled(manage && (Resignation.STATUS_APPROVED.equals(status)
                || Resignation.STATUS_PROCESSED.equals(status)));
        interview.addActionListener(event -> recordExitInterview());

        menu.add(approve);
        menu.add(process);
        menu.addSeparator();
        menu.add(reject);
        menu.add(withdraw);
        menu.addSeparator();
        menu.add(interview);
        return menu;
    }

    // ------------------------------------------------------------------
    // Terminations
    // ------------------------------------------------------------------

    private void refreshTerminations() {
        controller.loadTerminations(terminationSearch.getText(), terminations -> {
            loadedTerminations = terminations;
            List<Object[]> rows = new ArrayList<>();
            for (var termination : terminations) {
                rows.add(new Object[]{
                        termination.getId(),
                        termination.getTerminationCode(),
                        termination.getEmployeeCode() + " - " + termination.getEmployeeName(),
                        termination.getTerminationDate(),
                        termination.getReasonCategory(),
                        termination.isEligibleRehire() ? "Yes" : "No",
                        termination.getApprovedByName() == null
                                ? "-" : termination.getApprovedByName(),
                        termination.getNotes() == null ? "-" : termination.getNotes()});
            }
            terminationTable.setRows(rows);
        });
    }

    private Termination selectedTermination() {
        return findById(terminationTable, 0, loadedTerminations, Termination::getId);
    }

    private void openTerminationDialog() {
        UiThread.executeAsync("Load termination dialog data",
                () -> ServiceRegistry.employeeService().findAll(
                        new com.ams.hrms.repository.EmployeeRepository.Filter(
                                "", null, null, null)),
                employees -> {
                    List<Employee> active = employees.stream()
                            .filter(employee -> "ACTIVE".equals(employee.getStatus()))
                            .toList();
                    if (active.isEmpty()) {
                        Dialogs.info(swingWindow(), "No active employees",
                                "Terminations are recorded for ACTIVE employees.");
                        return;
                    }
                    boolean confirmed = Dialogs.confirm(swingWindow(), "Terminate",
                            "A termination is effective immediately: the employee is set "
                                    + "to TERMINATED, assignments close and assets return.\n\n"
                                    + "Continue?");
                    if (!confirmed) {
                        return;
                    }
                    TerminationDialog dialog = new TerminationDialog(swingWindow(), active);
                    if (dialog.showDialog() == TerminationDialog.Result.SAVED) {
                        refreshTerminations();
                    }
                });
    }

    private JPopupMenu buildTerminationMenu() {
        JPopupMenu menu = new JPopupMenu();
        var termination = selectedTermination();

        JMenuItem view = new JMenuItem("Details are read-only after recording");
        view.setEnabled(false);
        menu.add(view);
        return menu;
    }

    // ------------------------------------------------------------------
    // Helpers
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
