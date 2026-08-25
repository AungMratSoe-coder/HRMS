package com.ams.hrms.component;

import java.awt.Component;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import javax.swing.BorderFactory;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;

/**
 * Application-standard JTable factory (spec section 37). Centralizes row
 * height, sorting, single-selection behavior, cell padding, status badges,
 * double-click actions and context menus so individual screens never
 * re-implement table plumbing.
 *
 * <pre>{@code
 * HrmsTable table = HrmsTable.builder("Code", "Name", "Department", "Status")
 *         .columnType(0, String.class)
 *         .badgeColumn(3)
 *         .onDoubleClick((viewRow, modelRow) -> openProfile(viewRow))
 *         .build();
 * table.setRows(List.of(
 *         new Object[]{"EMP-0001", "Aung Kyaw", "IT", "ACTIVE"},
 *         new Object[]{"EMP-0002", "Su Su Hlaing", "HR", "PENDING"}));
 * }</pre>
 */
public class HrmsTable extends JTable {

    private HrmsTable(DefaultTableModel model) {
        super(model);
        setRowHeight(34);
        setShowGrid(false);
        setFillsViewportHeight(true);
        setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        getTableHeader().setReorderingAllowed(false);
        setDefaultRenderer(Object.class, new PaddedRenderer());
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    /** Replaces the table contents. Active sorting is reset. */
    public void setRows(List<Object[]> rows) {
        DefaultTableModel model = (DefaultTableModel) getModel();
        model.setRowCount(0);
        for (Object[] row : rows) {
            model.addRow(row);
        }
    }

    /** Model row index of the current selection, or -1 when nothing is selected. */
    public int selectedModelRow() {
        int viewRow = getSelectedRow();
        return viewRow < 0 ? -1 : convertRowIndexToModel(viewRow);
    }

    /** Value of {@code columnIndex} in the selected row, or null. */
    public Object selectedValue(int columnIndex) {
        int modelRow = selectedModelRow();
        if (modelRow < 0 || columnIndex >= getModel().getColumnCount()) {
            return null;
        }
        return getModel().getValueAt(modelRow, columnIndex);
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static Builder builder(Object... columnNames) {
        return new Builder(columnNames);
    }

    public static final class Builder {

        private final Object[] columnNames;
        private final List<Class<?>> columnTypes = new ArrayList<>();
        private final Map<Integer, Class<?>> sorterTypes = new HashMap<>();
        private final Map<Integer, Integer> fixedWidths = new HashMap<>();
        private final List<Integer> hiddenColumns = new ArrayList<>();
        private final List<Integer> badgeColumns = new ArrayList<>();
        private BiConsumer<Integer, Integer> doubleClickHandler;
        private Supplier<javax.swing.JPopupMenu> contextMenuSupplier;

        private Builder(Object[] columnNames) {
            this.columnNames = columnNames.clone();
            for (int ignored = 0; ignored < columnNames.length; ignored++) {
                columnTypes.add(String.class);
            }
        }

        /**
         * Declares the comparable type of a column so the sorter compares
         * numbers/dates numerically instead of alphabetically.
         */
        public Builder columnType(int columnIndex, Class<?> type) {
            columnTypes.set(columnIndex, type);
            sorterTypes.put(columnIndex, type);
            return this;
        }

        /** Column keeps a fixed pixel width. */
        public Builder fixedColumn(int columnIndex, int width) {
            fixedWidths.put(columnIndex, width);
            return this;
        }

        /** Column is removed from the view but remains available in the model. */
        public Builder hiddenColumn(int columnIndex) {
            hiddenColumns.add(columnIndex);
            return this;
        }

        /** Renders the column text as a status pill. */
        public Builder badgeColumn(int columnIndex) {
            badgeColumns.add(columnIndex);
            return this;
        }

        /** Action on double-click: receives the view row and model row. */
        public Builder onDoubleClick(BiConsumer<Integer, Integer> handler) {
            this.doubleClickHandler = handler;
            return this;
        }

        /** Supplies the popup menu shown on right-click over a row. */
        public Builder contextMenu(Supplier<javax.swing.JPopupMenu> menuSupplier) {
            this.contextMenuSupplier = menuSupplier;
            return this;
        }

        public HrmsTable build() {
            DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }

                @Override
                public Class<?> getColumnClass(int columnIndex) {
                    return columnTypes.get(columnIndex);
                }
            };

            HrmsTable table = new HrmsTable(model);

            TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
            for (Map.Entry<Integer, Class<?>> entry : sorterTypes.entrySet()) {
                Class<?> type = entry.getValue();
                if (type == String.class) {
                    sorter.setComparator(entry.getKey(), java.text.Collator.getInstance());
                }
                // Numeric/temporal types use their natural Comparable order.
            }
            table.setRowSorter(sorter);

            applyColumns(table);
            wireInteractions(table);
            return table;
        }

        private void applyColumns(HrmsTable table) {
            for (int i = 0; i < columnNames.length; i++) {
                TableColumn column = table.getColumnModel().getColumn(i);
                if (hiddenColumns.contains(i)) {
                    column.setMinWidth(0);
                    column.setMaxWidth(0);
                    column.setPreferredWidth(0);
                    continue;
                }
                if (fixedWidths.containsKey(i)) {
                    int width = fixedWidths.get(i);
                    column.setMinWidth(width / 2);
                    column.setMaxWidth(Math.max(width + 80, width));
                    column.setPreferredWidth(width);
                }
                if (badgeColumns.contains(i)) {
                    column.setCellRenderer(StatusBadge.cellRenderer());
                }
            }
        }

        private void wireInteractions(HrmsTable table) {
            if (doubleClickHandler != null) {
                table.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseClicked(java.awt.event.MouseEvent event) {
                        if (event.getClickCount() == 2 && table.selectedModelRow() >= 0) {
                            doubleClickHandler.accept(table.getSelectedRow(), table.selectedModelRow());
                        }
                    }
                });
            }
            if (contextMenuSupplier != null) {
                table.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mousePressed(java.awt.event.MouseEvent event) {
                        maybeShowPopup(event);
                    }

                    @Override
                    public void mouseReleased(java.awt.event.MouseEvent event) {
                        maybeShowPopup(event);
                    }

                    private void maybeShowPopup(java.awt.event.MouseEvent event) {
                        if (!event.isPopupTrigger()) {
                            return;
                        }
                        int viewRow = table.rowAtPoint(event.getPoint());
                        if (viewRow >= 0 && !table.isRowSelected(viewRow)) {
                            table.setRowSelectionInterval(viewRow, viewRow);
                        }
                        javax.swing.JPopupMenu menu = contextMenuSupplier.get();
                        if (menu != null) {
                            menu.show(event.getComponent(), event.getX(), event.getY());
                        }
                    }
                });
            }
        }
    }

    /** Adds consistent horizontal padding to all default-rendered cells. */
    private static final class PaddedRenderer extends DefaultTableCellRenderer
            implements TableCellRenderer {

        PaddedRenderer() {
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 12));
            return this;
        }
    }
}
