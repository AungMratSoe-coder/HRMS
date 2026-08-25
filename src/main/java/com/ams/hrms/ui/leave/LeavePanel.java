package com.ams.hrms.ui.leave;

import java.awt.BorderLayout;
import java.time.LocalDate;
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
import com.ams.hrms.controller.LeaveController;
import com.ams.hrms.model.EmployeeLeaveRequest;
import com.ams.hrms.repository.LeaveRepository.LeaveTypeOption;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

/**
 * Leave module (spec section 18): requests tab with approval workflow and a
 * balances tab per employee/year.
 */
public class LeavePanel extends JPanel {

    private final LeaveController controller =
            new LeaveController(ServiceRegistry.leaveService());

    // --- requests tab ---
    private final SearchField searchField = new SearchField("Search employee or code...");
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[]{
            "All Statuses", "PENDING", "APPROVED", "REJECTED", "CANCELLED"});
    private final SecureButton newRequestButton =
            new SecureButton("New Request", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.LEAVE_REQUEST);
    private final HrmsTable requestTable = HrmsTable.builder(
                    "ID", "Code", "Employee", "Type", "From", "To", "Days",
                    "Status", "Decided By")
            .hiddenColumn(0)
            .fixedColumn(1, 80)
            .fixedColumn(4, 95)
            .fixedColumn(5, 95)
            .badgeColumn(7)
            .contextMenu(this::buildRequestMenu)
            .build();
    private List<EmployeeLeaveRequest> loadedRequests = List.of();

    // --- balances tab ---
    private final JComboBox<String> balanceEmployeeCombo = new JComboBox<>();
    private final javax.swing.JSpinner balanceYearSpinner =
            new javax.swing.JSpinner(new javax.swing.SpinnerNumberModel(
                    LocalDate.now().getYear(), 2000, 2100, 1));
    private final List<Long> balanceEmployeeIds = new ArrayList<>();
    private final HrmsTable balanceTable = HrmsTable.builder(
                    "Type", "Entitled", "Carried", "Used", "Pending", "Adjusted", "Available")
            .fixedColumn(0, 170)
            .build();

    public LeavePanel() {
        super(new BorderLayout());
        setOpaque(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Requests", buildRequestsTab());
        tabs.addTab("Balances", buildBalancesTab());
        add(tabs, BorderLayout.CENTER);

        loadBalanceEmployees();
        refreshRequests();
        refreshBalances();
    }

    private JPanel buildRequestsTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10", "[grow,fill][][][fill][]"));
        toolbar.setOpaque(false);
        toolbar.add(searchField);
        toolbar.add(statusFilter, "width 130!");
        toolbar.add(new JLabel(""), "width 4");
        toolbar.add(newRequestButton);

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(requestTable), BorderLayout.CENTER);

        searchField.onTextChanged(text -> refreshRequests());
        statusFilter.addActionListener(event -> refreshRequests());
        newRequestButton.addActionListener(event -> openRequestDialog());
        return panel;
    }

    private JPanel buildBalancesTab() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10"));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("Employee:"), "gapright 6");
        toolbar.add(balanceEmployeeCombo, "width 260!");
        toolbar.add(new JLabel("Year:"), "gapright 6");
        toolbar.add(balanceYearSpinner, "width 80!");

        panel.add(toolbar, BorderLayout.NORTH);
        panel.add(new JScrollPane(balanceTable), BorderLayout.CENTER);

        balanceEmployeeCombo.addActionListener(event -> refreshBalances());
        balanceYearSpinner.addChangeListener(event -> refreshBalances());
        return panel;
    }

    /** Active employees feed the Balances tab picker. */
    private void loadBalanceEmployees() {
        UiThread.executeAsync("Load balance employees",
                () -> ServiceRegistry.employeeService().findAll(
                        new com.ams.hrms.repository.EmployeeRepository.Filter(
                                "", null, null, null)),
                employees -> {
                    balanceEmployeeCombo.removeAllItems();
                    balanceEmployeeIds.clear();
                    for (var employee : employees) {
                        if ("ACTIVE".equals(employee.getStatus())) {
                            balanceEmployeeIds.add(employee.getId());
                            balanceEmployeeCombo.addItem(
                                    employee.getCode() + " - " + employee.getFullName());
                        }
                    }
                    if (balanceEmployeeCombo.getItemCount() > 0) {
                        balanceEmployeeCombo.setSelectedIndex(0);
                    }
                });
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private void refreshRequests() {
        String status = statusFilter.getSelectedIndex() <= 0
                ? "" : String.valueOf(statusFilter.getSelectedItem());
        controller.loadRequests(searchField.getText(), status, null, rows -> {
            loadedRequests = rows;
            List<Object[]> tableRows = new ArrayList<>();
            for (var request : rows) {
                tableRows.add(new Object[]{
                        request.getId(),
                        request.getLeaveCode(),
                        request.getEmployeeCode() + " - " + request.getFullName(),
                        request.getTypeName(),
                        request.getStartDate().toString(),
                        request.getEndDate().toString(),
                        request.getNumberOfDays().toPlainString(),
                        request.getStatus(),
                        request.getDecidedByName() == null ? "-" : request.getDecidedByName()});
            }
            requestTable.setRows(tableRows);
        });
    }

    private void refreshBalances() {
        int index = balanceEmployeeCombo.getSelectedIndex();
        int year = (int) balanceYearSpinner.getValue();
        if (index < 0 || index >= balanceEmployeeIds.size()) {
            balanceTable.setRows(List.of());
            return;
        }
        long employeeId = balanceEmployeeIds.get(index);
        controller.loadBalances(employeeId, year, rows -> {
            List<Object[]> tableRows = new ArrayList<>();
            for (var row : rows) {
                tableRows.add(new Object[]{
                        row.typeName(),
                        row.entitled().toPlainString(),
                        row.carriedForward().toPlainString(),
                        row.used().toPlainString(),
                        row.pending().toPlainString(),
                        row.adjusted().toPlainString(),
                        row.available().toPlainString() + " day(s)"});
            }
            balanceTable.setRows(tableRows);
        });
    }

    private EmployeeLeaveRequest selectedRequest() {
        Object id = requestTable.selectedValue(0);
        if (id == null) {
            return null;
        }
        Long target = ((Number) id).longValue();
        for (var request : loadedRequests) {
            if (request.getId() != null && request.getId() == target) {
                return request;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void openRequestDialog() {
        UiThread.executeAsync("Load request dialog data",
                () -> new Object[]{
                        ServiceRegistry.employeeService().findAll(
                                new com.ams.hrms.repository.EmployeeRepository.Filter(
                                        "", null, null, null)),
                        ServiceRegistry.leaveService().activeTypes()},
                result -> {
                    Object[] parts = (Object[]) result;
                    @SuppressWarnings("unchecked")
                    List<com.ams.hrms.model.Employee> employees =
                            (List<com.ams.hrms.model.Employee>) parts[0];
                    @SuppressWarnings("unchecked")
                    List<LeaveTypeOption> types = (List<LeaveTypeOption>) parts[1];

                    LeaveRequestDialog dialog =
                            new LeaveRequestDialog(swingWindow(), employees, types);
                    if (dialog.showDialog() == LeaveRequestDialog.Result.SAVED) {
                        refreshRequests();
                        refreshBalances();
                    }
                });
    }

    private void approveSelected(String level) {
        EmployeeLeaveRequest request = selectedRequest();
        if (request == null || !request.isPending()) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Approve",
                "Approve leave " + request.getLeaveCode() + " ("
                        + request.getNumberOfDays().toPlainString() + " day(s), "
                        + request.getTypeName() + ") at " + level + " level?");
        if (!confirmed) {
            return;
        }
        controller.approve(request.getId(), level, null, () -> {
            refreshRequests();
            refreshBalances();
        }, error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void rejectSelected() {
        EmployeeLeaveRequest request = selectedRequest();
        if (request == null || !request.isPending()) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Reject",
                "Reject leave " + request.getLeaveCode() + "?");
        if (!confirmed) {
            return;
        }
        controller.reject(request.getId(), "Rejected from leave module", () -> refreshRequests(),
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void cancelSelected() {
        EmployeeLeaveRequest request = selectedRequest();
        if (request == null || !request.isPending()) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Cancel",
                "Cancel pending leave " + request.getLeaveCode() + "?");
        if (!confirmed) {
            return;
        }
        controller.cancel(request.getId(), () -> refreshRequests(),
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private JPopupMenu buildRequestMenu() {
        JPopupMenu menu = new JPopupMenu();
        var request = selectedRequest();
        boolean scopedManager = com.ams.hrms.service.ApprovalScope.isScopedManager(
                com.ams.hrms.security.SessionContext.roles().stream()
                        .map(com.ams.hrms.security.SessionContext.RoleRef::code)
                        .collect(java.util.stream.Collectors.toSet()));

        JMenuItem approveManager = new JMenuItem("Approve (Manager)");
        approveManager.setEnabled(request != null && request.isPending()
                && SecurityService.can(Permissions.LEAVE_APPROVE));
        approveManager.addActionListener(event -> approveSelected("MANAGER"));

        JMenuItem approveHr = new JMenuItem("Approve (HR - Final)");
        approveHr.setEnabled(request != null && request.isPending()
                && SecurityService.can(Permissions.LEAVE_APPROVE)
                && !scopedManager);
        approveHr.setToolTipText(scopedManager
                ? "Final approval is reserved for HR, Finance and Super Admin accounts."
                : null);
        approveHr.addActionListener(event -> approveSelected("HR"));

        JMenuItem reject = new JMenuItem("Reject");
        reject.setEnabled(request != null && request.isPending()
                && SecurityService.can(Permissions.LEAVE_APPROVE));
        reject.addActionListener(event -> rejectSelected());

        JMenuItem cancel = new JMenuItem("Cancel Request");
        cancel.setEnabled(request != null && request.isPending());
        cancel.addActionListener(event -> cancelSelected());

        menu.add(approveManager);
        menu.add(approveHr);
        menu.add(reject);
        menu.add(cancel);
        return menu;
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
