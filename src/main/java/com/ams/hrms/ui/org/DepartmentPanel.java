package com.ams.hrms.ui.org;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;

import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.SearchField;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.DepartmentController;
import com.ams.hrms.model.Department;
import com.ams.hrms.repository.EmployeeRepository;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.util.Dialogs;

/**
 * Departments module screen (spec section 12): searchable table,
 * permission-gated creation, row context menu (edit / activate / deactivate)
 * and referential guards surfaced as friendly errors.
 */
public class DepartmentPanel extends JPanel {

    private final DepartmentController controller =
            new DepartmentController(ServiceRegistry.departmentService());
    private final EmployeeRepository employeeRepository = new EmployeeRepository();

    private final SearchField searchField = new SearchField("Search by name or code...");
    private final SecureButton newButton =
            new SecureButton("New Department", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.DEPARTMENT_CREATE);

    private final HrmsTable table = HrmsTable.builder(
                    "ID", "Code", "Department Name", "Manager", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 90)
            .badgeColumn(4)
            .onDoubleClick((viewRow, modelRow) -> editSelected())
            .contextMenu(this::buildContextMenu)
            .build();

    private List<Department> loaded = new ArrayList<>();

    public DepartmentPanel() {
        super(new BorderLayout());
        setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 12", "[grow,fill][]"));
        toolbar.setOpaque(false);
        toolbar.add(searchField);
        toolbar.add(newButton);

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        searchField.onTextChanged(text -> refresh());
        newButton.addActionListener(event -> createNew());
        refresh();
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private void refresh() {
        controller.load(searchField.getText(), rows -> {
            loaded = rows;
            List<Object[]> tableRows = new ArrayList<>();
            for (Department department : rows) {
                tableRows.add(new Object[]{
                        department.getId(),
                        department.getCode(),
                        department.getName(),
                        department.getManagerName() == null ? "-" : department.getManagerName(),
                        department.getStatus()});
            }
            table.setRows(tableRows);
        });
    }

    private Department selectedDepartment() {
        Object id = table.selectedValue(0);
        if (id == null) {
            return null;
        }
        for (Department department : loaded) {
            if (department.getId().equals(((Number) id).longValue())) {
                return department;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void createNew() {
        DepartmentDialog dialog = new DepartmentDialog(
                swingWindow(), null, employeeRepository.findActiveOptions());
        if (dialog.showDialog() == DepartmentDialog.Result.SAVED) {
            refresh();
        }
    }

    private void editSelected() {
        Department department = selectedDepartment();
        if (department == null || !SecurityService.can(Permissions.DEPARTMENT_UPDATE)) {
            return;
        }
        DepartmentDialog dialog = new DepartmentDialog(
                swingWindow(), department, employeeRepository.findActiveOptions());
        if (dialog.showDialog() == DepartmentDialog.Result.SAVED) {
            refresh();
        }
    }

    private void toggleSelected() {
        Department department = selectedDepartment();
        if (department == null) {
            return;
        }
        String next = "ACTIVE".equals(department.getStatus()) ? "INACTIVE" : "ACTIVE";
        String verb = "INACTIVE".equals(next) ? "deactivate" : "activate";
        boolean confirmed = Dialogs.confirm(swingWindow(),
                verb.substring(0, 1).toUpperCase() + verb.substring(1),
                "Are you sure you want to " + verb + " department '"
                        + department.getName() + "'?");
        if (!confirmed) {
            return;
        }
        controller.setStatus(department.getId(), next, this::refresh);
    }

    /** Context menu built per selection; items disabled without permissions. */
    private JPopupMenu buildContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        Department department = selectedDepartment();

        JMenuItem edit = new JMenuItem("Edit");
        edit.setEnabled(department != null
                && SecurityService.can(Permissions.DEPARTMENT_UPDATE));
        edit.addActionListener(event -> editSelected());
        menu.add(edit);

        if (department != null) {
            boolean active = "ACTIVE".equals(department.getStatus());
            JMenuItem toggle = new JMenuItem(active ? "Deactivate" : "Activate");
            toggle.setEnabled(SecurityService.can(Permissions.DEPARTMENT_UPDATE));
            toggle.addActionListener(event -> toggleSelected());
            menu.add(toggle);
        }
        return menu;
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
