package com.ams.hrms.ui.attendance;

import java.awt.BorderLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.ams.hrms.component.DatePickerField;
import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.SearchField;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.AttendanceController;
import com.ams.hrms.model.AttendanceRecord;
import com.ams.hrms.model.Department;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.UiThread;

/**
 * Attendance module (spec section 16): daily view with check-in/out and
 * corrections, plus the absent/weekend sweep.
 */
public class AttendancePanel extends JPanel {

    private final AttendanceController controller = new AttendanceController(ServiceRegistry.attendanceService());

    private final DatePickerField datePicker = new DatePickerField();
    private final JLabel dateLabel = new JLabel("Date");
    private final SearchField searchField = new SearchField("Search employee...");
    private final JComboBox<String> departmentFilter = new JComboBox<>(new String[] { "All Departments" });
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[] {
            "All Statuses", "PRESENT", "LATE", "EARLY_LEAVE", "HALF_DAY",
            "ABSENT", "WEEKEND", "MISSION" });
    private final SecureButton checkInButton = new SecureButton("Check In", "check", ModernButton.Variant.SUCCESS,
            Permissions.ATTENDANCE_CREATE);
    private final SecureButton checkOutButton = new SecureButton("Check Out", "logout", ModernButton.Variant.PRIMARY,
            Permissions.ATTENDANCE_CREATE);
    private final SecureButton correctButton = new SecureButton("Correct...", "edit", ModernButton.Variant.OUTLINE,
            Permissions.ATTENDANCE_UPDATE);
    private final SecureButton sweepButton = new SecureButton("Mark Absentees", "warning", ModernButton.Variant.GHOST,
            Permissions.ATTENDANCE_CREATE);

    private final HrmsTable table = HrmsTable.builder(
            "ID", "Code", "Employee", "Department", "In", "Out",
            "Status", "Late", "Early", "Worked", "OT")
            .hiddenColumn(0)
            .fixedColumn(1, 95)
            .fixedColumn(4, 80)
            .fixedColumn(5, 80)
            .badgeColumn(6)
            .onDoubleClick((viewRow, modelRow) -> correctSelected())
            .contextMenu(this::buildMenu)
            .build();

    private final List<Department> departmentOptions = new ArrayList<>();
    private List<AttendanceRecord> loaded = List.of();
    private boolean filterEventsAttached;

    public AttendancePanel() {
        super(new BorderLayout());
        setOpaque(false);

        // Two-row toolbar (same pattern as Audit): filters on the first row,
        // actions on the second. One row cannot fit 5 filters + 4 buttons at
        // the minimum window width - the search field collapses and the
        // buttons clip. The date picker and status filter get widths that
        // fit an ISO date and the longest status text without truncation.
        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 14 20 12 20, gapx 10, gapy 8, wrap", "[grow,fill]"));
        toolbar.setOpaque(false);

        dateLabel.setFont(dateLabel.getFont().deriveFont(Font.PLAIN, 12f));
        dateLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        datePicker.setDate(LocalDate.now());

        JPanel filtersRow = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 0, gap 10", "[][fill][grow,fill][fill][fill]"));
        filtersRow.setOpaque(false);
        filtersRow.add(dateLabel);
        filtersRow.add(datePicker, "width 140!");
        filtersRow.add(searchField, "grow");
        filtersRow.add(departmentFilter, "width 180!");
        filtersRow.add(statusFilter, "width 140!");

        JPanel actionsRow = new JPanel(new net.miginfocom.swing.MigLayout("insets 0, gap 10"));
        actionsRow.setOpaque(false);
        actionsRow.add(new JLabel(""), "growx");
        actionsRow.add(checkInButton);
        actionsRow.add(checkOutButton);
        actionsRow.add(correctButton);
        actionsRow.add(sweepButton);

        toolbar.add(filtersRow, "growx");
        toolbar.add(actionsRow);

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        datePicker.addDateChangedListener(event -> refresh());
        // The department filter needs org rights; self-service accounts skip
        // it entirely (their view is scoped to their own rows anyway).
        if (com.ams.hrms.security.SessionContext.has(Permissions.DEPARTMENT_VIEW)) {
            loadDepartments();
        } else {
            filtersRow.remove(departmentFilter);
        }
        attachFilterEvents();
        wireActions();
        refresh();
    }

    /** Re-resolves cached palette colors after a theme switch. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (dateLabel != null) {
            dateLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        }
    }

    private void loadDepartments() {
        UiThread.executeAsync("Load departments",
                () -> ServiceRegistry.departmentService().findAll(""),
                rows -> {
                    for (var d : rows) {
                        if ("ACTIVE".equals(d.getStatus())) {
                            departmentOptions.add(d);
                            departmentFilter.addItem(d.getCode() + " - " + d.getName());
                        }
                    }
                    attachFilterEvents();
                });
    }

    private void attachFilterEvents() {
        if (filterEventsAttached) {
            return;
        }
        filterEventsAttached = true;
        searchField.onTextChanged(text -> refresh());
        departmentFilter.addActionListener(event -> refresh());
        statusFilter.addActionListener(event -> refresh());
    }

    private void wireActions() {
        checkInButton.addActionListener(event -> {
            AttendanceRecord record = selected();
            if (record == null) {
                return;
            }
            controller.checkIn(record.getEmployeeId(), this::refresh);
        });
        checkOutButton.addActionListener(event -> {
            AttendanceRecord record = selected();
            if (record == null || !record.isOpen()) {
                return;
            }
            controller.checkOut(record.getEmployeeId(), this::refresh);
        });
        correctButton.addActionListener(event -> correctSelected());
        sweepButton.addActionListener(event -> generateDaily());
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private void refresh() {
        LocalDate date = datePicker.getDate() == null
                ? LocalDate.now()
                : datePicker.getDate();
        int deptIndex = departmentFilter.getSelectedIndex();
        Long deptId = deptIndex <= 0 ? null : departmentOptions.get(deptIndex - 1).getId();
        String status = statusFilter.getSelectedIndex() <= 0
                ? ""
                : String.valueOf(statusFilter.getSelectedItem());

        controller.loadDay(date, searchField.getText(), deptId, status, rows -> {
            loaded = rows;
            List<Object[]> tableRows = new ArrayList<>();
            for (var r : loaded) {
                tableRows.add(new Object[] {
                        r.getId(),
                        r.getEmployeeCode(),
                        r.getFullName(),
                        r.getDepartmentName() == null ? "-" : r.getDepartmentName(),
                        timeText(r.getCheckIn()),
                        timeText(r.getCheckOut()),
                        r.getStatus(),
                        minutesText(r.getLateMinutes()),
                        minutesText(r.getEarlyLeaveMinutes()),
                        hoursText(r.getWorkedHours()),
                        hoursText(r.getOvertimeHours()) });
            }
            table.setRows(tableRows);
        });
    }

    private AttendanceRecord selected() {
        Object id = table.selectedValue(0);
        if (id == null) {
            return null;
        }
        for (var record : loaded) {
            if (record.getId().equals(((Number) id).longValue())) {
                return record;
            }
        }
        return null;
    }

    private void generateDaily() {
        LocalDate date = datePicker.getDate() == null
                ? LocalDate.now()
                : datePicker.getDate();
        controller.generateDaily(date, created -> {
            if (created > 0) {
                com.ams.hrms.component.Toast.show(swingWindow(),
                        com.ams.hrms.component.Toast.Type.INFO,
                        created + " attendance row(s) generated");
            } else {
                com.ams.hrms.component.Toast.show(swingWindow(),
                        com.ams.hrms.component.Toast.Type.INFO,
                        "Every active employee already has a record");
            }
            refresh();
        });
    }

    private void correctSelected() {
        AttendanceRecord record = selected();
        if (record == null || !SecurityGate.canCorrect()) {
            return;
        }
        CorrectionDialog dialog = new CorrectionDialog(swingWindow(), controller, record);
        if (dialog.showDialog() == CorrectionDialog.Result.SAVED) {
            refresh();
        }
    }

    /** Small gate so the panel does not import SecurityService directly twice. */
    private static final class SecurityGate {
        static boolean canCorrect() {
            return com.ams.hrms.security.SecurityService.can(
                    com.ams.hrms.security.Permissions.ATTENDANCE_UPDATE);
        }
    }

    private JPopupMenu buildMenu() {
        JPopupMenu menu = new JPopupMenu();
        var record = selected();

        JMenuItem correct = new JMenuItem("Correct...");
        correct.setEnabled(record != null && SecurityGate.canCorrect());
        correct.addActionListener(event -> correctSelected());

        JMenuItem history = new JMenuItem("Monthly View");
        history.setEnabled(record != null);
        history.addActionListener(event -> MonthlyViewDialog.openFor(swingWindow(), controller,
                record.getEmployeeId(),
                record.getEmployeeCode() + " - " + record.getFullName()));

        menu.add(correct);
        menu.add(history);
        return menu;
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }

    private static String timeText(java.time.LocalTime time) {
        return time == null ? "-" : time.toString();
    }

    private static String minutesText(int value) {
        return value <= 0 ? "-" : value + " m";
    }

    private static String hoursText(java.math.BigDecimal value) {
        return value == null || value.signum() == 0 ? "-" : value.toPlainString();
    }
}
