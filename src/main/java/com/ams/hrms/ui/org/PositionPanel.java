package com.ams.hrms.ui.org;

import java.awt.BorderLayout;
import java.math.BigDecimal;
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
import com.ams.hrms.controller.PositionController;
import com.ams.hrms.model.Position;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

/**
 * Positions module screen (spec section 13): searchable table with salary
 * envelope column, permission-gated creation, context menu with
 * activate/deactivate guarded by active-employee counts.
 */
public class PositionPanel extends JPanel {

    private final PositionController controller =
            new PositionController(ServiceRegistry.positionService());

    private final SearchField searchField =
            new SearchField("Search by name, code or department...");
    private final SecureButton newButton =
            new SecureButton("New Position", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.POSITION_CREATE);

    private final HrmsTable table = HrmsTable.builder(
                    "ID", "Code", "Position Name", "Department", "Salary Range", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 90)
            .badgeColumn(5)
            .onDoubleClick((viewRow, modelRow) -> editSelected())
            .contextMenu(this::buildContextMenu)
            .build();

    private List<Position> loaded = new ArrayList<>();

    public PositionPanel() {
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
            for (Position position : rows) {
                tableRows.add(new Object[]{
                        position.getId(),
                        position.getCode(),
                        position.getName(),
                        position.getDepartmentName(),
                        salaryRange(position),
                        position.getStatus()});
            }
            table.setRows(tableRows);
        });
    }

    private static String salaryRange(Position position) {
        BigDecimal min = position.getMinSalary();
        BigDecimal max = position.getMaxSalary();
        if (min == null && max == null) {
            return "-";
        }
        if (min != null && max != null) {
            return String.format("%,.0f - %,.0f", min, max);
        }
        return min != null
                ? String.format("from %,.0f", min)
                : String.format("up to %,.0f", max);
    }

    private Position selectedPosition() {
        Object id = table.selectedValue(0);
        if (id == null) {
            return null;
        }
        for (Position position : loaded) {
            if (position.getId().equals(((Number) id).longValue())) {
                return position;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    private void createNew() {
        openDialog(null);
    }

    private void editSelected() {
        Position position = selectedPosition();
        if (position == null || !SecurityService.can(Permissions.POSITION_UPDATE)) {
            return;
        }
        openDialog(position);
    }

    /** Loads active departments off the EDT, then opens the modal dialog. */
    private void openDialog(Position existing) {
        UiThread.executeAsync("Load departments",
                () -> ServiceRegistry.departmentService().findAll(""),
                departments -> {
                    PositionDialog dialog = new PositionDialog(swingWindow(), existing, departments);
                    if (dialog.showDialog() == PositionDialog.Result.SAVED) {
                        refresh();
                    }
                });
    }

    private void toggleSelected() {
        Position position = selectedPosition();
        if (position == null) {
            return;
        }
        String next = "ACTIVE".equals(position.getStatus()) ? "INACTIVE" : "ACTIVE";
        String verb = "INACTIVE".equals(next) ? "deactivate" : "activate";
        boolean confirmed = Dialogs.confirm(swingWindow(),
                verb.substring(0, 1).toUpperCase() + verb.substring(1),
                "Are you sure you want to " + verb + " position '"
                        + position.getName() + "'?");
        if (!confirmed) {
            return;
        }
        controller.setStatus(position.getId(), next, this::refresh);
    }

    private JPopupMenu buildContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        Position position = selectedPosition();

        JMenuItem edit = new JMenuItem("Edit");
        edit.setEnabled(position != null && SecurityService.can(Permissions.POSITION_UPDATE));
        edit.addActionListener(event -> editSelected());
        menu.add(edit);

        if (position != null) {
            boolean active = "ACTIVE".equals(position.getStatus());
            JMenuItem toggle = new JMenuItem(active ? "Deactivate" : "Activate");
            toggle.setEnabled(SecurityService.can(Permissions.POSITION_UPDATE));
            toggle.addActionListener(event -> toggleSelected());
            menu.add(toggle);
        }
        return menu;
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
