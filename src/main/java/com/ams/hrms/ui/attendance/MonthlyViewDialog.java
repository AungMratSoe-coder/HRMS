package com.ams.hrms.ui.attendance;

import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.controller.AttendanceController;
import com.ams.hrms.model.AttendanceRecord;
import com.ams.hrms.repository.EmployeeRepository;
import com.ams.hrms.ui.theme.Palette.Role;

import net.miginfocom.swing.MigLayout;

/**
 * Monthly attendance view for one employee (spec section 16): day-by-day
 * rows plus a totals line.
 */
public class MonthlyViewDialog extends JDialog {

    private final AttendanceController controller;
    private final long employeeId;
    private final JComboBox<String> monthCombo;
    private final javax.swing.JSpinner yearSpinner;
    private final HrmsTable table = HrmsTable.builder(
                    "Day", "Status", "In", "Out", "Late", "Early", "Worked", "OT")
            .fixedColumn(0, 70)
            .badgeColumn(1)
            .build();
    private final JLabel totalsLabel = new JLabel();

    public static void openFor(java.awt.Window owner, AttendanceController controller,
                               long employeeId, String employeeLabel) {
        new MonthlyViewDialog(owner, controller, employeeId, employeeLabel).setVisible(true);
    }

    private MonthlyViewDialog(java.awt.Window owner, AttendanceController controller,
                              long employeeId, String employeeLabel) {
        super(owner, "Monthly Attendance - " + employeeLabel,
                ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.employeeId = employeeId;

        LocalDate today = LocalDate.now();
        setLayout(new BorderLayout());

        JPanel top = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 8 20, gap 10"));
        top.setOpaque(false);
        monthCombo = new JComboBox<>(new String[]{
                "January", "February", "March", "April", "May", "June", "July",
                "August", "September", "October", "November", "December"});
        monthCombo.setSelectedIndex(today.getMonthValue() - 1);
        yearSpinner = new javax.swing.JSpinner(
                new javax.swing.SpinnerNumberModel(today.getYear(), 2000, 2100, 1));
        top.add(new JLabel("Month:"));
        top.add(monthCombo, "width 140!");
        top.add(new JLabel("Year:"));
        top.add(yearSpinner, "width 80!");
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        center.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new net.miginfocom.swing.MigLayout("insets 8 20 14 20"));
        bottom.setOpaque(false);
        totalsLabel.setFont(totalsLabel.getFont().deriveFont(java.awt.Font.BOLD, 12f));
        totalsLabel.setForeground(com.ams.hrms.ui.theme.Palette.color(Role.TEXT));
        bottom.add(totalsLabel);
        center.add(bottom, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        setSize(720, 520);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        monthCombo.addActionListener(event -> load());
        yearSpinner.addChangeListener(event -> load());
        load();
    }

    private void load() {
        int month = monthCombo.getSelectedIndex() + 1;
        int year = (int) yearSpinner.getValue();
        controller.loadMonth(employeeId, year, month, records -> {
            render(records, year, month);
        }, summary -> {
            totalsLabel.setText(String.format(
                    "Present %d · Late %d · Early %d · Half-day %d · Absent %d · Worked %.2f h · Overtime %.2f h",
                    summary.present(), summary.late(), summary.earlyLeave(),
                    summary.halfDay(), summary.absent(),
                    summary.totalWorked().doubleValue(),
                    summary.totalOvertime().doubleValue()));
        });
    }

    private void render(List<AttendanceRecord> records, int year, int month) {
        List<Object[]> rows = new ArrayList<>();
        var byDate = new java.util.HashMap<LocalDate, AttendanceRecord>();
        for (var record : records) {
            byDate.put(record.getAttendanceDate(), record);
        }
        LocalDate first = LocalDate.of(year, month, 1);
        int days = first.lengthOfMonth();
        for (int day = 1; day <= days; day++) {
            LocalDate date = first.withDayOfMonth(day);
            var record = byDate.get(date);
            if (record == null) {
                rows.add(new Object[]{String.valueOf(day), "-", "-", "-",
                        "-", "-", "-", "-"});
            } else {
                rows.add(new Object[]{
                        String.valueOf(day),
                        record.getStatus(),
                        time(record.getCheckIn()),
                        time(record.getCheckOut()),
                        minutes(record.getLateMinutes()),
                        minutes(record.getEarlyLeaveMinutes()),
                        hours(record.getWorkedHours()),
                        hours(record.getOvertimeHours())});
            }
        }
        table.setRows(rows);
    }

    private static String time(java.time.LocalTime t) {
        return t == null ? "-" : t.toString();
    }

    private static String minutes(int m) {
        return m <= 0 ? "-" : m + "m";
    }

    private static String hours(java.math.BigDecimal h) {
        return h == null || h.signum() == 0 ? "-" : h.toPlainString();
    }
}
