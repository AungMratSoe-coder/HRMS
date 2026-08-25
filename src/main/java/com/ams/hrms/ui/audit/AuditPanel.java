package com.ams.hrms.ui.audit;

import java.awt.BorderLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import com.ams.hrms.component.DatePickerField;
import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.PaginationPanel;
import com.ams.hrms.component.SearchField;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.AuditController;
import com.ams.hrms.repository.AuditRepository.AuditRow;
import com.ams.hrms.repository.AuditRepository.Filter;
import com.ams.hrms.repository.AuditRepository.UserOption;

import net.miginfocom.swing.MigLayout;

/**
 * Audit Log viewer (spec section 28). The trail is append-only, so this
 * screen is strictly read-only: filter by keyword, action, module, user and
 * date range, page through results server-side (the trail grows without
 * bound) and open the full entry - including device data - on double-click.
 */
public class AuditPanel extends JPanel {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm:ss");

    private final AuditController controller =
            new AuditController(ServiceRegistry.auditService());

    private final SearchField searchField = new SearchField("Search description, entity, action or user...");
    private final JComboBox<String> moduleFilter = new JComboBox<>(new String[]{"All Modules"});
    private final JComboBox<String> actionFilter = new JComboBox<>(new String[]{"All Actions"});
    private final JComboBox<String> userFilter = new JComboBox<>(new String[]{"All Users"});
    private final DatePickerField fromDateField = new DatePickerField();
    private final DatePickerField toDateField = new DatePickerField();
    private final PaginationPanel pagination = new PaginationPanel();

    private final HrmsTable table = HrmsTable.builder(
                    "ID", "When", "User", "Action", "Module", "Entity", "Entity ID",
                    "Description", "IP")
            .hiddenColumn(0)
            .fixedColumn(1, 130)
            .fixedColumn(2, 120)
            .fixedColumn(3, 110)
            .fixedColumn(4, 110)
            .fixedColumn(5, 140)
            .badgeColumn(3)
            .onDoubleClick((viewRow, modelRow) -> showDetails())
            .build();

    private List<AuditRow> loadedPage = List.of();
    private List<UserOption> userOptions = List.of();
    private boolean filterEventsAttached;

    public AuditPanel() {
        super(new BorderLayout());
        setOpaque(false);

        add(buildToolbar(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(new JScrollPane(table), BorderLayout.CENTER);
        center.add(pagination, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);

        wireEvents();
        loadFilterOptions();
        refresh();
    }

    // ------------------------------------------------------------------
    // Toolbar & filters
    // ------------------------------------------------------------------

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new MigLayout(
                "wrap, insets 16 20 10 20, gap 8",
                "[grow,fill]"));

        JPanel firstRow = new JPanel(new MigLayout("insets 0, gap 10", "[grow,fill][][][]"));
        firstRow.setOpaque(false);
        firstRow.add(searchField);
        firstRow.add(moduleFilter, "width 170!");
        firstRow.add(actionFilter, "width 150!");
        firstRow.add(userFilter, "width 150!");

        JPanel secondRow = new JPanel(new MigLayout("insets 0, gap 10"));
        secondRow.setOpaque(false);
        JLabel rangeLabel = new JLabel("Date range:");
        rangeLabel.setForeground(com.ams.hrms.ui.theme.Palette.color(
                com.ams.hrms.ui.theme.Palette.Role.TEXT_MUTED));
        fromDateField.setDate(LocalDate.now().minusDays(30));
        secondRow.add(rangeLabel);
        secondRow.add(fromDateField, "width 150!");
        secondRow.add(new JLabel("to"));
        secondRow.add(toDateField, "width 150!");

        toolbar.add(firstRow, "growx, wrap");
        toolbar.add(secondRow, "wrap");
        return toolbar;
    }
    /** Filter options load once; combos react only after the initial fill. */
    private void loadFilterOptions() {
        controller.loadFilterOptions(options -> {
            userOptions = options.users();
            for (String module : options.modules()) {
                moduleFilter.addItem(module);
            }
            for (String action : options.actions()) {
                actionFilter.addItem(action);
            }
            for (UserOption user : options.users()) {
                userFilter.addItem(user.username());
                // Selected index maps positionally onto the cached option ids.
            }
            attachFilterEvents();
        });
    }

    private void attachFilterEvents() {
        if (filterEventsAttached) {
            return;
        }
        filterEventsAttached = true;
        searchField.onTextChanged(text -> resetToFirstPageAndRefresh());
        moduleFilter.addActionListener(event -> resetToFirstPageAndRefresh());
        actionFilter.addActionListener(event -> resetToFirstPageAndRefresh());
        userFilter.addActionListener(event -> resetToFirstPageAndRefresh());
        fromDateField.addDateChangedListener(event -> resetToFirstPageAndRefresh());
        toDateField.addDateChangedListener(event -> resetToFirstPageAndRefresh());
    }

    // ------------------------------------------------------------------
    // Data + server-side pagination
    // ------------------------------------------------------------------

    private void wireEvents() {
        pagination.onPageChange(page -> loadPage());
        pagination.onPageSizeChange(size -> refresh());
    }

    private void resetToFirstPageAndRefresh() {
        pagination.reset();
        refresh();
    }

    private Filter currentFilter() {
        Long userId = null;
        int index = userFilter.getSelectedIndex();
        if (index > 0 && index <= userOptions.size()) {
            userId = userOptions.get(index - 1).id(); // skip "All Users"
        }
        return new Filter(
                searchField.getText(),
                selectedIndexValue(actionFilter),
                selectedIndexValue(moduleFilter),
                userId,
                fromDateField.getDate(),
                toDateField.getDate());
    }

    /** Combo value without the leading "All ..." placeholder entry. */
    private static String selectedIndexValue(JComboBox<String> combo) {
        return combo.getSelectedIndex() <= 0 ? "" : String.valueOf(combo.getSelectedItem());
    }

    private void refresh() {
        controller.loadPage(currentFilter(), 1, pagination.pageSize(), result -> {
            loadedPage = result.rows();
            renderRows(result.rows());
            pagination.updateTotal((int) Math.min(result.totalMatching(), Integer.MAX_VALUE));
        });
    }

    private void loadPage() {
        int offset = (pagination.currentPage() - 1) * pagination.pageSize();
        controller.loadPage(currentFilter(), pagination.currentPage(),
                pagination.pageSize(), result -> {
                    loadedPage = result.rows();
                    renderRows(result.rows());
                });
    }

    private void renderRows(List<AuditRow> rows) {
        List<Object[]> tableRows = new ArrayList<>();
        for (AuditRow row : rows) {
            tableRows.add(new Object[]{
                    row.id(),
                    row.createdAt() == null ? "-" : TIME_FORMAT.format(row.createdAt()),
                    row.username(),
                    row.action(),
                    row.module(),
                    row.entity() == null ? "-" : row.entity(),
                    row.entityId() == null ? "-" : String.valueOf(row.entityId()),
                    row.description() == null ? "" : row.description(),
                    row.ipAddress() == null ? "-" : row.ipAddress()});
        }
        table.setRows(tableRows);
    }

    // ------------------------------------------------------------------
    // Details
    // ------------------------------------------------------------------

    private void showDetails() {
        Object value = table.selectedValue(0);
        if (value == null) {
            return;
        }
        long id = ((Number) value).longValue();
        loadedPage.stream()
                .filter(row -> row.id() == id)
                .findFirst()
                .ifPresent(row -> new AuditDetailDialog(swingWindow(), row).setVisible(true));
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
