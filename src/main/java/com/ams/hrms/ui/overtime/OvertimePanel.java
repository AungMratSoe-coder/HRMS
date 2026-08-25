package com.ams.hrms.ui.overtime;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JComboBox;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;

import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.SearchField;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.OvertimeController;
import com.ams.hrms.model.OvertimeRequest;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

/**
 * Overtime module (spec section 19): request list with approval workflow.
 * Rate/amount columns show values once approved (snapshot at approval).
 */
public class OvertimePanel extends JPanel {

    private final OvertimeController controller =
            new OvertimeController(ServiceRegistry.overtimeService());

    private final SearchField searchField = new SearchField("Search employee or code...");
    private final JComboBox<String> statusFilter = new JComboBox<>(new String[]{
            "All Statuses", "PENDING", "APPROVED", "REJECTED"});
    private final SecureButton requestButton =
            new SecureButton("Request Overtime", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.OVERTIME_REQUEST);
    private final HrmsTable table = HrmsTable.builder(
                    "ID", "Code", "Employee", "Date", "Hours", "Rate/h",
                    "Amount", "Status", "Approved By")
            .hiddenColumn(0)
            .fixedColumn(1, 85)
            .fixedColumn(3, 95)
            .badgeColumn(7)
            .contextMenu(this::buildMenu)
            .build();

    private List<OvertimeRequest> loaded = List.of();

    public OvertimePanel() {
        super(new BorderLayout());
        setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10", "[grow,fill][][][fill][]"));
        toolbar.setOpaque(false);
        toolbar.add(searchField);
        toolbar.add(statusFilter, "width 130!");
        toolbar.add(new javax.swing.JLabel(""), "width 4");
        toolbar.add(requestButton);

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        searchField.onTextChanged(text -> refresh());
        statusFilter.addActionListener(event -> refresh());
        requestButton.addActionListener(event -> openRequestDialog());
        refresh();
    }

    private void refresh() {
        String status = statusFilter.getSelectedIndex() <= 0
                ? "" : String.valueOf(statusFilter.getSelectedItem());
        controller.load(searchField.getText(), status, rows -> {
            loaded = rows;
            List<Object[]> tableRows = new ArrayList<>();
            for (var request : rows) {
                tableRows.add(new Object[]{
                        request.getId(),
                        request.getOvertimeCode(),
                        request.getEmployeeCode() + " - " + request.getFullName(),
                        request.getRequestDate().toString(),
                        request.getHours().toPlainString(),
                        request.getRatePerHour() == null ? "-"
                                : request.getRatePerHour().toPlainString(),
                        request.getAmount() == null ? "-" : request.getAmount().toPlainString(),
                        request.getStatus(),
                        request.getApprovedByName() == null ? "-" : request.getApprovedByName()});
            }
            table.setRows(tableRows);
        });
    }

    private OvertimeRequest selected() {
        Object id = table.selectedValue(0);
        if (id == null) {
            return null;
        }
        Long target = ((Number) id).longValue();
        for (var request : loaded) {
            if (request.getId() != null && request.getId() == target) {
                return request;
            }
        }
        return null;
    }

    private void openRequestDialog() {
        UiThread.executeAsync("Load employees",
                () -> ServiceRegistry.employeeService().findAll(
                        new com.ams.hrms.repository.EmployeeRepository.Filter(
                                "", null, null, null)),
                employees -> {
                    OvertimeRequestDialog dialog =
                            new OvertimeRequestDialog(swingWindow(), employees);
                    if (dialog.showDialog() == OvertimeRequestDialog.Result.SAVED) {
                        refresh();
                    }
                });
    }

    private void approveSelected() {
        OvertimeRequest request = selected();
        if (request == null || !request.isPending()) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Approve Overtime",
                "Approve overtime " + request.getOvertimeCode() + " ("
                        + request.getHours().toPlainString() + "h on "
                        + request.getRequestDate() + ")?");
        if (!confirmed) {
            return;
        }
        controller.approve(request.getId(), () -> refresh(),
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void rejectSelected() {
        OvertimeRequest request = selected();
        if (request == null || !request.isPending()) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Reject Overtime",
                "Reject overtime " + request.getOvertimeCode() + "?");
        if (!confirmed) {
            return;
        }
        controller.reject(request.getId(), () -> refresh(),
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private JPopupMenu buildMenu() {
        JPopupMenu menu = new JPopupMenu();
        var request = selected();

        JMenuItem approve = new JMenuItem("Approve");
        approve.setEnabled(request != null && request.isPending()
                && SecurityService.can(Permissions.OVERTIME_APPROVE));
        approve.addActionListener(event -> approveSelected());

        JMenuItem reject = new JMenuItem("Reject");
        reject.setEnabled(request != null && request.isPending()
                && SecurityService.can(Permissions.OVERTIME_APPROVE));
        reject.addActionListener(event -> rejectSelected());

        menu.add(approve);
        menu.add(reject);
        return menu;
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
