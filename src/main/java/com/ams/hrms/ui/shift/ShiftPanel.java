package com.ams.hrms.ui.shift;

import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.SearchField;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.ShiftController;
import com.ams.hrms.model.EmployeeShift;
import com.ams.hrms.model.Shift;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

/**
 * Shifts module (spec section 17): definitions tab and assignments tab.
 * Overnight shifts display a marker; assignment rows show effective ranges
 * with "Current" for open-ended records.
 */
public class ShiftPanel extends JPanel {

    private final ShiftController controller =
            new ShiftController(ServiceRegistry.shiftService());

    // --- shifts tab ---
    private final SearchField shiftSearch = new SearchField("Search shifts...");
    private final SecureButton newShiftButton =
            new SecureButton("New Shift", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.SHIFT_MANAGE);
    private final HrmsTable shiftTable = HrmsTable.builder(
                    "ID", "Code", "Shift Name", "Time", "Grace", "Break", "Assigned", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 100)
            .fixedColumn(3, 170)
            .badgeColumn(7)
            .onDoubleClick((viewRow, modelRow) -> editSelectedShift())
            .contextMenu(this::buildShiftMenu)
            .build();
    private List<Shift> loadedShifts = List.of();

    // --- assignments tab ---
    private final SecureButton assignButton =
            new SecureButton("Assign Shift", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.SHIFT_ASSIGN);
    private final HrmsTable assignmentTable = HrmsTable.builder(
                    "AID", "Employee ID", "Employee", "Shift", "From", "Until", "Assigned By")
            .hiddenColumn(0)
            .hiddenColumn(1)
            .onDoubleClick((viewRow, modelRow) -> showAssignmentHistory())
            .contextMenu(this::buildAssignmentMenu)
            .build();
    private List<EmployeeShift> loadedAssignments = List.of();

    public ShiftPanel() {
        super(new BorderLayout());
        setOpaque(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Shifts", buildShiftsTab());
        tabs.addTab("Assignments", buildAssignmentsTab());
        add(tabs, BorderLayout.CENTER);

        refreshShifts();
        refreshAssignments();
    }

    private JPanel buildShiftsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 12", "[grow,fill][]"));
        toolbar.setOpaque(false);
        toolbar.add(shiftSearch);
        toolbar.add(newShiftButton);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(shiftTable), BorderLayout.CENTER);

        shiftSearch.onTextChanged(text -> refreshShifts());
        newShiftButton.addActionListener(event -> createNewShift());
        return panel;
    }

    private JPanel buildAssignmentsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20", "[grow,fill][]"));
        toolbar.setOpaque(false);
        javax.swing.JLabel hint =
                new JLabel("One current shift per employee; full history is kept.");
        hint.setForeground(com.ams.hrms.ui.theme.Palette.color(Role.TEXT_MUTED));
        toolbar.add(hint, "growx");
        toolbar.add(assignButton);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(assignmentTable), BorderLayout.CENTER);

        assignButton.addActionListener(event -> openAssignmentDialog(null, null));
        return panel;
    }

    /** Opens the picker; either the employee, the shift, or both may be preselected. */
    public void openAssignmentDialog(Long employeeId, Long shiftId) {
        UiThread.executeAsync("Load picker data",
                () -> new Object[]{
                        ServiceRegistry.employeeService().findAll(
                                new com.ams.hrms.repository.EmployeeRepository.Filter(
                                        "", null, null, null)),
                        ServiceRegistry.shiftService().findAll("")},
                result -> {
                    Object[] parts = (Object[]) result;
                    @SuppressWarnings("unchecked")
                    List<com.ams.hrms.model.Employee> employees =
                            (List<com.ams.hrms.model.Employee>) parts[0];
                    @SuppressWarnings("unchecked")
                    List<Shift> shifts = (List<Shift>) parts[1];

                    AssignmentDialog dialog = new AssignmentDialog(swingWindow(),
                            employees, shifts, employeeId, shiftId);
                    if (dialog.showDialog() == AssignmentDialog.Result.SAVED) {
                        refreshShifts();
                        refreshAssignments();
                    }
                });
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private void refreshShifts() {
        controller.loadShifts(shiftSearch.getText(), rows -> {
            loadedShifts = rows;
            UiThread.executeAsync("Load assignment counts", () -> {
                long[] counts = new long[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    counts[i] = ServiceRegistry.shiftService()
                            .countOpenAssignments(rows.get(i).getId());
                }
                return counts;
            }, counts -> renderShiftRows(rows, counts));
        });
    }

    private void renderShiftRows(List<Shift> rows, long[] counts) {
        List<Object[]> tableRows = new ArrayList<>();
        for (int i = 0; i < rows.size() && i < counts.length; i++) {
            Shift shift = rows.get(i);
            tableRows.add(new Object[]{
                    shift.getId(),
                    shift.getCode(),
                    shift.getName(),
                    timeRange(shift),
                    shift.getGraceMinutes() + " min",
                    shift.getBreakMinutes() + " min",
                    String.valueOf(counts[i]),
                    shift.getStatus()});
        }
        shiftTable.setRows(tableRows);
    }

    static String timeRange(Shift shift) {
        String range = shift.getStartTime() + " - " + shift.getEndTime();
        return shift.isOvernight() ? range + "  (overnight)" : range;
    }

    private void refreshAssignments() {
        controller.loadAssignments(rows -> {
            loadedAssignments = rows;
            List<Object[]> tableRows = new ArrayList<>();
            for (EmployeeShift assignment : rows) {
                tableRows.add(new Object[]{
                        assignment.getId(),
                        assignment.getEmployeeId(),
                        assignment.getEmployeeDisplay(),
                        assignment.getShiftName(),
                        assignment.getEffectiveFrom().toString(),
                        assignment.isCurrent() ? "Current" : assignment.getEffectiveTo().toString(),
                        assignment.getAssignedByName() == null
                                ? "-" : assignment.getAssignedByName()});
            }
            assignmentTable.setRows(tableRows);
        });
    }

    private Shift selectedShift() {
        Object id = shiftTable.selectedValue(0);
        if (id == null) {
            return null;
        }
        for (Shift shift : loadedShifts) {
            if (shift.getId().equals(((Number) id).longValue())) {
                return shift;
            }
        }
        return null;
    }

    private EmployeeShift selectedAssignment() {
        Object id = assignmentTable.selectedValue(0);
        if (id == null) {
            return null;
        }
        Long target = ((Number) id).longValue();
        for (EmployeeShift assignment : loadedAssignments) {
            if (assignment.getId() != null && assignment.getId() == target) {
                return assignment;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Actions: shifts
    // ------------------------------------------------------------------

    private void createNewShift() {
        ShiftDialog dialog = new ShiftDialog(swingWindow(), null);
        if (dialog.showDialog() == ShiftDialog.Result.SAVED) {
            refreshShifts();
        }
    }

    private void editSelectedShift() {
        Shift shift = selectedShift();
        if (shift == null || !SecurityService.can(Permissions.SHIFT_MANAGE)) {
            return;
        }
        ShiftDialog dialog = new ShiftDialog(swingWindow(), shift);
        if (dialog.showDialog() == ShiftDialog.Result.SAVED) {
            refreshShifts();
        }
    }

    private void toggleSelectedShift() {
        Shift shift = selectedShift();
        if (shift == null) {
            return;
        }
        String next = "ACTIVE".equals(shift.getStatus()) ? "INACTIVE" : "ACTIVE";
        String verb = "INACTIVE".equals(next) ? "deactivate" : "activate";
        boolean confirmed = Dialogs.confirm(swingWindow(),
                verb.substring(0, 1).toUpperCase() + verb.substring(1),
                "Are you sure you want to " + verb + " shift '" + shift.getName() + "'?");
        if (!confirmed) {
            return;
        }
        controller.setShiftStatus(shift.getId(), next, () -> {
            refreshShifts();
            refreshAssignments();
        });
    }

    private JPopupMenu buildShiftMenu() {
        JPopupMenu menu = new JPopupMenu();
        Shift shift = selectedShift();

        JMenuItem edit = new JMenuItem("Edit");
        edit.setEnabled(shift != null && SecurityService.can(Permissions.SHIFT_MANAGE));
        edit.addActionListener(event -> editSelectedShift());

        JMenuItem assign = new JMenuItem("Assign Employees...");
        assign.setEnabled(shift != null && SecurityService.can(Permissions.SHIFT_ASSIGN));
        assign.addActionListener(event -> {
            Shift target = selectedShift();
            if (target != null) {
                openAssignmentDialog(null, target.getId());
            }
        });

        menu.add(edit);
        menu.add(assign);

        if (shift != null) {
            boolean active = "ACTIVE".equals(shift.getStatus());
            JMenuItem toggle = new JMenuItem(active ? "Deactivate" : "Activate");
            toggle.setEnabled(SecurityService.can(Permissions.SHIFT_MANAGE));
            toggle.addActionListener(event -> toggleSelectedShift());
            menu.add(toggle);
        }
        return menu;
    }

    // ------------------------------------------------------------------
    // Actions: assignments
    // ------------------------------------------------------------------

    private void endSelectedAssignment() {
        EmployeeShift assignment = selectedAssignment();
        if (assignment == null || !assignment.isCurrent()) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "End Assignment",
                "End '" + assignment.getShiftName() + "' for "
                        + assignment.getEmployeeDisplay() + " as of today?");
        if (!confirmed) {
            return;
        }
        controller.endAssignment(assignment.getId(), LocalDate.now(), () -> {
            refreshShifts();
            refreshAssignments();
        }, error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void showAssignmentHistory() {
        EmployeeShift assignment = selectedAssignment();
        if (assignment == null) {
            return;
        }
        controller.loadHistory(assignment.getEmployeeId(), entries ->
                new AssignmentHistoryDialog(swingWindow(),
                        assignment.getEmployeeDisplay(), entries).setVisible(true));
    }

    private JPopupMenu buildAssignmentMenu() {
        JPopupMenu menu = new JPopupMenu();
        EmployeeShift assignment = selectedAssignment();

        JMenuItem history = new JMenuItem("History");
        history.setEnabled(assignment != null);
        history.addActionListener(event -> showAssignmentHistory());

        JMenuItem end = new JMenuItem("End Assignment");
        end.setEnabled(assignment != null && assignment.isCurrent()
                && SecurityService.can(Permissions.SHIFT_ASSIGN));
        end.addActionListener(event -> endSelectedAssignment());

        JMenuItem reassign = new JMenuItem("Reassign...");
        reassign.setEnabled(assignment != null && SecurityService.can(Permissions.SHIFT_ASSIGN));
        reassign.addActionListener(event -> {
            EmployeeShift target = selectedAssignment();
            if (target != null) {
                openAssignmentDialog(target.getEmployeeId(), null);
            }
        });

        menu.add(history);
        menu.add(end);
        menu.add(reassign);
        return menu;
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
