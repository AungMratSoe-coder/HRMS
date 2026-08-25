package com.ams.hrms.ui.reports;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.printing.PDFPageable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.component.EmptyStatePanel;
import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SearchField;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.Toast;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.ReportController;
import com.ams.hrms.exception.ErrorHandler;
import com.ams.hrms.model.Department;
import com.ams.hrms.report.ReportColumn;
import com.ams.hrms.report.ReportDefinition;
import com.ams.hrms.report.ReportFilter;
import com.ams.hrms.report.ReportResult;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.ui.theme.ThemeManager;
import com.ams.hrms.util.UiThread;
import com.ams.hrms.util.UiGraphics;

import net.miginfocom.swing.MigLayout;

/**
 * Reports module (spec section 27): report catalog on the left, dynamic
 * filter bar on top, live preview table below and PDF / Excel / print
 * exports. All long work runs off the EDT; exports are permission-gated by
 * the service layer.
 */
public class ReportsPanel extends JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(ReportsPanel.class);

    private final ReportController controller = new ReportController();

    // --- catalog ---
    private final DefaultListModel<ReportDefinition> catalogModel = new DefaultListModel<>();
    private final JList<ReportDefinition> catalogList = new JList<>(catalogModel);

    // --- filters ---
    private final com.ams.hrms.component.DatePickerField fromPicker =
            new com.ams.hrms.component.DatePickerField();
    private final com.ams.hrms.component.DatePickerField toPicker =
            new com.ams.hrms.component.DatePickerField();
    private final javax.swing.JComboBox<String> departmentCombo = new javax.swing.JComboBox<>();
    private final List<Long> departmentIds = new java.util.ArrayList<>();
    private final javax.swing.JComboBox<String> statusCombo = new javax.swing.JComboBox<>();
    private final SearchField keywordField = new SearchField("Search name or code...");
    private final ModernButton generateButton =
            new ModernButton("Generate", "refresh", ModernButton.Variant.PRIMARY);
    private final JLabel fromLabel = label("From");
    private final JLabel toLabel = label("To");
    private final JLabel departmentLabel = label("Department");
    private final JLabel statusLabel = label("Status");
    private JPanel filterBar;

    // --- results ---
    private final JLabel resultTitle = new JLabel("Select a report");
    private final JLabel resultSubtitle = new JLabel(" ");
    private final SecureButton exportPdfButton = new SecureButton(
            "Export PDF", "reports", ModernButton.Variant.OUTLINE,
            Permissions.REPORT_EXPORT);
    private final SecureButton exportExcelButton = new SecureButton(
            "Export Excel", "reports", ModernButton.Variant.OUTLINE,
            Permissions.REPORT_EXPORT);
    private final SecureButton printButton = new SecureButton(
            "Print", "print", ModernButton.Variant.OUTLINE,
            Permissions.REPORT_EXPORT);
    private final JPanel tableContainer = new JPanel(new BorderLayout());
    private final JPanel emptyState = new JPanel(new MigLayout("wrap 1, align center center"));

    private final JPanel catalogPanel = new JPanel(new BorderLayout());
    private final JLabel catalogHeading = new JLabel("REPORT CATALOG");
    private final JPanel resultCard = new JPanel(new BorderLayout(0, 10));
    private final JLabel descriptionLabel = new JLabel("Choose a report from the catalog to begin.");

    private final Consumer<ThemeManager.Theme> themeListener =
            theme -> UiThread.runLater(() -> {
                applyThemeColors();
                repaint();
            });

    private ReportDefinition selectedReport;
    private ReportResult lastResult;
    private File lastExportDirectory;

    public ReportsPanel() {
        super(new BorderLayout());
        setOpaque(false);

        add(buildCatalog(), BorderLayout.WEST);
        add(buildMainArea(), BorderLayout.CENTER);
        applyThemeColors();

        LocalDate today = LocalDate.now();
        fromPicker.setDate(today.withDayOfMonth(1));
        toPicker.setDate(today);

        wireCatalog();
        wireFilters();
        loadDepartments();
        loadCatalog();

        updateExportButtons();
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private JPanel buildCatalog() {
        catalogList.setCellRenderer(new CatalogRenderer());
        catalogList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);

        catalogPanel.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 10));

        catalogHeading.setFont(catalogHeading.getFont().deriveFont(java.awt.Font.BOLD, 11f));
        catalogHeading.setBorder(BorderFactory.createEmptyBorder(0, 4, 10, 0));

        catalogPanel.add(catalogHeading, BorderLayout.NORTH);
        catalogPanel.add(new JScrollPane(catalogList), BorderLayout.CENTER);
        catalogPanel.setPreferredSize(new java.awt.Dimension(292, 0));
        return catalogPanel;
    }

    private JPanel buildMainArea() {
        JPanel main = new JPanel(new BorderLayout(0, 12));
        main.setOpaque(false);
        main.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel filterCard = new JPanel(new BorderLayout(0, 8));
        filterCard.setOpaque(false);
        filterCard.add(buildDescriptionLabel(), BorderLayout.NORTH);
        filterCard.add(buildFilterBar(), BorderLayout.CENTER);

        tableContainer.add(emptyState, BorderLayout.CENTER);

        resultCard.setBorder(BorderFactory.createEmptyBorder(14, 16, 16, 16));
        resultCard.add(buildResultToolbar(), BorderLayout.NORTH);
        resultCard.add(tableContainer, BorderLayout.CENTER);

        main.add(filterCard, BorderLayout.NORTH);
        main.add(resultCard, BorderLayout.CENTER);
        return main;
    }

    private JLabel buildDescriptionLabel() {
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(java.awt.Font.PLAIN, 12f));
        descriptionLabel.setName("reportDescription");
        return descriptionLabel;
    }

    /** Re-applies palette-derived colors after a light/dark switch. */
    private void applyThemeColors() {
        catalogPanel.setBackground(Palette.color(Role.CARD_BG));
        catalogHeading.setForeground(Palette.color(Role.TEXT_MUTED));
        descriptionLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        resultTitle.setForeground(Palette.color(Role.TEXT));
        resultSubtitle.setForeground(Palette.color(Role.TEXT_MUTED));
        resultCard.setBackground(Palette.color(Role.CARD_BG));
        if (filterBar != null) {
            filterBar.setBackground(Palette.color(Role.SURFACE_ALT));
            filterBar.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(Palette.color(Role.CARD_BORDER)),
                    BorderFactory.createEmptyBorder()));
        }
        for (JLabel caption : new JLabel[]{fromLabel, toLabel, departmentLabel, statusLabel}) {
            caption.setForeground(Palette.color(Role.TEXT_MUTED));
        }
    }

    private JPanel buildResultToolbar() {
        resultTitle.setFont(resultTitle.getFont().deriveFont(java.awt.Font.BOLD, 15f));
        resultSubtitle.setFont(resultSubtitle.getFont().deriveFont(java.awt.Font.PLAIN, 11f));

        JPanel titles = new JPanel(new MigLayout("wrap 1, insets 0, gap 1"));
        titles.setOpaque(false);
        titles.add(resultTitle);
        titles.add(resultSubtitle);

        JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8"));
        buttons.setOpaque(false);
        buttons.add(exportPdfButton);
        buttons.add(exportExcelButton);
        buttons.add(printButton);

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.add(titles, BorderLayout.WEST);
        toolbar.add(buttons, BorderLayout.EAST);
        return toolbar;
    }

    // ------------------------------------------------------------------
    // Filter bar
    // ------------------------------------------------------------------

    /**
     * Fixed-slot filter bar, built once: From, To, Department, Status,
     * keyword and Generate each keep one permanent position for every
     * report. Filters a report does not understand are disabled in place
     * (see {@link #updateFilterState()}) instead of being added or removed,
     * so switching reports never shifts the layout.
     */
    private JPanel buildFilterBar() {
        JPanel bar = new JPanel(new MigLayout("wrap 1, insets 12 14, gap 8"));
        bar.setBackground(Palette.color(Role.SURFACE_ALT));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Palette.color(Role.CARD_BORDER)),
                BorderFactory.createEmptyBorder()));
        filterBar = bar;

        JPanel selectors = new JPanel(new MigLayout(
                "insets 0, gapx 8, gapy 4, wrap 4",
                "[fill][fill][fill][fill]"));
        selectors.setOpaque(false);
        selectors.add(fromLabel);
        selectors.add(fromPicker, "width 130!");
        selectors.add(toLabel);
        selectors.add(toPicker, "width 130!");
        selectors.add(departmentLabel);
        selectors.add(departmentCombo, "width 140!");
        selectors.add(statusLabel);
        selectors.add(statusCombo, "width 130!");
        bar.add(selectors, "growx");

        // Row 2: keyword search left, action right.
        JPanel actions = new JPanel(new BorderLayout());
        actions.setOpaque(false);
        keywordField.setPreferredSize(new java.awt.Dimension(240, 34));
        actions.add(keywordField, BorderLayout.WEST);
        JPanel generateHolder = new JPanel(new MigLayout("insets 0"));
        generateHolder.setOpaque(false);
        generateHolder.add(generateButton);
        actions.add(generateHolder, BorderLayout.EAST);
        bar.add(actions, "growx");
        return bar;
    }

    /**
     * Enables only the filters the selected report understands; disabled
     * controls keep their slot with an explanatory tooltip. Values already
     * typed into a control are kept, so switching back and forth between
     * reports preserves the user's filter input.
     */
    private void updateFilterState() {
        boolean dates = selectedReport != null && selectedReport.needsDateRange();
        boolean department = selectedReport != null && selectedReport.supportsDepartment();
        boolean keyword = selectedReport != null && selectedReport.supportsKeyword();
        String[] statusOptions =
                selectedReport == null ? new String[0] : selectedReport.statusOptions();

        setUsable(fromPicker, fromLabel, dates);
        setUsable(toPicker, toLabel, dates);
        setUsable(departmentCombo, departmentLabel, department);
        setUsable(keywordField, null, keyword);

        statusCombo.removeAllItems();
        statusCombo.addItem("All Statuses");
        for (String option : statusOptions) {
            statusCombo.addItem(option);
        }
        setUsable(statusCombo, statusLabel, statusOptions.length > 0);
    }

    private static void setUsable(JComponent component, JLabel caption, boolean usable) {
        setEnabledDeep(component, usable);
        component.setToolTipText(usable ? null : "Not applicable for this report");
        if (caption != null) {
            caption.setEnabled(usable);
        }
    }

    /** Swing does not propagate enabled state to container children. */
    private static void setEnabledDeep(Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                setEnabledDeep(child, enabled);
            }
        }
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(com.ams.hrms.ui.theme.Palette.color(
                com.ams.hrms.ui.theme.Palette.Role.TEXT_MUTED));
        return label;
    }

    // ------------------------------------------------------------------
    // Wiring
    // ------------------------------------------------------------------

    @Override
    public void addNotify() {
        super.addNotify();
        ThemeManager.addListener(themeListener);
    }

    @Override
    public void removeNotify() {
        ThemeManager.removeListener(themeListener);
        super.removeNotify();
    }

    private void wireCatalog() {
        catalogList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                selectReport(catalogList.getSelectedValue());
            }
        });
    }

    private void wireFilters() {
        generateButton.addActionListener(event -> generate());
        exportPdfButton.addActionListener(event -> exportAs("pdf"));
        exportExcelButton.addActionListener(event -> exportAs("xlsx"));
        printButton.addActionListener(event -> printResult());
    }

    private void loadDepartments() {
        UiThread.executeAsync("Load report departments",
                () -> ServiceRegistry.departmentService().findAll(""),
                departments -> {
                    departmentCombo.removeAllItems();
                    departmentIds.clear();
                    departmentCombo.addItem("All Departments");
                    departmentIds.add(null);
                    for (Department department : departments) {
                        departmentCombo.addItem(department.getName());
                        departmentIds.add(department.getId());
                    }
                });
    }

    private void loadCatalog() {
        controller.loadCatalog(reports -> {
            catalogModel.clear();
            for (ReportDefinition definition : reports) {
                catalogModel.addElement(definition);
            }
            if (!catalogModel.isEmpty()) {
                catalogList.setSelectedIndex(0);
            }
        });
    }

    private void selectReport(ReportDefinition definition) {
        this.selectedReport = definition;
        lastResult = null;
        updateExportButtons();

        updateFilterState();

        if (definition != null) {
            findDescriptionLabel().setText(definition.title() + " - " + definition.description());
            resultTitle.setText(definition.title());
            resultSubtitle.setText("No data generated yet");
        } else {
            findDescriptionLabel().setText("Choose a report from the catalog to begin.");
            resultTitle.setText("Select a report");
            resultSubtitle.setText(" ");
        }
        showEmptyState(definition == null ? "reports" : iconFor(definition),
                definition == null ? "Reports" : "Ready to generate",
                definition == null ? "Select a report from the catalog."
                        : "Set your filters and press Generate.");
        repaint();
    }

    private JLabel findDescriptionLabel() {
        JPanel mainArea = (JPanel) getComponent(1);
        JPanel filterCard = (JPanel) mainArea.getComponent(0);
        return (JLabel) filterCard.getComponent(0);
    }

    private void showEmptyState(String iconKey, String title, String message) {
        emptyState.removeAll();
        emptyState.setOpaque(false);
        emptyState.add(new EmptyStatePanel(iconKey, title, message));
        tableContainer.removeAll();
        tableContainer.add(emptyState, BorderLayout.CENTER);
        tableContainer.revalidate();
        tableContainer.repaint();
    }

    private static String iconFor(ReportDefinition definition) {
        return switch (definition) {
            case PAYROLL_REPORT, SALARY_REPORT -> "payroll";
            case ATTENDANCE_SUMMARY, LATE_REPORT, ABSENCE_REPORT -> "attendance";
            case LEAVE_REPORT, LEAVE_BALANCE -> "leave";
            case ASSET_REPORT -> "assets";
            case TRAINING_REPORT -> "training";
            case PERFORMANCE_REPORT -> "performance";
            case DEPARTMENT_REPORT -> "building";
            default -> "reports";
        };
    }

    // ------------------------------------------------------------------
    // Generation
    // ------------------------------------------------------------------

    private void generate() {
        if (selectedReport == null || generateButton.isEnabled() == false) {
            return;
        }
        ReportFilter filter = currentFilter();
        generateButton.setEnabled(false);
        generateButton.setText("Generating...");
        resultSubtitle.setText("Generating...");

        controller.generate(selectedReport, filter, this::renderResult,
                error -> {
                    ErrorHandler.handle(this, error instanceof Exception exception
                            ? exception : new IllegalStateException(error));
                    restoreGenerateButton();
                    resultSubtitle.setText("Generation failed");
                });
    }

    private ReportFilter currentFilter() {
        Long departmentId = selectedReport.supportsDepartment()
                && departmentCombo.getSelectedIndex() > 0
                ? departmentIds.get(departmentCombo.getSelectedIndex())
                : null;
        String departmentName = departmentId == null ? null
                : String.valueOf(departmentCombo.getSelectedItem());
        String status = selectedReport.statusOptions().length > 0
                && statusCombo.getSelectedIndex() > 0
                ? String.valueOf(statusCombo.getSelectedItem())
                : null;
        String keyword = selectedReport.supportsKeyword()
                ? keywordField.getText() : null;
        return new ReportFilter(selectedReport.needsDateRange() ? fromPicker.getDate() : null,
                selectedReport.needsDateRange() ? toPicker.getDate() : null,
                departmentId, departmentName, keyword, status);
    }

    private void renderResult(ReportResult result) {
        lastResult = result;
        restoreGenerateButton();
        resultTitle.setText(result.title());
        resultSubtitle.setText(result.subtitle() + "   |   "
                + result.dataRowCount() + " record(s)");

        if (!result.hasData()) {
            showEmptyState(iconFor(selectedReport), "No records",
                    "The filters returned no rows. Widen the date range or clear a filter.");
            updateExportButtons();
            return;
        }

        Object[] columnNames = new Object[result.columns().size()];
        for (int i = 0; i < result.columns().size(); i++) {
            ReportColumn column = result.columns().get(i);
            columnNames[i] = column.header();
        }
        HrmsTable.Builder builder = HrmsTable.builder(columnNames);
        for (int i = 0; i < result.columns().size(); i++) {
            builder.columnType(i, modelType(result.columns().get(i).kind()));
        }
        HrmsTable table = builder.build();
        table.setDefaultRenderer(Object.class, new ReportCellRenderer(result));

        java.util.List<Object[]> modelRows = new java.util.ArrayList<>(result.rows());
        if (result.totalsRow() != null) {
            modelRows.add(result.totalsRow());
        }
        table.setRows(modelRows);

        JScrollPane scroll = new JScrollPane(table);
        scroll.getViewport().setBackground(table.getBackground());
        tableContainer.removeAll();
        tableContainer.add(scroll, BorderLayout.CENTER);
        tableContainer.revalidate();
        tableContainer.repaint();
        updateExportButtons();
    }

    private static Class<?> modelType(ReportColumn.Kind kind) {
        return switch (kind) {
            case MONEY, NUMBER -> java.math.BigDecimal.class;
            case DATE -> LocalDate.class;
            default -> String.class;
        };
    }

    private void restoreGenerateButton() {
        generateButton.setEnabled(true);
        generateButton.setText("Generate");
    }

    // ------------------------------------------------------------------
    // Export & print
    // ------------------------------------------------------------------

    private void exportAs(String format) {
        if (lastResult == null || !lastResult.hasData()) {
            Toast.show(SwingUtilities.getWindowAncestor(this), Toast.Type.WARNING,
                    "Generate a report first.");
            return;
        }
        boolean pdf = format.equals("pdf");
        File suggested = suggestedFile(pdf ? ".pdf" : ".xlsx");
        JFileChooser chooser = new JFileChooser(lastExportDirectory);
        chooser.setSelectedFile(suggested);
        chooser.setDialogTitle(pdf ? "Export PDF" : "Export Excel");
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File target = ensureExtension(chooser.getSelectedFile(), pdf ? ".pdf" : ".xlsx");
        lastExportDirectory = target.getParentFile();
        if (target.exists() && !com.ams.hrms.util.Dialogs.confirm(this, "Overwrite file?",
                target.getName() + " already exists. Replace it?")) {
            return;
        }

        java.util.function.Consumer<byte[]> writer = bytes ->
                UiThread.executeAsync("Save " + format.toUpperCase(),
                        () -> {
                            try {
                                Files.write(target.toPath(), bytes);
                                return target.toPath();
                            } catch (java.io.IOException e) {
                                throw new com.ams.hrms.exception.BusinessException(
                                        "Could not save the file",
                                        "Check the folder permissions and try again.");
                            }
                        },
                        path -> Toast.show(SwingUtilities.getWindowAncestor(this),
                                Toast.Type.SUCCESS, "Saved " + path.getFileName()),
                        error -> ErrorHandler.handle(this, error instanceof Exception exception
                                ? exception : new IllegalStateException(error)));

        if (pdf) {
            controller.exportPdf(lastResult, writer, this::exportFailed);
        } else {
            controller.exportExcel(lastResult, writer, this::exportFailed);
        }
    }

    private void exportFailed(Exception error) {
        ErrorHandler.handle(this, error);
    }

    private void printResult() {
        if (lastResult == null || !lastResult.hasData()) {
            Toast.show(SwingUtilities.getWindowAncestor(this), Toast.Type.WARNING,
                    "Generate a report first.");
            return;
        }
        controller.exportPdf(lastResult, bytes -> {
            try (PDDocument document = Loader.loadPDF(bytes)) {
                java.awt.print.PrinterJob job = java.awt.print.PrinterJob.getPrinterJob();
                job.setPageable(new PDFPageable(document));
                job.setJobName(lastResult.title());
                if (job.printDialog()) {
                    job.print();
                }
            } catch (Exception e) {
                LOG.error("Printing failed: {}", e.getMessage(), e);
                ErrorHandler.handle(this, e);
            }
        }, this::exportFailed);
    }

    private File suggestedFile(String extension) {
        String prefix = selectedReport == null ? "report" : selectedReport.fileId();
        return new File(lastExportDirectory, prefix + "_" + LocalDate.now() + extension);
    }

    private static File ensureExtension(File file, String extension) {
        String path = file.getPath();
        return path.toLowerCase().endsWith(extension)
                ? file : new File(path + extension);
    }

    private void updateExportButtons() {
        boolean allowed = SessionContext.has(Permissions.REPORT_EXPORT);
        boolean hasData = lastResult != null && lastResult.hasData();
        exportPdfButton.setEnabled(allowed && hasData);
        exportExcelButton.setEnabled(allowed && hasData);
        printButton.setEnabled(allowed && hasData);
    }

    // ------------------------------------------------------------------
    // Renderers
    // ------------------------------------------------------------------

    /** Formats typed cells via {@link ReportColumn} and right-aligns numbers. */
    private static final class ReportCellRenderer extends DefaultTableCellRenderer {

        private final transient ReportResult result;

        ReportCellRenderer(ReportResult result) {
            this.result = result;
            setHorizontalAlignment(javax.swing.SwingConstants.LEADING);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus,
                                                       int row, int viewColumn) {
            super.getTableCellRendererComponent(table, value, isSelected, false, row, viewColumn);
            int columnIndex = table.convertColumnIndexToModel(viewColumn);
            ReportColumn column = result.columns().get(columnIndex);
            setText(column.display(value));
            setHorizontalAlignment(column.isRightAligned()
                    ? javax.swing.SwingConstants.RIGHT
                    : javax.swing.SwingConstants.LEADING);

            boolean totalsRow = result.totalsRow() != null
                    && row == table.getRowCount() - 1;
            setFont(getFont().deriveFont(totalsRow ? java.awt.Font.BOLD : java.awt.Font.PLAIN));
            return this;
        }
    }

    /** Two-line catalog entry: bold title over a muted description. */
    private static final class CatalogRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value,
                                                      int index, boolean selected,
                                                      boolean focused) {
            ReportDefinition definition = (ReportDefinition) value;
            JPanel panel = new JPanel(new MigLayout("wrap 1, insets 8 10, gap 2"));
            JLabel title = new JLabel(definition.title());
            title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 12.5f));
            JLabel description = new JLabel(wrap(definition.description()));
            description.setFont(description.getFont().deriveFont(java.awt.Font.PLAIN, 10.5f));

            Color text = com.ams.hrms.ui.theme.Palette.color(
                    com.ams.hrms.ui.theme.Palette.Role.TEXT);
            Color muted = com.ams.hrms.ui.theme.Palette.color(
                    com.ams.hrms.ui.theme.Palette.Role.TEXT_MUTED);
            title.setForeground(text);
            description.setForeground(muted);
            panel.add(title);
            panel.add(description);

            if (selected) {
                panel.setBackground(UiGraphics.blend(list.getBackground(),
                        com.ams.hrms.ui.theme.Palette.accentSoft(), 0.35));
            } else {
                panel.setBackground(list.getBackground());
            }
            panel.setOpaque(true);
            return panel;
        }

        private static String wrap(String text) {
            return "<html><div width=240>" + text + "</div></html>";
        }
    }
}
