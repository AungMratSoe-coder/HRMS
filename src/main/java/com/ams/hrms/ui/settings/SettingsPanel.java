package com.ams.hrms.ui.settings;

import java.awt.BorderLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import com.ams.hrms.component.FormField;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.Toast;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.SettingsController;
import com.ams.hrms.exception.ErrorHandler;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.AppSetting;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.service.SettingsService;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Application settings module: one tab per category, one editor row per
 * seeded {@code app_settings} key. Saving submits every value; the service
 * validates, ignores unchanged keys and writes changes atomically with an
 * audit entry each.
 */
public class SettingsPanel extends JPanel {

    private static final Map<String, String> CATEGORY_TITLES = buildCategoryTitles();

    private final SettingsController controller = new SettingsController(
            ServiceRegistry.settingsService(), ServiceRegistry.backupService());

    private final JPanel centerHolder = new JPanel(new BorderLayout());
    private final JTabbedPane tabs = new JTabbedPane();
    private final SecureButton saveButton =
            new SecureButton("Save Changes", "check", ModernButton.Variant.PRIMARY,
                    Permissions.SETTINGS_MANAGE);
    private final JLabel statusLabel = new JLabel(" ");
    private final JLabel errorBanner = new JLabel();

    private final Map<String, String> originalValues = new LinkedHashMap<>();
    private final Map<String, FormField> editors = new LinkedHashMap<>();

    public SettingsPanel() {
        super(new BorderLayout());
        setOpaque(false);
        centerHolder.setOpaque(false);

        statusLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVerticalTextPosition(javax.swing.SwingConstants.TOP);
        errorBanner.setVisible(false);

        JPanel south = new JPanel(new MigLayout(
                "insets 12 20 16 20, gapx 12, wrap 2",
                "[grow,fill][]"));
        south.setOpaque(false);
        south.add(statusLabel, "cell 0 0");
        south.add(saveButton, "cell 1 0");
        south.add(errorBanner, "cell 0 1, spanx 2");

        JLabel loading = new JLabel("Loading settings...");
        loading.setForeground(Palette.color(Role.TEXT_MUTED));

        centerHolder.add(loading, BorderLayout.CENTER);

        add(centerHolder, BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);

        saveButton.addActionListener(event -> save());
        reload();
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private void reload() {
        controller.loadSettings(this::renderSettings);
    }

    private void renderSettings(List<AppSetting> settings) {
        originalValues.clear();
        editors.clear();
        errorBanner.setVisible(false);

        int selectedIndex = tabs.getSelectedIndex();
        tabs.removeAll();

        for (AppSetting setting : settings) {
            originalValues.put(setting.getKey(), setting.getValue() == null
                    ? "" : setting.getValue());
        }
        Map<String, List<AppSetting>> byCategory = groupByCategory(settings);
        for (Map.Entry<String, List<AppSetting>> group : byCategory.entrySet()) {
            tabs.addTab(CATEGORY_TITLES.getOrDefault(group.getKey(),
                    titleCase(group.getKey())), buildCategoryTab(group.getValue()));
        }

        if (com.ams.hrms.security.SessionContext.has(Permissions.USER_MANAGE)) {
            tabs.addTab("User Accounts", new UserAccountsPanel());
        }

        tabs.addTab("Backup && Restore", buildBackupTab());

        centerHolder.removeAll();
        if (tabs.getTabCount() == 0) {
            JLabel empty = new JLabel("No settings are defined.");
            empty.setForeground(Palette.color(Role.TEXT_MUTED));
            centerHolder.add(empty, BorderLayout.CENTER);
        } else {
            centerHolder.add(tabs, BorderLayout.CENTER);
        }
        centerHolder.revalidate();
        centerHolder.repaint();

        if (tabs.getTabCount() > 0) {
            tabs.setSelectedIndex(Math.max(0, Math.min(
                    selectedIndex < 0 ? 0 : selectedIndex, tabs.getTabCount() - 1)));
        }
        updateStatus();
    }

    private JComponent buildCategoryTab(List<AppSetting> settings) {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 20 24 8 24, gapy 6",
                "[grow,fill]",
                ""));
        form.setOpaque(false);

        for (AppSetting setting : settings) {
            form.add(buildSettingRow(setting));
        }
        form.add(new JPanel(), "height 12!");

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private JComponent buildSettingRow(AppSetting setting) {
        FormField field = setting.isBoolean()
                ? FormField.comboBox(SettingsService.friendlyLabel(setting.getKey()),
                        new String[]{"true", "false"}, false)
                : FormField.textField(SettingsService.friendlyLabel(setting.getKey()), false);
        field.setText(setting.getValue() == null ? "" : setting.getValue());
        field.setToolTipText(setting.getDescription());
        editors.put(setting.getKey(), field);
        trackChanges(field);

        JPanel row = new JPanel(new MigLayout("wrap 1, insets 0, gap 2", "[grow,fill]"));
        row.setOpaque(false);
        row.add(field);
        JLabel description = new JLabel(setting.getDescription());
        description.setFont(description.getFont().deriveFont(Font.PLAIN, 11f));
        description.setForeground(Palette.color(Role.TEXT_MUTED));
        row.add(description);
        return row;
    }

    /** Marks the status label dirty/clean while the user types. */
    private void trackChanges(FormField field) {
        if (field.editor() instanceof javax.swing.JTextField textField) {
            javax.swing.event.DocumentListener listener =
                    new javax.swing.event.DocumentListener() {
                        @Override
                        public void insertUpdate(javax.swing.event.DocumentEvent e) {
                            updateStatus();
                        }

                        @Override
                        public void removeUpdate(javax.swing.event.DocumentEvent e) {
                            updateStatus();
                        }

                        @Override
                        public void changedUpdate(javax.swing.event.DocumentEvent e) {
                            updateStatus();
                        }
                    };
            textField.getDocument().addDocumentListener(listener);
        } else if (field.editor() instanceof javax.swing.JComboBox<?> comboBox) {
            comboBox.addActionListener(event -> updateStatus());
        }
    }

    // ------------------------------------------------------------------
    // Backup & Restore
    // ------------------------------------------------------------------

    private JComponent buildBackupTab() {
        JPanel form = new JPanel(new MigLayout(
                "wrap 1, insets 20 24 8 24, gapy 6",
                "[grow,fill]",
                ""));
        form.setOpaque(false);

        JLabel backupTitle = sectionTitle("Database Backup");
        JLabel backupInfo = mutedLabel(
                "Saves a complete copy of the database (structure, data, routines) "
                        + "to a .sql file. Keep backups on a different drive or USB disk.");

        SecureButton backupButton = new SecureButton("Backup Now...", "save",
                ModernButton.Variant.PRIMARY, Permissions.SETTINGS_MANAGE);
        backupButton.addActionListener(event -> backupNow(backupButton));

        JLabel restoreTitle = sectionTitle("Restore from Backup");
        JLabel restoreWarning = new JLabel(
                "<html>Restoring replaces <b>everything</b> currently in the database with the "
                        + "contents of the backup file. All changes made after that backup are lost.<br>"
                        + "Make sure other users have closed the application before restoring.</html>");
        restoreWarning.setForeground(Palette.color(Role.DANGER));

        SecureButton restoreButton = new SecureButton("Restore from File...", "refresh",
                ModernButton.Variant.DANGER, Permissions.SETTINGS_MANAGE);
        restoreButton.addActionListener(event -> restoreFromFile(restoreButton));

        form.add(backupTitle);
        form.add(backupInfo);
        form.add(backupButton, "width 220!, height 36!");
        form.add(new JPanel(), "height 16!");
        form.add(restoreTitle);
        form.add(restoreWarning);
        form.add(restoreButton, "width 220!, height 36!");
        form.add(new JPanel(), "height 12!");

        JScrollPane scrollPane = new JScrollPane(form);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    private void backupNow(SecureButton button) {
        clearErrors();
        String defaultName = "hrms-backup-"
                + java.time.LocalDateTime.now()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".sql";
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setSelectedFile(new java.io.File(defaultName));
        chooser.setDialogTitle("Choose where to save the backup");
        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.nio.file.Path target = chooser.getSelectedFile().toPath();
        button.setEnabled(false);
        statusLabel.setText("Backing up the database...");
        controller.backupTo(target,
                file -> {
                    button.setEnabled(true);
                    statusLabel.setText(" ");
                    Toast.show(swungWindow(), Toast.Type.SUCCESS,
                            "Backup saved to " + file);
                },
                error -> {
                    button.setEnabled(true);
                    statusLabel.setText(" ");
                    showError(errorMessage(error));
                });
    }

    private void restoreFromFile(SecureButton button) {
        clearErrors();
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setDialogTitle("Choose a backup file to restore");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "SQL backup files (*.sql)", "sql"));
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.nio.file.Path source = chooser.getSelectedFile().toPath();
        if (!com.ams.hrms.util.Dialogs.confirm(this, "Restore Database",
                "Everything currently in the database will be REPLACED by the\n"
                        + "contents of:\n\n    " + source + "\n\n"
                        + "Changes made after this backup will be permanently lost.\n"
                        + "Restore now?")) {
            return;
        }
        button.setEnabled(false);
        saveButton.setEnabled(false);
        statusLabel.setText("Restoring the database...");
        controller.restoreFrom(source,
                () -> {
                    button.setEnabled(true);
                    saveButton.setEnabled(true);
                    statusLabel.setText(" ");
                    Toast.show(swungWindow(), Toast.Type.SUCCESS,
                            "Restore complete.");
                    reload();
                },
                error -> {
                    button.setEnabled(true);
                    saveButton.setEnabled(true);
                    statusLabel.setText(" ");
                    showError(errorMessage(error));
                });
    }

    private static JLabel sectionTitle(String text) {
        JLabel label = new JLabel(text.toUpperCase());
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        return label;
    }

    private static JLabel mutedLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 11f));
        label.setForeground(Palette.color(Role.TEXT_MUTED));
        return label;
    }

    private static String errorMessage(Exception error) {
        if (error instanceof com.ams.hrms.exception.HrmsException hrms) {
            return hrms.getUserMessage();
        }
        com.ams.hrms.exception.ErrorHandler.handle(error);
        return "Unexpected error - see the details dialog.";
    }

    // ------------------------------------------------------------------
    // Save
    // ------------------------------------------------------------------

    private void save() {
        clearErrors();
        Map<String, String> values = new LinkedHashMap<>();
        editors.forEach((key, field) -> values.put(key, field.getText()));

        saveButton.setEnabled(false);
        controller.saveChanges(values,
                count -> {
                    saveButton.setEnabled(true);
                    Toast.show(swungWindow(), Toast.Type.SUCCESS,
                            count == 0 ? "No changes to save."
                                    : count + " setting(s) saved.");
                    reload();
                },
                error -> {
                    saveButton.setEnabled(true);
                    if (error instanceof ValidationException ve) {
                        showError(String.join(" ", ve.getErrors()));
                    } else {
                        ErrorHandler.handle(error);
                    }
                });
    }

    private int unsavedCount() {
        int count = 0;
        for (Map.Entry<String, FormField> entry : editors.entrySet()) {
            if (!entry.getValue().getText()
                    .equals(originalValues.getOrDefault(entry.getKey(), ""))) {
                count++;
            }
        }
        return count;
    }

    private void updateStatus() {
        int dirty = unsavedCount();
        statusLabel.setText(dirty == 0 ? " "
                : "You have " + dirty + " unsaved change(s).");
    }

    private void clearErrors() {
        errorBanner.setVisible(false);
    }

    private void showError(String message) {
        errorBanner.setText(message);
        errorBanner.setVisible(true);
        revalidate();
        repaint();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static Map<String, String> buildCategoryTitles() {
        Map<String, String> titles = new LinkedHashMap<>();
        titles.put("COMPANY", "Company");
        titles.put("PAYROLL", "Payroll");
        titles.put("ATTENDANCE", "Attendance");
        titles.put("LEAVE", "Leave");
        titles.put("DOCUMENTS", "Documents");
        titles.put("GENERAL", "General");
        return titles;
    }

    private Map<String, List<AppSetting>> groupByCategory(List<AppSetting> settings) {
        Map<String, List<AppSetting>> groups = new LinkedHashMap<>();
        for (String category : CATEGORY_TITLES.keySet()) {
            groups.put(category, new ArrayList<>());
        }
        for (AppSetting setting : settings) {
            groups.computeIfAbsent(setting.getCategory(), k -> new ArrayList<>())
                    .add(setting);
        }
        groups.values().removeIf(List::isEmpty);
        return groups;
    }

    private static String titleCase(String category) {
        if (category == null || category.isBlank()) {
            return category;
        }
        return Character.toUpperCase(category.charAt(0))
                + category.substring(1).toLowerCase();
    }

    private java.awt.Window swungWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
