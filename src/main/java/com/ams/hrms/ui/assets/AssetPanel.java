package com.ams.hrms.ui.assets;

import java.awt.BorderLayout;
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
import com.ams.hrms.controller.AssetController;
import com.ams.hrms.model.Asset;
import com.ams.hrms.model.AssetAssignment;
import com.ams.hrms.model.Employee;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.service.AssetRules;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

/**
 * Asset module (spec section 24): the company asset catalogue with lifecycle
 * states plus assignment history with assign/return/lost flows.
 */
public class AssetPanel extends JPanel {

    private static final String[] CATEGORIES = {
            "LAPTOP", "DESKTOP", "MONITOR", "PHONE", "TABLET",
            "ID_CARD", "VEHICLE", "FURNITURE", "OTHER"};
    private static final String[] ASSET_STATUSES = {
            "AVAILABLE", "ASSIGNED", "UNDER_REPAIR", "RETIRED", "LOST"};
    private static final String[] ASSIGNMENT_STATUSES = {
            "ASSIGNED", "OVERDUE", "RETURNED", "LOST"};

    private final AssetController controller =
            new AssetController(ServiceRegistry.assetService());

    // --- assets tab ---
    private final SearchField assetSearch = new SearchField("Search name, code, serial...");
    private final JComboBox<String> categoryFilter = new JComboBox<>(withAll(CATEGORIES));
    private final JComboBox<String> assetStatusFilter = new JComboBox<>(withAll(ASSET_STATUSES));
    private final SecureButton newAssetButton =
            new SecureButton("New Asset", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.ASSET_MANAGE);
    private final HrmsTable assetTable = HrmsTable.builder(
                    "ID", "Code", "Name", "Category", "Serial", "Condition",
                    "Holder", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 85)
            .fixedColumn(3, 95)
            .fixedColumn(4, 140)
            .fixedColumn(5, 90)
            .badgeColumn(5)
            .badgeColumn(7)
            .contextMenu(this::buildAssetMenu)
            .build();
    private List<Asset> loadedAssets = List.of();

    // --- assignments tab ---
    private final SearchField assignmentSearch = new SearchField("Search asset or employee...");
    private final JComboBox<String> assignmentStatusFilter =
            new JComboBox<>(withAll(ASSIGNMENT_STATUSES));
    private final HrmsTable assignmentTable = HrmsTable.builder(
                    "ID", "Asset", "Employee", "Assigned", "Due Back",
                    "Returned", "Condition", "Status")
            .hiddenColumn(0)
            .fixedColumn(1, 190)
            .fixedColumn(3, 95)
            .fixedColumn(4, 95)
            .fixedColumn(5, 95)
            .fixedColumn(6, 90)
            .badgeColumn(6)
            .badgeColumn(7)
            .contextMenu(this::buildAssignmentMenu)
            .build();
    private List<AssetAssignment> loadedAssignments = List.of();

    public AssetPanel() {
        super(new BorderLayout());
        setOpaque(false);

        UiThread.runAsync("Flag overdue assignments",
                () -> ServiceRegistry.assetService().refreshOverdue());

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Assets", buildAssetsTab());
        tabs.addTab("Assignments", buildAssignmentsTab());
        add(tabs, BorderLayout.CENTER);

        wireEvents();
        refreshAssets();
        refreshAssignments();
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private static String[] withAll(String[] values) {
        String[] options = new String[values.length + 1];
        options[0] = "All";
        System.arraycopy(values, 0, options, 1, values.length);
        return options;
    }

    private JPanel buildAssetsTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 8", "[grow,fill][][][fill]"));
        toolbar.setOpaque(false);
        toolbar.add(assetSearch);
        toolbar.add(categoryFilter, "width 110!");
        toolbar.add(assetStatusFilter, "width 130!");
        toolbar.add(newAssetButton);

        tab.add(toolbar, BorderLayout.NORTH);
        tab.add(new JScrollPane(assetTable), BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildAssignmentsTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10", "[grow,fill][]"));
        toolbar.setOpaque(false);
        toolbar.add(assignmentSearch);
        toolbar.add(assignmentStatusFilter, "width 120!");

        tab.add(toolbar, BorderLayout.NORTH);
        tab.add(new JScrollPane(assignmentTable), BorderLayout.CENTER);
        return tab;
    }

    private void wireEvents() {
        assetSearch.onTextChanged(text -> refreshAssets());
        categoryFilter.addActionListener(event -> refreshAssets());
        assetStatusFilter.addActionListener(event -> refreshAssets());
        newAssetButton.addActionListener(event -> openAssetDialog(null));

        assignmentSearch.onTextChanged(text -> refreshAssignments());
        assignmentStatusFilter.addActionListener(event -> refreshAssignments());
    }

    // ------------------------------------------------------------------
    // Assets
    // ------------------------------------------------------------------

    private void refreshAssets() {
        controller.loadAssets(assetSearch.getText(), selectedOf(categoryFilter),
                selectedOf(assetStatusFilter), assets -> {
                    loadedAssets = assets;
                    List<Object[]> rows = new ArrayList<>();
                    for (var asset : assets) {
                        rows.add(new Object[]{
                                asset.getId(),
                                asset.getCode(),
                                asset.getName(),
                                asset.getCategory(),
                                asset.getSerialNumber() == null ? "-" : asset.getSerialNumber(),
                                asset.getConditionStatus(),
                                asset.getHolderName() == null ? "-"
                                        : asset.getHolderCode() + " - " + asset.getHolderName(),
                                asset.getStatus()});
                    }
                    assetTable.setRows(rows);
                });
    }

    private static String selectedOf(JComboBox<String> filter) {
        return filter.getSelectedIndex() <= 0 ? "" : String.valueOf(filter.getSelectedItem());
    }

    private Asset selectedAsset() {
        return findById(assetTable, 0, loadedAssets, Asset::getId);
    }

    private void openAssetDialog(Asset existing) {
        AssetDialog dialog = new AssetDialog(swingWindow(), existing);
        if (dialog.showDialog() == AssetDialog.Result.SAVED) {
            refreshAssets();
            refreshAssignments();
        }
    }

    /** Assign flow: loads active employees then opens the dialog. */
    private void assignSelected() {
        var asset = selectedAsset();
        if (asset == null || !AssetRules.ASSET_AVAILABLE.equals(asset.getStatus())) {
            return;
        }
        UiThread.executeAsync("Load assign dialog data",
                () -> ServiceRegistry.employeeService().findAll(
                        new com.ams.hrms.repository.EmployeeRepository.Filter(
                                "", null, null, null)),
                employees -> {
                    List<Employee> active = employees.stream()
                            .filter(employee -> "ACTIVE".equals(employee.getStatus()))
                            .toList();
                    AssignDialog dialog = new AssignDialog(swingWindow(), asset, active);
                    if (dialog.showDialog() == AssignDialog.Result.SAVED) {
                        refreshAssets();
                        refreshAssignments();
                    }
                });
    }

    private void setAssetStatus(Asset asset, String target) {
        boolean confirmed = Dialogs.confirm(swingWindow(), "Update Asset",
                "Set asset '" + asset.getCode() + "' (" + asset.getName()
                        + ") to " + target + "?");
        if (!confirmed) {
            return;
        }
        controller.setAssetStatus(asset.getId(), target, this::refreshAssets,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private JPopupMenu buildAssetMenu() {
        JPopupMenu menu = new JPopupMenu();
        var asset = selectedAsset();
        boolean manage = SecurityService.can(Permissions.ASSET_MANAGE);
        boolean assignPermission = SecurityService.can(Permissions.ASSET_ASSIGN);
        String status = asset == null ? "" : asset.getStatus();

        JMenuItem edit = new JMenuItem("Edit");
        edit.setEnabled(asset != null && manage && !"RETIRED".equals(status)
                && !"LOST".equals(status));
        edit.addActionListener(event -> openAssetDialog(selectedAsset()));

        JMenuItem assignItem = new JMenuItem("Assign to Employee...");
        assignItem.setEnabled(assignPermission
                && AssetRules.ASSET_AVAILABLE.equals(status));
        assignItem.addActionListener(event -> assignSelected());

        JMenuItem repair = new JMenuItem("Send to Repair");
        repair.setEnabled(manage && AssetRules.canTransitionManually(status,
                AssetRules.ASSET_UNDER_REPAIR));
        repair.addActionListener(event ->
                setAssetStatus(selectedAsset(), AssetRules.ASSET_UNDER_REPAIR));

        JMenuItem backAvailable = new JMenuItem("Mark Available");
        backAvailable.setEnabled(manage && AssetRules.canTransitionManually(status,
                AssetRules.ASSET_AVAILABLE));
        backAvailable.addActionListener(event ->
                setAssetStatus(selectedAsset(), AssetRules.ASSET_AVAILABLE));

        JMenuItem retire = new JMenuItem("Retire");
        retire.setEnabled(manage && AssetRules.canTransitionManually(status,
                AssetRules.ASSET_RETIRED));
        retire.addActionListener(event ->
                setAssetStatus(selectedAsset(), AssetRules.ASSET_RETIRED));

        JMenuItem lost = new JMenuItem("Mark Lost");
        lost.setEnabled(manage && AssetRules.canTransitionManually(status,
                AssetRules.ASSET_LOST));
        lost.addActionListener(event ->
                setAssetStatus(selectedAsset(), AssetRules.ASSET_LOST));

        menu.add(edit);
        menu.add(assignItem);
        menu.addSeparator();
        menu.add(repair);
        menu.add(backAvailable);
        menu.add(retire);
        menu.add(lost);
        return menu;
    }

    // ------------------------------------------------------------------
    // Assignments
    // ------------------------------------------------------------------

    private void refreshAssignments() {
        controller.loadAssignments(null, null, selectedOf(assignmentStatusFilter),
                assignmentSearch.getText(), assignments -> {
                    loadedAssignments = assignments;
                    List<Object[]> rows = new ArrayList<>();
                    for (var assignment : assignments) {
                        rows.add(new Object[]{
                                assignment.getId(),
                                assignment.getAssetCode() + " - " + assignment.getAssetName(),
                                assignment.getEmployeeCode() + " - "
                                        + assignment.getEmployeeName(),
                                assignment.getAssignedDate(),
                                assignment.getDueReturnDate() == null
                                        ? "-" : assignment.getDueReturnDate(),
                                assignment.getReturnedDate() == null
                                        ? "-" : assignment.getReturnedDate(),
                                assignment.getConditionOnReturn() == null
                                        ? "-" : assignment.getConditionOnReturn(),
                                assignment.getStatus()});
                    }
                    assignmentTable.setRows(rows);
                });
    }

    private AssetAssignment selectedAssignment() {
        return findById(assignmentTable, 0, loadedAssignments, AssetAssignment::getId);
    }

    private void returnSelected() {
        var assignment = selectedAssignment();
        if (assignment == null || !assignment.isOpen()) {
            return;
        }
        ReturnDialog dialog = new ReturnDialog(swingWindow(), assignment);
        if (dialog.showDialog() == ReturnDialog.Result.SAVED) {
            refreshAssignments();
            refreshAssets();
        }
    }

    private void markAssignmentLost() {
        var assignment = selectedAssignment();
        if (assignment == null || !assignment.isOpen()) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Mark Lost",
                "Mark '" + assignment.getAssetCode() + "' (held by "
                        + assignment.getEmployeeName() + ") as LOST? The asset record "
                        + "will also be marked LOST.");
        if (!confirmed) {
            return;
        }
        controller.markLost(assignment.getId(), null, () -> {
            refreshAssignments();
            refreshAssets();
        }, error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private JPopupMenu buildAssignmentMenu() {
        JPopupMenu menu = new JPopupMenu();
        var assignment = selectedAssignment();
        boolean assignPermission = SecurityService.can(Permissions.ASSET_ASSIGN);
        boolean manage = SecurityService.can(Permissions.ASSET_MANAGE);
        boolean open = assignment != null && assignment.isOpen();

        JMenuItem returned = new JMenuItem("Return Asset...");
        returned.setEnabled(open && assignPermission);
        returned.addActionListener(event -> returnSelected());

        JMenuItem lost = new JMenuItem("Mark Lost");
        lost.setEnabled(open && manage);
        lost.addActionListener(event -> markAssignmentLost());

        menu.add(returned);
        menu.add(lost);
        return menu;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private <T> T findById(HrmsTable table, int idColumn, List<T> source,
                           java.util.function.Function<T, Long> idGetter) {
        Object value = table.selectedValue(idColumn);
        if (value == null) {
            return null;
        }
        long target = ((Number) value).longValue();
        for (T item : source) {
            Long id = idGetter.apply(item);
            if (id != null && id == target) {
                return item;
            }
        }
        return null;
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
