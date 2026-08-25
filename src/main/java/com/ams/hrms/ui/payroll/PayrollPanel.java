package com.ams.hrms.ui.payroll;

import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JMenuItem;
import javax.swing.JScrollPane;

import com.ams.hrms.component.EmptyStatePanel;
import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.PayrollController;
import com.ams.hrms.repository.PayrollRepository.Period;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.UiThread;

/**
 * Payroll module (spec section 20): period picker, payroll table with status
 * badges, and workflow action buttons (Calculate / Review / Approve / Mark
 * Paid) — each permission-gated.
 */
public class PayrollPanel extends JPanel {

    private final PayrollController controller =
            new PayrollController(ServiceRegistry.payrollService());

    private final JComboBox<PeriodItem> periodCombo = new JComboBox<>();
    private final SecureButton calculateButton =
            new SecureButton("Calculate", "refresh", ModernButton.Variant.SUCCESS,
                    Permissions.PAYROLL_CALCULATE);
    private final SecureButton reviewAllButton =
            new SecureButton("Review All", "check", ModernButton.Variant.OUTLINE,
                    Permissions.PAYROLL_REVIEW);
    private final SecureButton approveAllButton =
            new SecureButton("Approve All", "check", ModernButton.Variant.SUCCESS,
                    Permissions.PAYROLL_APPROVE);
    private final SecureButton markPaidButton =
            new SecureButton("Mark Paid", "payroll", ModernButton.Variant.DANGER,
                    Permissions.PAYROLL_MARK_PAID);

    private final HrmsTable table = HrmsTable.builder(
                    "ID", "Number", "Employee", "Dept", "Period",
                    "Basic", "Gross", "Deductions", "Net", "Currency", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 140)
            .fixedColumn(4, 80)
            .fixedColumn(10, 90)
            .badgeColumn(10)
            .contextMenu(this::buildMenu)
            .build();

    private List<Period> periods = List.of();

    /** Center holder swapped between the table and explanatory empty states. */
    private final JPanel center = new JPanel(new BorderLayout());

    public PayrollPanel() {
        super(new BorderLayout());
        setOpaque(false);

        add(buildToolbar(), BorderLayout.NORTH);
        center.setOpaque(false);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);

        loadPeriods();
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 8",
                "[][grow,fill][][][][][push]"));
        toolbar.setOpaque(false);

        toolbar.add(new JLabel("Period:"));
        toolbar.add(periodCombo, "width 180!");
        toolbar.add(calculateButton);
        toolbar.add(reviewAllButton, "gap 6");
        toolbar.add(approveAllButton, "gap 6");
        toolbar.add(markPaidButton, "gap 6");

        return toolbar;
    }

    private void loadPeriods() {
        controller.loadPeriods(periodsLoaded -> {
            this.periods = periodsLoaded;
            periodCombo.removeAllItems();
            for (var period : periods) {
                periodCombo.addItem(new PeriodItem(period));
            }
            if (periodCombo.getItemCount() > 0) {
                periodCombo.setSelectedIndex(0);
                showTable();
            } else if (SecurityGate.has(Permissions.PAYROLL_CALCULATE)) {
                bootstrapFirstPeriod();
            } else {
                showEmptyState("No payroll period exists yet. Ask an administrator "
                        + "or Finance to run the first calculation.");
            }
            wireAfterLoad();
        });
    }

    /**
     * First visit with no periods at all: create the current month's period
     * and run the initial calculation - in the background, with failures
     * surfaced to the user so the module never looks silently broken.
     */
    private void bootstrapFirstPeriod() {
        LocalDate now = LocalDate.now();
        String expectedName = String.format("%d-%02d", now.getYear(), now.getMonthValue());
        showEmptyState("Preparing the " + expectedName
                + " payroll period (first-time setup)...");
        UiThread.executeAsync("Prepare first payroll period",
                () -> {
                    ServiceRegistry.payrollService().calculate(now.getYear(), now.getMonthValue());
                    return ServiceRegistry.payrollService().allPeriods().stream()
                            .filter(p -> p.periodName().equals(expectedName))
                            .findFirst().orElse(null);
                },
                period -> {
                    if (period == null) {
                        showEmptyState("The first payroll period could not be created. "
                                + "Check the log file for details.");
                    } else {
                        loadPeriods();
                    }
                },
                error -> com.ams.hrms.exception.ErrorHandler.handle(this,
                        error instanceof Exception e ? e : new IllegalStateException(error)));
    }

    private boolean wired;

    private void wireAfterLoad() {
        if (wired) {
            return;
        }
        wired = true;
        periodCombo.addActionListener(event -> refresh());
        calculateButton.addActionListener(event -> calculateCurrent());
        reviewAllButton.addActionListener(event -> bulkTransition("CALCULATED", "REVIEWED"));
        approveAllButton.addActionListener(event -> bulkTransition("REVIEWED", "APPROVED"));
        markPaidButton.addActionListener(event -> bulkTransition("APPROVED", "PAID"));
    }

    private Period selectedPeriod() {
        int index = periodCombo.getSelectedIndex();
        return index < 0 ? null : periods.get(index);
    }

    private void refresh() {
        var period = selectedPeriod();
        if (period == null) {
            table.setRows(List.of());
            return;
        }
        controller.loadPayrolls(period.id(), rows -> {
            List<Object[]> tableRows = new ArrayList<>();
            for (var row : rows) {
                tableRows.add(new Object[]{
                        row.id(),
                        row.payrollNumber(),
                        row.employeeCode() + " - " + row.fullName(),
                        row.departmentName(),
                        row.periodName(),
                        row.basicSalary(),
                        row.grossSalary(),
                        row.totalDeduction(),
                        row.netSalary(),
                        row.currency(),
                        row.status()});
            }
            table.setRows(tableRows);
        });
    }

    private void calculateCurrent() {
        var period = selectedPeriod();
        if (period == null) {
            UiThread.runLater(this::refresh);
            return;
        }
        controller.calculate(period.year(), period.month(), count ->
                UiThread.runLater(() -> {
                    com.ams.hrms.component.Toast.show(swingWindow(),
                            com.ams.hrms.component.Toast.Type.SUCCESS,
                            count + " payroll record(s) calculated");
                    refresh();
                }));
    }

    private void bulkTransition(String fromStatus, String toStatus) {
        var period = selectedPeriod();
        if (period == null) {
            return;
        }
        controller.transitionBulk(period.id(), fromStatus, toStatus,
                () -> refresh(),
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private JPopupMenu buildMenu() {
        JPopupMenu menu = new JPopupMenu();
        Object id = table.selectedValue(0);
        String status = table.selectedValue(10) == null
                ? "" : String.valueOf(table.selectedValue(10));

        JMenuItem review = new JMenuItem("Mark Reviewed");
        review.setEnabled(id != null && "CALCULATED".equals(status)
                && SecurityGate.has(Permissions.PAYROLL_REVIEW));
        review.addActionListener(event -> transitionSingle("REVIEWED"));

        JMenuItem approve = new JMenuItem("Approve");
        approve.setEnabled(id != null && "REVIEWED".equals(status)
                && SecurityGate.has(Permissions.PAYROLL_APPROVE));
        approve.addActionListener(event -> transitionSingle("APPROVED"));

        JMenuItem pay = new JMenuItem("Mark Paid");
        pay.setEnabled(id != null && "APPROVED".equals(status)
                && SecurityGate.has(Permissions.PAYROLL_MARK_PAID));
        pay.addActionListener(event -> transitionSingle("PAID"));

        menu.add(review);
        menu.add(approve);
        menu.add(pay);

        JMenuItem payslip = new JMenuItem("Download Payslip");
        payslip.setEnabled(id != null
                && ("APPROVED".equals(status) || "PAID".equals(status))
                && SecurityGate.has(com.ams.hrms.security.Permissions.PAYSLIP_GENERATE));
        payslip.addActionListener(event -> downloadPayslip());
        menu.addSeparator();
        menu.add(payslip);
        return menu;
    }

    /** Generates and opens a PDF payslip for the selected APPROVED/PAID record. */
    private void downloadPayslip() {
        Object id = table.selectedValue(0);
        if (id == null) {
            return;
        }
        UiThread.executeAsync("Generate payslip",
                () -> ServiceRegistry.payslipService().generatePayslip(
                        ((Number) id).longValue(),
                        java.nio.file.Path.of(System.getProperty("user.home"), "Desktop")),
                path -> com.ams.hrms.component.Toast.show(swingWindow(),
                        com.ams.hrms.component.Toast.Type.SUCCESS,
                        "Payslip saved to Desktop: " + path.getFileName()),
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void transitionSingle(String status) {
        Object id = table.selectedValue(0);
        if (id == null) {
            return;
        }
        controller.transition(((Number) id).longValue(), status,
                this::refresh,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void showTable() {
        center.removeAll();
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.revalidate();
        center.repaint();
    }

    /** Replaces the table with an explanatory empty state. */
    private void showEmptyState(String message) {
        table.setRows(List.of());
        center.removeAll();
        JPanel placeholder = new JPanel(new net.miginfocom.swing.MigLayout(
                "wrap 1, align center center"));
        placeholder.setOpaque(false);
        placeholder.add(new EmptyStatePanel("payroll", "Payroll", message));
        center.add(placeholder, BorderLayout.CENTER);
        center.revalidate();
        center.repaint();
    }

    private record PeriodItem(Period period) {
        @Override
        public String toString() {
            return period.periodName() + " (" + period.status() + ")";
        }
    }

    private static final class SecurityGate {
        static boolean has(com.ams.hrms.security.Permissions p) {
            return com.ams.hrms.security.SecurityService.can(p);
        }
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
