package com.ams.hrms.ui.employee;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JComboBox;

import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.PaginationPanel;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.SearchField;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.EmployeeController;
import com.ams.hrms.model.Department;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.Position;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

/**
 * Employees module screen (spec section 10): search + department/position/
 * status filters, server-side pagination (spec sections 37 and 44),
 * permission-gated creation, context
 * menu (edit / activate / deactivate / history).
 */
public class EmployeeListPanel extends JPanel {

    private final EmployeeController controller =
            new EmployeeController(ServiceRegistry.employeeService());

    private final SearchField searchField = new SearchField("Search by code, name or phone...");
    private final JComboBox<String> departmentFilter =
            new JComboBox<>(new String[]{"All Departments"});
    private final JComboBox<String> positionFilter =
            new JComboBox<>(new String[]{"All Positions"});
    private final JComboBox<String> statusFilter =
            new JComboBox<>(new String[]{"All Statuses", "ACTIVE", "INACTIVE"});
    /** Filter row panel, kept so org filters can be removed when unused. */
    private JPanel filtersRow;
    private final SecureButton newButton =
            new SecureButton("New Employee", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.EMPLOYEE_CREATE);
    private final PaginationPanel pagination = new PaginationPanel();

    private final HrmsTable table = HrmsTable.builder(
                    "ID", "Code", "Full Name", "Department", "Position", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 100)
            .badgeColumn(5)
            .onDoubleClick((viewRow, modelRow) -> showProfile())
            .contextMenu(this::buildContextMenu)
            .build();

    private final List<Department> departmentOptions = new ArrayList<>();
    private final List<Position> positionOptions = new ArrayList<>();
    private List<Employee> loaded = List.of();
    private boolean filterEventsAttached;

    /** Reloads the table when any module changes employee data (e.g. a hire from Recruitment). */
    private final Consumer<Events.DataChanged> dataListener = event -> {
        if (com.ams.hrms.service.EmployeeService.DATA_SCOPE.equals(event.scope())) {
            resetToFirstPageAndRefresh();
        }
    };

    public EmployeeListPanel() {
        super(new BorderLayout());
        setOpaque(false);

        add(buildToolbar(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(pagination, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        wirePagination();
        newButton.addActionListener(event -> createNew());
        loadFilterOptions();
        refresh();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EventBus.subscribe(Events.DataChanged.class, dataListener);
    }

    @Override
    public void removeNotify() {
        EventBus.unsubscribe(Events.DataChanged.class, dataListener);
        super.removeNotify();
    }

    /** True when the session may read org structure for the filter combos. */
    private static boolean canUseOrgFilters() {
        return com.ams.hrms.security.SessionContext.has(Permissions.DEPARTMENT_VIEW)
                && com.ams.hrms.security.SessionContext.has(Permissions.POSITION_VIEW);
    }

    // ------------------------------------------------------------------
    // Toolbar & filters
    // ------------------------------------------------------------------

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "wrap 2, insets 16 20 10 20, gap 10",
                "[grow,fill][fill]",
                "[grow,fill][]"));

        JPanel filtersRow = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 0, gap 10", "[grow,fill][][][]"));
        filtersRow.setOpaque(false);
        filtersRow.add(searchField);
        filtersRow.add(departmentFilter, "width 190!");
        filtersRow.add(positionFilter, "width 190!");
        filtersRow.add(statusFilter, "width 130!");
        this.filtersRow = filtersRow;

        toolbar.add(filtersRow, "growx");
        toolbar.add(newButton, "aligny top");
        return toolbar;
    }

    /** Filter options load once; combos react after the initial fill. */
    private void loadFilterOptions() {
        // Org-based filters need org rights; self-service accounts (whose
        // list contains only their own row) skip them entirely.
        if (!canUseOrgFilters()) {
            filtersRow.remove(departmentFilter);
            filtersRow.remove(positionFilter);
            filtersRow.revalidate();
            filtersRow.repaint();
            attachFilterEvents();
            return;
        }
        UiThread.executeAsync("Load employee filters",
                () -> new Object[]{
                        ServiceRegistry.departmentService().findAll(""),
                        ServiceRegistry.positionService().findAll("")},
                result -> {
                    Object[] parts = (Object[]) result;
                    @SuppressWarnings("unchecked")
                    List<Department> departments = (List<Department>) parts[0];
                    @SuppressWarnings("unchecked")
                    List<Position> positions = (List<Position>) parts[1];

                    for (var d : departments) {
                        if ("ACTIVE".equals(d.getStatus())) {
                            departmentOptions.add(d);
                            departmentFilter.addItem(d.getCode() + " - " + d.getName());
                        }
                    }
                    for (var p : positions) {
                        if ("ACTIVE".equals(p.getStatus())) {
                            positionOptions.add(p);
                            positionFilter.addItem(p.getCode() + " - " + p.getName());
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
        departmentFilter.addActionListener(event -> resetToFirstPageAndRefresh());
        positionFilter.addActionListener(event -> resetToFirstPageAndRefresh());
        statusFilter.addActionListener(event -> resetToFirstPageAndRefresh());
    }

    // ------------------------------------------------------------------
    // Data + pagination
    // ------------------------------------------------------------------

    private void resetToFirstPageAndRefresh() {
        pagination.reset();
        refresh();
    }

    private void refresh() {
        controller.loadPage(currentFilter(), 1, pagination.pageSize(), result -> {
            loaded = result.rows();
            renderRows(loaded);
            pagination.updateTotal(
                    (int) Math.min(result.totalMatching(), Integer.MAX_VALUE));
        });
    }

    private com.ams.hrms.repository.EmployeeRepository.Filter currentFilter() {
        int deptIndex = departmentFilter.getSelectedIndex();
        int posIndex = positionFilter.getSelectedIndex();
        String status = statusFilter.getSelectedIndex() <= 0
                ? "" : String.valueOf(statusFilter.getSelectedItem());
        return new com.ams.hrms.repository.EmployeeRepository.Filter(
                searchField.getText(),
                deptIndex <= 0 ? null : departmentOptions.get(deptIndex - 1).getId(),
                posIndex <= 0 ? null : positionOptions.get(posIndex - 1).getId(),
                status);
    }

    private void wirePagination() {
        pagination.onPageChange(page -> loadPage());
        pagination.onPageSizeChange(size -> refresh());
    }

    /** Fetches only the requested page from the database. */
    private void loadPage() {
        controller.loadPage(currentFilter(), pagination.currentPage(),
                pagination.pageSize(), result -> {
                    loaded = result.rows();
                    renderRows(loaded);
                });
    }

    private void renderRows(List<Employee> employees) {
        List<Object[]> rows = new ArrayList<>();
        for (Employee employee : employees) {
            rows.add(new Object[]{
                    employee.getId(),
                    employee.getCode(),
                    employee.getFullName(),
                    employee.getDepartmentName() == null ? "-" : employee.getDepartmentName(),
                    employee.getPositionName() == null ? "-" : employee.getPositionName(),
                    employee.getStatus()});
        }
        table.setRows(rows);
    }

    private Employee selectedEmployee() {
        Object id = table.selectedValue(0);
        if (id == null) {
            return null;
        }
        for (Employee employee : loaded) {
            if (employee.getId().equals(((Number) id).longValue())) {
                return employee;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void showProfile() {
        Employee employee = selectedEmployee();
        if (employee == null) {
            return;
        }
        new EmployeeProfileDialog(swingWindow(), employee).setVisible(true);
    }

    private void createNew() {
        UiThread.executeAsync("Load dialog data", () -> dialogData(), result -> {
            var parts = (Object[]) result;
            openDialog(null, parts);
        });
    }

    private void editSelected() {
        Employee employee = selectedEmployee();
        if (employee == null || !SecurityService.can(Permissions.EMPLOYEE_UPDATE)) {
            return;
        }
        UiThread.executeAsync("Load dialog data", () -> dialogData(), result -> {
            var parts = (Object[]) result;
            openDialog(employee, parts);
        });
    }

    private Object[] dialogData() {
        return new Object[]{
                ServiceRegistry.departmentService().findAll(""),
                ServiceRegistry.positionService().findAll(""),
                toManagerOptions(ServiceRegistry.employeeService().findAll(new com.ams.hrms.repository.EmployeeRepository.Filter("", null, null, null)))};
    }

    private void openDialog(Employee existing, Object[] parts) {
        @SuppressWarnings("unchecked")
        List<Department> departments = (List<Department>) parts[0];
        @SuppressWarnings("unchecked")
        List<Position> positions = (List<Position>) parts[1];
        @SuppressWarnings("unchecked")
        List<EmployeeDialog.ManagerOption> managers =
                (List<EmployeeDialog.ManagerOption>) parts[2];

        EmployeeDialog dialog = new EmployeeDialog(swingWindow(), existing,
                departments, positions, managers);
        if (dialog.showDialog() == EmployeeDialog.Result.SAVED) {
            refresh();
        }
    }

    private static List<EmployeeDialog.ManagerOption> toManagerOptions(List<Employee> employees) {
        List<EmployeeDialog.ManagerOption> options = new ArrayList<>();
        for (Employee candidate : employees) {
            if ("ACTIVE".equals(candidate.getStatus())) {
                options.add(new EmployeeDialog.ManagerOption(candidate.getId(),
                        candidate.getCode() + " - " + candidate.getFullName()));
            }
        }
        return options;
    }

    private void toggleSelected() {
        Employee employee = selectedEmployee();
        if (employee == null) {
            return;
        }
        String next = "ACTIVE".equals(employee.getStatus()) ? "INACTIVE" : "ACTIVE";
        String verb = "INACTIVE".equals(next) ? "deactivate" : "activate";
        boolean confirmed = Dialogs.confirm(swingWindow(),
                verb.substring(0, 1).toUpperCase() + verb.substring(1),
                "Are you sure you want to " + verb + " employee '"
                        + employee.getFullName() + "'?");
        if (!confirmed) {
            return;
        }
        controller.setStatus(employee.getId(), next, this::refresh);
    }

    private void showHistory() {
        Employee employee = selectedEmployee();
        if (employee == null) {
            return;
        }
        controller.loadHistory(employee.getId(), entries ->
                new HistoryDialog(swingWindow(), entries,
                        employee.getCode() + " - " + employee.getFullName()).setVisible(true));
    }

    private JPopupMenu buildContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        Employee employee = selectedEmployee();

        JMenuItem profile = new JMenuItem("View Profile");
        profile.setEnabled(employee != null);
        profile.addActionListener(event -> showProfile());

        JMenuItem edit = new JMenuItem("Edit");
        edit.setEnabled(employee != null && SecurityService.can(Permissions.EMPLOYEE_UPDATE));
        edit.addActionListener(event -> editSelected());

        JMenuItem history = new JMenuItem("View History");
        history.setEnabled(employee != null);
        history.addActionListener(event -> showHistory());

        menu.add(profile);
        menu.add(edit);
        menu.add(history);

        if (employee != null) {
            boolean active = "ACTIVE".equals(employee.getStatus());
            JMenuItem toggle = new JMenuItem(active ? "Deactivate" : "Activate");
            toggle.setEnabled(SecurityService.can(Permissions.EMPLOYEE_UPDATE));
            toggle.addActionListener(event -> toggleSelected());
            menu.add(toggle);
        }
        return menu;
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
