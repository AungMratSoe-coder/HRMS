package com.ams.hrms.ui.documents;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.ams.hrms.component.DatePickerField;
import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.SearchField;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.DocumentController;
import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ErrorHandler;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.EmployeeDocument;
import com.ams.hrms.repository.EmployeeRepository;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.service.DocumentService;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.ui.theme.ThemeManager;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.FileStorage;
import com.ams.hrms.util.UiThread;

import net.miginfocom.swing.MigLayout;

/**
 * Documents module (spec section 25): org-wide employee document list with
 * keyword/type/status filters, upload, open, archive and soft-delete. The
 * per-employee flow lives in the employee profile; this screen manages the
 * whole company. Files stay on disk; rows are soft-deleted by status.
 */
public class DocumentsPanel extends JPanel {

    private static final String[] STATUSES = {"ACTIVE", "EXPIRED", "ARCHIVED"};

    private final DocumentController controller =
            new DocumentController(ServiceRegistry.documentService());

    private final SearchField searchField = new SearchField("Search file, employee, notes...");
    private final JComboBox<String> typeFilter =
            new JComboBox<>(withAll(sortedTypes()));
    private final JComboBox<String> statusFilter = new JComboBox<>(withAll(STATUSES));
    private final SecureButton uploadButton =
            new SecureButton("Upload", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.DOCUMENT_MANAGE);

    private final HrmsTable table = HrmsTable.builder(
                    "ID", "Employee", "Type", "File Name", "Size", "Expiry",
                    "Uploaded", "Status")
            .hiddenColumn(0)
            .fixedColumn(2, 150)
            .fixedColumn(4, 90)
            .fixedColumn(5, 95)
            .fixedColumn(6, 95)
            .fixedColumn(7, 95)
            .badgeColumn(7)
            .contextMenu(this::buildContextMenu)
            .onDoubleClick((row, column) -> openSelected())
            .build();

    private List<EmployeeDocument> loaded = List.of();

    private final JLabel expiryHintLabel = new JLabel(" ");

    private final java.util.function.Consumer<ThemeManager.Theme> themeListener =
            theme -> UiThread.runLater(() ->
                    expiryHintLabel.setForeground(Palette.color(Role.WARNING)));

    private final java.util.function.Consumer<Events.DataChanged> dataListener = event -> {
        if (DocumentService.DATA_SCOPE.equals(event.scope())) {
            refresh();
        }
    };

    public DocumentsPanel() {
        super(new BorderLayout());
        setOpaque(false);

        add(buildToolbar(), BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        wireEvents();
        refresh();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EventBus.subscribe(Events.DataChanged.class, dataListener);
        ThemeManager.addListener(themeListener);
    }

    @Override
    public void removeNotify() {
        ThemeManager.removeListener(themeListener);
        EventBus.unsubscribe(Events.DataChanged.class, dataListener);
        super.removeNotify();
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private static String[] sortedTypes() {
        return DocumentService.DOCUMENT_TYPES.stream().sorted().toArray(String[]::new);
    }

    private static String[] withAll(String[] values) {
        String[] options = new String[values.length + 1];
        options[0] = "All";
        System.arraycopy(values, 0, options, 1, values.length);
        return options;
    }

    private JComponent buildToolbar() {
        // Single-row toolbar (same pattern as Recruitment/Shift): the growing
        // search column pushes Upload to the right edge and keeps every
        // control on one baseline.
        JPanel toolbar = new JPanel(new MigLayout(
                "insets 16 20 10 20, gap 8", "[grow,fill][][][]"));
        toolbar.setOpaque(false);
        toolbar.add(searchField);
        toolbar.add(typeFilter, "width 150!");
        toolbar.add(statusFilter, "width 110!");
        toolbar.add(uploadButton);

        JPanel outer = new JPanel(new BorderLayout());
        outer.setOpaque(false);
        outer.add(toolbar, BorderLayout.NORTH);
        expiryHintLabel.setFont(expiryHintLabel.getFont().deriveFont(java.awt.Font.PLAIN, 12f));
        expiryHintLabel.setForeground(Palette.color(Role.WARNING));
        JPanel hintRow = new JPanel(new MigLayout("insets 0 20 10 20"));
        hintRow.setOpaque(false);
        hintRow.add(expiryHintLabel);
        outer.add(hintRow, BorderLayout.CENTER);
        return outer;
    }

    private void wireEvents() {
        searchField.onTextChanged(text -> refresh());
        typeFilter.addActionListener(event -> refresh());
        statusFilter.addActionListener(event -> refresh());
        uploadButton.addActionListener(event -> uploadFlow());
    }

    // ------------------------------------------------------------------
    // Listing
    // ------------------------------------------------------------------

    private void refresh() {
        controller.loadAll(searchField.getText(), selectedOf(typeFilter),
                selectedOf(statusFilter), rows -> {
                    loaded = rows;
                    List<Object[]> tableRows = new ArrayList<>();
                    for (EmployeeDocument document : rows) {
                        tableRows.add(new Object[]{
                                document.getId(),
                                document.getEmployeeCode() + " - " + document.getEmployeeName(),
                                pretty(document.getDocumentType()),
                                document.getFileName(),
                                document.formattedSize(),
                                document.getExpiryDate() == null
                                        ? "-" : document.getExpiryDate().toString(),
                                document.getUploadedAt() == null ? "-"
                                        : document.getUploadedAt().toLocalDate().toString(),
                                document.getStatus()});
                    }
                    table.setRows(tableRows);
                    expiryHint();
                });
    }

    /** "N document(s) expiring within 30 days" hint above the table. */
    private void expiryHint() {
        long expiring = loaded.stream()
                .filter(document -> "ACTIVE".equals(document.getStatus()))
                .filter(document -> document.getExpiryDate() != null)
                .filter(document -> !document.getExpiryDate().isBefore(LocalDate.now()))
                .filter(document -> document.getExpiryDate().isBefore(LocalDate.now()
                        .plusDays(DocumentService.EXPIRY_WARNING_DAYS)))
                .count();
        if (expiring > 0) {
            expiryHintLabel.setText(expiring + " document(s) expiring within "
                    + DocumentService.EXPIRY_WARNING_DAYS + " days");
        } else {
            expiryHintLabel.setText(" ");
        }
    }

    private static String selectedOf(JComboBox<String> filter) {
        return filter.getSelectedIndex() <= 0 ? "" : String.valueOf(filter.getSelectedItem());
    }

    private static String pretty(String value) {
        return value == null ? "" : value.replace('_', ' ');
    }

    private EmployeeDocument selectedDocument() {
        Object id = table.selectedValue(0);
        if (id == null) {
            return null;
        }
        long target = ((Number) id).longValue();
        for (EmployeeDocument document : loaded) {
            if (document.getId() == target) {
                return document;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /** Opens the stored file with the OS default application. */
    private void openSelected() {
        EmployeeDocument document = selectedDocument();
        if (document == null) {
            return;
        }
        UiThread.executeAsync("Open document",
                () -> {
                    Path path = FileStorage.resolve(document.getFilePath());
                    if (path == null || !java.nio.file.Files.isRegularFile(path)) {
                        throw new BusinessException("File is missing on disk",
                                "The stored file could not be found: "
                                        + document.getFilePath());
                    }
                    try {
                        Desktop.getDesktop().open(path.toFile());
                    } catch (java.io.IOException e) {
                        throw new BusinessException("Could not open file",
                                "No application is associated with '"
                                        + document.getFileName() + "'.");
                    }
                    return null;
                },
                result -> { },
                error -> ErrorHandler.handle(this, error instanceof Exception exception
                        ? exception : new IllegalStateException(error)));
    }

    private void archiveSelected() {
        EmployeeDocument document = selectedDocument();
        if (document == null || !"ACTIVE".equals(document.getStatus())) {
            return;
        }
        boolean confirmed = Dialogs.confirm(this, "Archive",
                "Archive '" + document.getFileName() + "'? The file stays on disk.");
        if (!confirmed) {
            return;
        }
        controller.archive(document.getId(), this::refresh);
    }

    private void deleteSelected() {
        EmployeeDocument document = selectedDocument();
        if (document == null || "DELETED".equals(document.getStatus())) {
            return;
        }
        boolean confirmed = Dialogs.confirm(this, "Delete",
                "Delete '" + document.getFileName() + "'? The row is removed from "
                        + "the list; the file stays on disk.");
        if (!confirmed) {
            return;
        }
        controller.delete(document.getId(), this::refresh);
    }

    /** File chooser first, then employee/type/expiry/notes in one dialog. */
    private void uploadFlow() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Documents (PDF, Word, Excel, JPG, PNG)",
                "pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();

        UiThread.executeAsync("Load employees for upload",
                () -> ServiceRegistry.employeeService().findAll(
                        new EmployeeRepository.Filter("", null, null, null)),
                employees -> {
                    List<Employee> active = employees.stream()
                            .filter(employee -> "ACTIVE".equals(employee.getStatus()))
                            .toList();
                    if (active.isEmpty()) {
                        Dialogs.info(this, "Upload Document",
                                "No active employees found.");
                        return;
                    }
                    showUploadDialog(file, active);
                },
                error -> ErrorHandler.handle(this, error instanceof Exception exception
                        ? exception : new IllegalStateException(error)));
    }

    private void showUploadDialog(Path file, List<Employee> employees) {
        JComboBox<Employee> employeeCombo = new JComboBox<>(employees.toArray(Employee[]::new));
        employeeCombo.setRenderer((list, value, index, selected, focused) -> {
            JLabel label = new JLabel(value == null ? "" : renderEmployee(value));
            label.setOpaque(true);
            if (selected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            return label;
        });
        JComboBox<String> typeCombo = new JComboBox<>(sortedTypes());
        DatePickerField expiryPicker = new DatePickerField();
        JTextField notesField = new JTextField();

        JPanel form = new JPanel(new MigLayout("wrap 2, gap 8",
                "[right][grow,fill]", "[][][][][]"));
        form.add(new JLabel("Employee:"));
        form.add(employeeCombo);
        form.add(new JLabel("Type:"));
        form.add(typeCombo);
        form.add(new JLabel("Expires:"));
        form.add(expiryPicker, "width 150!");
        form.add(new JLabel("Notes:"));
        form.add(notesField);

        int choice = javax.swing.JOptionPane.showConfirmDialog(this, form,
                "Upload Document - " + file.getFileName(),
                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (choice != javax.swing.JOptionPane.OK_OPTION) {
            return;
        }
        Employee employee = (Employee) employeeCombo.getSelectedItem();
        if (employee == null) {
            return;
        }
        controller.upload(employee.getId(),
                String.valueOf(typeCombo.getSelectedItem()),
                file, expiryPicker.getDate(), notesField.getText(),
                id -> refresh(),
                error -> ErrorHandler.handle(this, error instanceof Exception exception
                        ? exception : new IllegalStateException(error)));
    }

    private static String renderEmployee(Employee employee) {
        return employee.getCode() + " - " + employee.getFullName();
    }

    private JPopupMenu buildContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        EmployeeDocument document = selectedDocument();
        String status = document == null ? "" : document.getStatus();

        JMenuItem open = new JMenuItem("Open File");
        open.setEnabled(document != null && !"DELETED".equals(status));
        open.addActionListener(event -> openSelected());

        JMenuItem archive = new JMenuItem("Archive");
        archive.setEnabled(document != null && "ACTIVE".equals(status));
        archive.addActionListener(event -> archiveSelected());

        JMenuItem delete = new JMenuItem("Delete");
        delete.setEnabled(document != null && !"DELETED".equals(status));
        delete.addActionListener(event -> deleteSelected());

        menu.add(open);
        menu.addSeparator();
        menu.add(archive);
        menu.add(delete);
        return menu;
    }
}
