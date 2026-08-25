package com.ams.hrms.ui.payroll;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.ams.hrms.component.EmptyStatePanel;
import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.repository.PayrollRepository.PayrollRow;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.util.UiThread;

import net.miginfocom.swing.MigLayout;

/**
 * Self-service payslips (PAYSLIP_VIEW): the signed-in employee sees their own
 * payroll history - period, gross, deductions, net and payment status.
 * Read-only by design; payslip PDFs remain an HR/finance task.
 */
public class MyPayslipsPanel extends JPanel {

    private final HrmsTable table = HrmsTable.builder(
                    "ID", "Period", "Payroll Number", "Basic", "Gross",
                    "Deductions", "Net", "Currency", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 110)
            .fixedColumn(2, 170)
            .badgeColumn(8)
            .build();

    /** Center holder swapped between the table and explanatory empty states. */
    private final JPanel center = new JPanel(new BorderLayout());

    public MyPayslipsPanel() {
        super(new BorderLayout());
        setOpaque(false);

        JPanel toolbar = new JPanel(new MigLayout("insets 14 18, gap 8"));
        toolbar.setOpaque(false);
        ModernButton refreshButton = new ModernButton("Refresh", ModernButton.Variant.OUTLINE);
        refreshButton.addActionListener(event -> refresh());
        toolbar.add(refreshButton);

        center.setOpaque(false);
        add(toolbar, BorderLayout.NORTH);
        add(center, BorderLayout.CENTER);
        refresh();
    }

    private void refresh() {
        Long employeeId = SessionContext.currentEmployeeId();
        if (employeeId == null) {
            // Unlinked account: nothing to resolve against.
            showEmptyState("Your account is not linked to an employee record yet. "
                    + "Ask an administrator to link it (Settings > User Accounts).");
            return;
        }
        UiThread.executeAsync("Load my payslips",
                () -> ServiceRegistry.payrollService().findByEmployee(employeeId),
                this::applyRows,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void applyRows(List<PayrollRow> rows) {
        if (rows.isEmpty()) {
            showEmptyState("No payslips yet - they appear once payroll is processed.");
            return;
        }
        List<Object[]> tableRows = new ArrayList<>();
        for (PayrollRow row : rows) {
            tableRows.add(new Object[]{
                    row.id(),
                    row.periodName(),
                    row.payrollNumber(),
                    row.basicSalary(),
                    row.grossSalary(),
                    row.totalDeduction(),
                    row.netSalary(),
                    row.currency(),
                    row.status()});
        }
        table.setRows(tableRows);
        showTable();
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
        JPanel placeholder = new JPanel(new MigLayout("wrap 1, align center center"));
        placeholder.setOpaque(false);
        placeholder.add(new EmptyStatePanel("payroll", "Payslips", message));
        center.add(placeholder, BorderLayout.CENTER);
        center.revalidate();
        center.repaint();
    }
}
