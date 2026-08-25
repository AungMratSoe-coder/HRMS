package com.ams.hrms.ui.employee;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTabbedPane;
import javax.swing.filechooser.FileNameExtensionFilter;

import com.ams.hrms.component.EmptyStatePanel;
import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.StatusBadge;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.DocumentController;
import com.ams.hrms.controller.EmployeeController;
import com.ams.hrms.model.AttendanceRecord;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.EmployeeDocument;
import com.ams.hrms.repository.LeaveRepository.BalanceRow;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.service.DocumentService;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Employee Profile (spec section 11): identity header with photo and tabbed
 * sections - Documents and History are live, and the data tabs (Attendance,
 * Leave, Payroll, Performance, Training, Assets) render read-only records
 * from their services when the session holds the matching view permission.
 */
public class EmployeeProfileDialog extends JDialog {

    private static final int PHOTO_SIZE = 96;
    private static final int ATTENDANCE_DAYS_BACK = 90;

    private final Employee employee;
    private final DocumentController documentController =
            new DocumentController(ServiceRegistry.documentService());
    private final EmployeeController employeeController =
            new EmployeeController(ServiceRegistry.employeeService());

    private final List<EmployeeDocument> documents = new ArrayList<>();
    private HrmsTable documentTable;
    private JLabel expiryWarningLabel;

    public EmployeeProfileDialog(java.awt.Window owner, Employee loaded) {
        super(owner, "Employee Profile", ModalityType.APPLICATION_MODAL);
        this.employee = loaded;

        setTitle("Employee Profile - " + loaded.getCode() + " " + loaded.getFullName());
        setLayout(new BorderLayout());
        add(buildTabs(), BorderLayout.CENTER);

        setSize(960, 700);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    }

    // ------------------------------------------------------------------
    // Tabs
    // ------------------------------------------------------------------

    private JTabbedPane buildTabs() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Profile", buildSummaryPanel());
        tabs.addTab("Documents", buildDocumentsPanel());
        tabs.addTab("History", buildHistoryPanel());
        tabs.addTab("Attendance", guardedTab(Permissions.ATTENDANCE_VIEW,
                "Attendance", this::buildAttendanceTab));
        tabs.addTab("Leave", guardedTab(Permissions.LEAVE_VIEW,
                "Leave", this::buildLeaveTab));
        tabs.addTab("Payroll", guardedTab(null,
                "Payroll", this::buildPayrollTab, Permissions.PAYROLL_VIEW,
                Permissions.PAYSLIP_VIEW));
        tabs.addTab("Performance", guardedTab(Permissions.PERFORMANCE_VIEW,
                "Performance", this::buildPerformanceTab));
        tabs.addTab("Training", guardedTab(Permissions.TRAINING_VIEW,
                "Training", this::buildTrainingTab));
        tabs.addTab("Assets", guardedTab(Permissions.ASSET_VIEW,
                "Assets", this::buildAssetsTab));
        return tabs;
    }

    /**
     * Builds the tab content only when the session holds the required
     * permission(s); otherwise renders an explanatory empty state. A null
     * {@code permission} means any-of-{@code anyOf} semantics.
     */
    private JPanel guardedTab(Permissions permission, String title,
                              java.util.function.Supplier<JPanel> builder,
                              Permissions... anyOf) {
        boolean allowed = permission != null
                ? com.ams.hrms.security.SessionContext.has(permission)
                : com.ams.hrms.security.SessionContext.permissions().stream()
                        .anyMatch(java.util.Arrays.asList(anyOf)::contains);
        if (!allowed) {
            JPanel denied = new JPanel(new net.miginfocom.swing.MigLayout(
                    "wrap 1, align center center"));
            denied.setOpaque(false);
            denied.add(new EmptyStatePanel("lock", title,
                    "Your account does not include the "
                            + (permission != null ? permission.name() : names(anyOf))
                            + " permission."));
            return denied;
        }
        return builder.get();
    }

    private static String names(Permissions[] permissions) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < permissions.length; i++) {
            if (i > 0) {
                text.append(" or ");
            }
            text.append(permissions[i].name());
        }
        return text.toString();
    }

    /** Runs a read query off the EDT and fills the target table on return. */
    private <T> void loadInto(String taskName, java.util.function.Supplier<T> work,
                              java.util.function.Consumer<T> onSuccess) {
        com.ams.hrms.util.UiThread.executeAsync(taskName, work, onSuccess,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    // ------------------------------------------------------------------
    // Attendance tab
    // ------------------------------------------------------------------

    private JPanel buildAttendanceTab() {
        HrmsTable table = HrmsTable.builder(
                        "Date", "In", "Out", "Status", "Late (m)", "Early (m)",
                        "Worked (h)", "OT (h)")
                .fixedColumn(0, 110)
                .badgeColumn(3)
                .build();

        LocalDate today = LocalDate.now();
        loadInto("Load profile attendance",
                () -> ServiceRegistry.attendanceService().findByEmployeeBetween(
                        employee.getId(), today.minusDays(ATTENDANCE_DAYS_BACK), today),
                records -> {
                    List<Object[]> rows = new ArrayList<>();
                    for (AttendanceRecord record : records) {
                        rows.add(new Object[]{
                                dateText(record.getAttendanceDate()),
                                timeText(record.getCheckIn()),
                                timeText(record.getCheckOut()),
                                pretty(record.getStatus()),
                                record.getLateMinutes(),
                                record.getEarlyLeaveMinutes(),
                                decimalText(record.getWorkedHours()),
                                decimalText(record.getOvertimeHours())});
                    }
                    table.setRows(rows);
                });

        return tablePanel(table);
    }

    // ------------------------------------------------------------------
    // Summary tab
    // ------------------------------------------------------------------

    private JPanel buildSummaryPanel() {
        JPanel summary = new JPanel(new net.miginfocom.swing.MigLayout(
                "wrap 2, insets 24 28, gapx 30, gapy 8",
                "[300!,fill][grow,fill]"));
        summary.setOpaque(false);

        summary.add(buildIdentityCard());
        summary.add(buildInfoSections());
        return summary;
    }

    /** Photo + name/code/status + tenure. */
    private JPanel buildIdentityCard() {
        JPanel card = new JPanel(new net.miginfocom.swing.MigLayout(
                "wrap 1, insets 18 18, gap 10, align center center"));
        card.setBackground(Palette.color(Role.CARD_BG));
        card.setBorder(BorderFactory.createLineBorder(Palette.color(Role.CARD_BORDER)));

        JLabel photo = new JLabel();
        photo.setPreferredSize(new Dimension(PHOTO_SIZE, PHOTO_SIZE));
        photo.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        byte[] photoBytes = com.ams.hrms.util.ImageUtils.readStored(employee.getPhotoPath());
        if (photoBytes != null && com.ams.hrms.util.ImageUtils.isImage(photoBytes)) {
            ImageIcon icon = new ImageIcon(photoBytes);
            java.awt.Image scaled = icon.getImage().getScaledInstance(
                    PHOTO_SIZE, PHOTO_SIZE, java.awt.Image.SCALE_SMOOTH);
            photo.setIcon(new ImageIcon(scaled));
        } else {
            photo.setIcon(IconLoader.icon("user", PHOTO_SIZE));
        }
        card.add(photo);

        JLabel name = new JLabel(employee.getFullName(), javax.swing.SwingConstants.CENTER);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 17f));
        name.setForeground(Palette.color(Role.TEXT));

        JLabel code = new JLabel(employee.getCode(), javax.swing.SwingConstants.CENTER);
        code.setFont(code.getFont().deriveFont(Font.PLAIN, 12f));
        code.setForeground(Palette.color(Role.TEXT_MUTED));

        StatusBadge badge = new StatusBadge();
        badge.setStatus(employee.getStatus());
        badge.setPreferredSize(new Dimension(110, 22));

        String tenure = employee.getJoinDate() == null ? "" :
                Period.between(employee.getJoinDate(), LocalDate.now()).getYears() + " yr(s) · since "
                        + employee.getJoinDate();
        JLabel tenureLabel = new JLabel(tenure, javax.swing.SwingConstants.CENTER);
        tenureLabel.setFont(tenureLabel.getFont().deriveFont(Font.PLAIN, 11f));
        tenureLabel.setForeground(Palette.color(Role.TEXT_MUTED));

        card.add(name);
        card.add(code);
        card.add(badge, "alignx center");
        card.add(tenureLabel);
        return card;
    }

    /** Read-only information grid: personal / contact / employment / salary. */
    private JPanel buildInfoSections() {
        JPanel sections = new JPanel(new net.miginfocom.swing.MigLayout(
                "wrap 1, insets 0, gapy 14"));

        sections.add(section("Personal Information",
                infoRow("Gender", pretty(employee.getGender())),
                infoRow("Date of Birth", dateText(employee.getDateOfBirth())),
                infoRow("NRC / National ID", dash(employee.getNrc()))));

        sections.add(section("Contact Information",
                infoRow("Phone", dash(employee.getPhone())),
                infoRow("Email", dash(employee.getEmail())),
                infoRow("Address", dash(employee.getAddress()))));

        sections.add(section("Employment Information",
                infoRow("Department", dash(employee.getDepartmentName())),
                infoRow("Position", dash(employee.getPositionName())),
                infoRow("Manager", dash(employee.getManagerName())),
                infoRow("Join Date", dateText(employee.getJoinDate())),
                infoRow("Employment Type", pretty(employee.getEmploymentType()))));

        sections.add(section("Salary Information",
                infoRow("Basic Salary", money(employee.getBasicSalary())),
                infoRow("Status", pretty(employee.getStatus()))));

        return sections;
    }

    private JPanel section(String title, JPanel... rows) {
        JPanel block = new JPanel(new net.miginfocom.swing.MigLayout(
                "wrap 2, insets 0, gapy 4",
                "[150!,left][grow,left]"));
        block.setOpaque(false);

        JLabel titleLabel = new JLabel(title.toUpperCase());
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 11f));
        titleLabel.setForeground(Palette.color(Role.ACCENT));
        block.add(titleLabel, "span 2, wrap");

        for (JPanel row : rows) {
            block.add(row, "span 2, growx");
        }
        return block;
    }

    private JPanel infoRow(String label, String value) {
        JPanel pair = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 0, gap 12", "[140!,left][grow,left]"));
        pair.setOpaque(false);
        JLabel key = new JLabel(label);
        key.setFont(key.getFont().deriveFont(Font.PLAIN, 12f));
        key.setForeground(Palette.color(Role.TEXT_MUTED));
        JLabel val = new JLabel(value);
        val.setFont(val.getFont().deriveFont(Font.PLAIN, 13f));
        val.setForeground(Palette.color(Role.TEXT));
        pair.add(key);
        pair.add(val, "growx");
        return pair;
    }

    // ------------------------------------------------------------------
    // Documents tab
    // ------------------------------------------------------------------

    private JPanel buildDocumentsPanel() {
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 14 18, gap 10", "[grow,fill][]push[][]"));
        toolbar.setOpaque(false);

        expiryWarningLabel = new JLabel();
        expiryWarningLabel.setIcon(com.ams.hrms.util.IconLoader.small("warning"));
        expiryWarningLabel.setForeground(Palette.color(Role.WARNING));
        expiryWarningLabel.setVisible(false);

        SecureButton uploadButton = new SecureButton("Upload", "plus",
                ModernButton.Variant.PRIMARY, Permissions.DOCUMENT_MANAGE);
        uploadButton.addActionListener(event -> chooseAndUpload());

        SecureButton archiveButton = new SecureButton("Archive", "export",
                ModernButton.Variant.OUTLINE, Permissions.DOCUMENT_MANAGE);
        archiveButton.addActionListener(event -> archiveSelected());

        SecureButton deleteButton = new SecureButton("Delete", "trash",
                ModernButton.Variant.DANGER, Permissions.DOCUMENT_MANAGE);
        deleteButton.addActionListener(event -> deleteSelected());

        toolbar.add(expiryWarningLabel, "growx");
        toolbar.add(uploadButton);
        toolbar.add(archiveButton, "gap 8");
        toolbar.add(deleteButton);

        documentTable = HrmsTable.builder(
                        "ID", "Type", "File Name", "Size", "Expiry", "Status")
                .hiddenColumn(0)
                .fixedColumn(1, 170)
                .badgeColumn(5)
                .build();

        JScrollPane scroll = new JScrollPane(documentTable);
        scroll.setBorder(null);
        content.add(toolbar, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);

        loadDocuments();
        return content;
    }

    private void loadDocuments() {
        documentController.load(employee.getId(), rows -> {
            documents.clear();
            documents.addAll(rows);
            renderDocumentRows();

            long expiringSoon = rows.stream()
                    .filter(d -> "ACTIVE".equals(d.getStatus()))
                    .filter(d -> d.getExpiryDate() != null)
                    .filter(d -> !d.getExpiryDate().isBefore(LocalDate.now()))
                    .filter(d -> d.getExpiryDate().isBefore(LocalDate.now()
                            .plusDays(DocumentService.EXPIRY_WARNING_DAYS)))
                    .count();
            if (expiringSoon > 0) {
                expiryWarningLabel.setText(expiringSoon
                        + " document(s) expiring within "
                        + DocumentService.EXPIRY_WARNING_DAYS + " days");
                expiryWarningLabel.setVisible(true);
            } else {
                expiryWarningLabel.setVisible(false);
            }
        });
    }

    private void renderDocumentRows() {
        List<Object[]> rows = new ArrayList<>();
        for (EmployeeDocument document : documents) {
            rows.add(new Object[]{
                    document.getId(),
                    pretty(document.getDocumentType()),
                    document.getFileName(),
                    document.formattedSize(),
                    document.getExpiryDate() == null ? "-" : document.getExpiryDate().toString(),
                    document.getStatus()});
        }
        documentTable.setRows(rows);
    }

    private EmployeeDocument selectedDocument() {
        Object id = documentTable.selectedValue(0);
        if (id == null) {
            return null;
        }
        for (EmployeeDocument document : documents) {
            if (document.getId().equals(((Number) id).longValue())) {
                return document;
            }
        }
        return null;
    }

    /** One combined modal: file chooser first, then type + notes together. */
    private void chooseAndUpload() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Documents (PDF, Word, Excel, JPG, PNG)",
                "pdf", "doc", "docx", "xls", "xlsx", "jpg", "jpeg", "png"));
        int choice = chooser.showOpenDialog(this);
        if (choice != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path file = chooser.getSelectedFile().toPath();

        javax.swing.JComboBox<String> typeCombo = new javax.swing.JComboBox<>(
                DocumentService.DOCUMENT_TYPES.stream().sorted().toArray(String[]::new));
        JTextField notesField = new JTextField();

        JPanel form = new JPanel(new MigLayout("wrap 2, gap 8", "[right][grow,fill]"));
        form.add(new JLabel("Type:"));
        form.add(typeCombo);
        form.add(new JLabel("Notes:"));
        form.add(notesField);

        int ok = javax.swing.JOptionPane.showConfirmDialog(this, form,
                "Upload Document - " + file.getFileName(),
                javax.swing.JOptionPane.OK_CANCEL_OPTION,
                javax.swing.JOptionPane.PLAIN_MESSAGE);
        if (ok != javax.swing.JOptionPane.OK_OPTION || typeCombo.getSelectedItem() == null) {
            return;
        }
        documentController.upload(employee.getId(),
                String.valueOf(typeCombo.getSelectedItem()),
                file, null, notesField.getText(),
                id -> loadDocuments(),
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
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
        documentController.archive(document.getId(), this::loadDocuments);
    }

    private void deleteSelected() {
        EmployeeDocument document = selectedDocument();
        if (document == null || "DELETED".equals(document.getStatus())) {
            return;
        }
        boolean confirmed = Dialogs.confirm(this, "Delete",
                "Delete '" + document.getFileName()
                        + "'? This hides the record (soft delete).");
        if (!confirmed) {
            return;
        }
        documentController.delete(document.getId(), this::loadDocuments);
    }

    // ------------------------------------------------------------------
    // History tab
    // ------------------------------------------------------------------

    private JPanel buildHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        HrmsTable historyTable = HrmsTable.builder(
                        "Date", "Change", "From", "To", "Remarks")
                .fixedColumn(0, 110)
                .fixedColumn(1, 150)
                .build();

        employeeController.loadHistory(employee.getId(), entries -> {
            List<Object[]> rows = new ArrayList<>();
            for (var entry : entries) {
                rows.add(new Object[]{
                        entry.effectiveDate().toString(),
                        entry.changeType(),
                        entry.oldValue() == null ? "-" : entry.oldValue(),
                        entry.newValue() == null ? "-" : entry.newValue(),
                        entry.remarks() == null ? "" : entry.remarks()});
            }
            historyTable.setRows(rows);
        });

        JScrollPane scroll = new JScrollPane(historyTable);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ------------------------------------------------------------------
    // Leave tab
    // ------------------------------------------------------------------

    private JPanel buildLeaveTab() {
        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);

        JPanel balanceStrip = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 12 18, gapx 22"));
        balanceStrip.setOpaque(false);

        HrmsTable table = HrmsTable.builder(
                        "Code", "Type", "Start", "End", "Days", "Status")
                .fixedColumn(0, 110)
                .fixedColumn(1, 150)
                .badgeColumn(5)
                .build();

        int year = LocalDate.now().getYear();
        loadInto("Load profile leave balances",
                () -> ServiceRegistry.leaveService().balances(employee.getId(), year),
                balances -> {
                    for (BalanceRow balance : balances) {
                        JLabel chip = new JLabel(balance.typeName());
                        chip.setForeground(Palette.color(Role.TEXT_MUTED));
                        JLabel value = new JLabel(decimalText(balance.available())
                                + " of " + decimalText(
                                        balance.entitled().add(balance.carriedForward()))
                                + " day(s)");
                        value.setFont(value.getFont().deriveFont(Font.BOLD, 12f));
                        value.setForeground(Palette.color(Role.TEXT));
                        balanceStrip.add(chip);
                        balanceStrip.add(value, "gapright 10");
                    }
                    balanceStrip.revalidate();
                    balanceStrip.repaint();
                });

        loadInto("Load profile leave requests",
                () -> ServiceRegistry.leaveService().findForEmployee(employee.getId()),
                requests -> {
                    List<Object[]> rows = new ArrayList<>();
                    for (var request : requests) {
                        rows.add(new Object[]{
                                request.getLeaveCode(),
                                pretty(request.getTypeName()),
                                dateText(request.getStartDate()),
                                dateText(request.getEndDate()),
                                decimalText(request.getNumberOfDays()),
                                pretty(request.getStatus())});
                    }
                    table.setRows(rows);
                });

        content.add(balanceStrip, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        content.add(scroll, BorderLayout.CENTER);
        return content;
    }

    // ------------------------------------------------------------------
    // Payroll tab
    // ------------------------------------------------------------------

    private JPanel buildPayrollTab() {
        HrmsTable table = HrmsTable.builder(
                        "Period", "Number", "Basic", "Gross", "Deductions",
                        "Net", "Status")
                .fixedColumn(0, 120)
                .fixedColumn(1, 160)
                .badgeColumn(6)
                .build();

        loadInto("Load profile payroll",
                () -> ServiceRegistry.payrollService().findByEmployee(employee.getId()),
                payrolls -> {
                    List<Object[]> rows = new ArrayList<>();
                    for (var payroll : payrolls) {
                        rows.add(new Object[]{
                                payroll.periodName(),
                                payroll.payrollNumber(),
                                money(payroll.basicSalary()),
                                money(payroll.grossSalary()),
                                money(payroll.totalDeduction()),
                                money(payroll.netSalary()),
                                pretty(payroll.status())});
                    }
                    table.setRows(rows);
                });

        return tablePanel(table);
    }

    // ------------------------------------------------------------------
    // Performance tab
    // ------------------------------------------------------------------

    private JPanel buildPerformanceTab() {
        HrmsTable table = HrmsTable.builder(
                        "Code", "Period", "Overall Score", "Stage", "Status")
                .fixedColumn(0, 110)
                .badgeColumn(4)
                .build();

        loadInto("Load profile performance",
                () -> ServiceRegistry.performanceService().findForEmployee(employee.getId()),
                reviews -> {
                    List<Object[]> rows = new ArrayList<>();
                    for (var review : reviews) {
                        rows.add(new Object[]{
                                review.getReviewCode(),
                                review.getPeriodStart() + " to " + review.getPeriodEnd(),
                                review.getOverallScore() == null ? "-"
                                        : review.getOverallScore().toPlainString() + " / 5",
                                pretty(review.getStage()),
                                pretty(review.getStatus())});
                    }
                    table.setRows(rows);
                });

        return tablePanel(table);
    }

    // ------------------------------------------------------------------
    // Training tab
    // ------------------------------------------------------------------

    private JPanel buildTrainingTab() {
        HrmsTable table = HrmsTable.builder(
                        "Program", "Session", "Result", "Score", "Completed")
                .fixedColumn(0, 190)
                .badgeColumn(2)
                .build();

        loadInto("Load profile training",
                () -> ServiceRegistry.trainingService()
                        .findEnrollmentsForEmployee(employee.getId()),
                enrollments -> {
                    List<Object[]> rows = new ArrayList<>();
                    for (var enrollment : enrollments) {
                        rows.add(new Object[]{
                                enrollment.getProgramName(),
                                dash(enrollment.getSessionSummary()),
                                pretty(enrollment.getResult()),
                                enrollment.getScore() == null ? "-"
                                        : enrollment.getScore().toPlainString(),
                                dateText(enrollment.getCompletionDate())});
                    }
                    table.setRows(rows);
                });

        return tablePanel(table);
    }

    // ------------------------------------------------------------------
    // Assets tab
    // ------------------------------------------------------------------

    private JPanel buildAssetsTab() {
        HrmsTable table = HrmsTable.builder(
                        "Asset", "Assigned", "Due Return", "Returned",
                        "Condition", "Status")
                .fixedColumn(0, 230)
                .badgeColumn(5)
                .build();

        loadInto("Load profile assets",
                () -> ServiceRegistry.assetService().findAssignments(
                        null, employee.getId(), null, null),
                assignments -> {
                    List<Object[]> rows = new ArrayList<>();
                    for (var assignment : assignments) {
                        rows.add(new Object[]{
                                assignment.getAssetCode() + " - " + assignment.getAssetName(),
                                dateText(assignment.getAssignedDate()),
                                dateText(assignment.getDueReturnDate()),
                                dateText(assignment.getReturnedDate()),
                                pretty(assignment.getConditionOnReturn()),
                                pretty(assignment.getStatus())});
                    }
                    table.setRows(rows);
                });

        return tablePanel(table);
    }

    /** Scrollable read-only table filling a tab. */
    private static JPanel tablePanel(HrmsTable table) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(null);
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    // ------------------------------------------------------------------
    // Formatting helpers
    // ------------------------------------------------------------------

    private static String pretty(String value) {
        return value == null || value.isBlank() ? "-" : value.replace('_', ' ');
    }

    private static String dateText(LocalDate date) {
        return date == null ? "-" : date.toString();
    }

    private static String timeText(java.time.LocalTime time) {
        return time == null ? "-" : time.toString();
    }

    /** Plain decimal text without trailing zeros ("3.50" -> "3.5", null -> "-"). */
    private static String decimalText(java.math.BigDecimal value) {
        return value == null ? "-"
                : value.stripTrailingZeros().toPlainString();
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String money(java.math.BigDecimal value) {
        return value == null ? "-" : String.format("%,.2f", value);
    }
}
