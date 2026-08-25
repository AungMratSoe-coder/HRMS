package com.ams.hrms.ui.onboarding;

import java.awt.BorderLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.OnboardingController;
import com.ams.hrms.dto.OnboardingProgress;
import com.ams.hrms.model.Employee;
import com.ams.hrms.model.OnboardingTask;
import com.ams.hrms.model.OnboardingTemplate;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

/**
 * Onboarding module (spec section 15): per-employee checklists with live
 * completion progress and the reusable template catalogue that feeds future
 * hires. New hires receive their checklist automatically at hire time.
 */
public class OnboardingPanel extends JPanel {

    private final OnboardingController controller =
            new OnboardingController(ServiceRegistry.onboardingService());

    // --- checklist tab ---
    private final JComboBox<String> employeeCombo = new JComboBox<>();
    private final List<Employee> employees = new ArrayList<>();
    private final JLabel progressLabel = new JLabel(" ");
    private final JProgressBar progressBar = new JProgressBar(0, 100);
    private final SecureButton generateButton =
            new SecureButton("Generate Checklist", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.ONBOARDING_MANAGE);
    private final HrmsTable taskTable = HrmsTable.builder(
                    "ID", "#", "Task", "Mandatory", "Due", "Status",
                    "Completed At", "Completed By")
            .hiddenColumn(0)
            .fixedColumn(1, 40)
            .fixedColumn(3, 85)
            .fixedColumn(4, 95)
            .fixedColumn(5, 105)
            .fixedColumn(6, 150)
            .fixedColumn(7, 160)
            .badgeColumn(5)
            .contextMenu(this::buildTaskMenu)
            .build();
    private List<OnboardingTask> loadedTasks = List.of();

    // --- templates tab ---
    private final SecureButton newTemplateButton =
            new SecureButton("New Template", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.ONBOARDING_MANAGE);
    private final HrmsTable templateTable = HrmsTable.builder(
                    "ID", "#", "Task", "Description", "Mandatory", "Active")
            .hiddenColumn(0)
            .fixedColumn(1, 40)
            .fixedColumn(4, 85)
            .fixedColumn(5, 75)
            .contextMenu(this::buildTemplateMenu)
            .build();
    private List<OnboardingTemplate> loadedTemplates = List.of();

    public OnboardingPanel() {
        super(new BorderLayout());
        setOpaque(false);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Checklists", buildChecklistTab());
        tabs.addTab("Templates", buildTemplatesTab());
        add(tabs, BorderLayout.CENTER);

        wireEvents();
        loadEmployees();
        refreshTemplates();
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private JPanel buildChecklistTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10", "[][][grow,fill][][]"));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("Employee:"), "gapright 4");
        toolbar.add(employeeCombo, "width 320!");
        toolbar.add(progressLabel, "gapleft 16, gapright 8");
        toolbar.add(progressBar, "width 160!");
        toolbar.add(generateButton);

        progressBar.setStringPainted(true);

        tab.add(toolbar, BorderLayout.NORTH);
        tab.add(new JScrollPane(taskTable), BorderLayout.CENTER);
        return tab;
    }

    private JPanel buildTemplatesTab() {
        JPanel tab = new JPanel(new BorderLayout());
        tab.setOpaque(false);

        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 16 20 10 20, gap 10"));
        toolbar.setOpaque(false);
        toolbar.add(new JLabel("Checklist templates feed every new hire; "
                + "changes never rewrite existing checklists."), "gapright 20");
        toolbar.add(newTemplateButton, "pushx, alignx right");

        tab.add(toolbar, BorderLayout.NORTH);
        tab.add(new JScrollPane(templateTable), BorderLayout.CENTER);
        return tab;
    }

    private void wireEvents() {
        employeeCombo.addActionListener(event -> refreshTasks());
        generateButton.addActionListener(event -> generateChecklist());
        newTemplateButton.addActionListener(event -> openTemplateDialog(null));
    }

    // ------------------------------------------------------------------
    // Data
    // ------------------------------------------------------------------

    private void loadEmployees() {
        controller.loadEmployees(result -> {
            employees.clear();
            employeeCombo.removeAllItems();
            result.stream()
                    .filter(employee -> "ACTIVE".equals(employee.getStatus()))
                    .forEach(employee -> {
                        employees.add(employee);
                        employeeCombo.addItem(
                                employee.getCode() + " - " + employee.getFullName());
                    });
            refreshTasks();
        });
    }

    private Employee selectedEmployee() {
        int index = employeeCombo.getSelectedIndex();
        return index < 0 || index >= employees.size() ? null : employees.get(index);
    }

    private void refreshTasks() {
        Employee employee = selectedEmployee();
        if (employee == null) {
            taskTable.setRows(List.of());
            updateProgress(OnboardingProgress.from(List.of()));
            return;
        }
        controller.loadTasks(employee.getId(), tasks -> {
            loadedTasks = tasks;
            List<Object[]> rows = new ArrayList<>();
            for (var task : tasks) {
                rows.add(new Object[]{
                        task.getId(),
                        task.getTaskOrder(),
                        task.getTaskName(),
                        task.isMandatory() ? "Yes" : "No",
                        task.getDueDate() == null ? "-" : task.getDueDate(),
                        task.getStatus(),
                        task.getCompletedAt() == null ? "-"
                                : task.getCompletedAt().format(java.time.format
                                        .DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                        task.getCompletedByName() == null ? "-" : task.getCompletedByName()});
            }
            taskTable.setRows(rows);
            updateProgress(OnboardingProgress.from(tasks));
            generateButton.setVisible(tasks.isEmpty());
        });
    }

    private void updateProgress(OnboardingProgress progress) {
        progressBar.setValue(progress.percentComplete());
        if (progress.total() == 0) {
            progressLabel.setText("No checklist yet");
            progressBar.setString("0%");
            return;
        }
        String summary = progress.completed() + "/" + progress.total() + " done";
        if (progress.mandatoryOutstanding() > 0) {
            summary += " - " + progress.mandatoryOutstanding() + " mandatory left";
        } else {
            summary += " - complete";
        }
        progressLabel.setText(summary);
        progressBar.setString(progress.percentComplete() + "%");
    }

    private void refreshTemplates() {
        controller.loadTemplates(templates -> {
            loadedTemplates = templates;
            List<Object[]> rows = new ArrayList<>();
            for (var template : templates) {
                rows.add(new Object[]{
                        template.getId(),
                        template.getTaskOrder(),
                        template.getTaskName(),
                        template.getDescription() == null ? "-" : template.getDescription(),
                        template.isMandatory() ? "Yes" : "No",
                        template.isActive() ? "ACTIVE" : "INACTIVE"});
            }
            templateTable.setRows(rows);
        });
    }

    private OnboardingTask selectedTask() {
        Object value = taskTable.selectedValue(0);
        if (value == null) {
            return null;
        }
        long target = ((Number) value).longValue();
        return loadedTasks.stream()
                .filter(task -> task.getId() != null && task.getId() == target)
                .findFirst().orElse(null);
    }

    private OnboardingTemplate selectedTemplate() {
        Object value = templateTable.selectedValue(0);
        if (value == null) {
            return null;
        }
        long target = ((Number) value).longValue();
        return loadedTemplates.stream()
                .filter(template -> template.getId() != null && template.getId() == target)
                .findFirst().orElse(null);
    }

    // ------------------------------------------------------------------
    // Checklist actions
    // ------------------------------------------------------------------

    private void generateChecklist() {
        Employee employee = selectedEmployee();
        if (employee == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), "Generate Checklist",
                "Create an onboarding checklist for " + employee.getFullName()
                        + " from the active templates (due in "
                        + com.ams.hrms.service.OnboardingService.DEFAULT_DUE_DAYS
                        + " days)?");
        if (!confirmed) {
            return;
        }
        controller.generateChecklist(employee.getId(), LocalDate.now().plusDays(
                        com.ams.hrms.service.OnboardingService.DEFAULT_DUE_DAYS),
                this::refreshTasks,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private void setTaskStatus(OnboardingTask task, String status) {
        boolean confirmed = Dialogs.confirm(swingWindow(), "Update Task",
                "Set '" + task.getTaskName() + "' to " + status + "?");
        if (!confirmed) {
            return;
        }
        controller.setTaskStatus(task.getId(), status, this::refreshTasks,
                error -> com.ams.hrms.exception.ErrorHandler.handle(error));
    }

    private JPopupMenu buildTaskMenu() {
        JPopupMenu menu = new JPopupMenu();
        var task = selectedTask();
        boolean manage = SecurityService.can(Permissions.ONBOARDING_MANAGE);
        boolean pending = task != null && task.isPending();

        JMenuItem complete = new JMenuItem("Mark Completed");
        complete.setEnabled(pending && manage);
        complete.addActionListener(event -> setTaskStatus(selectedTask(),
                OnboardingTask.STATUS_COMPLETED));

        JMenuItem skip = new JMenuItem("Skip");
        skip.setEnabled(pending && manage);
        skip.addActionListener(event -> setTaskStatus(selectedTask(),
                OnboardingTask.STATUS_SKIPPED));

        JMenuItem waive = new JMenuItem("Waive");
        waive.setEnabled(pending && manage);
        waive.addActionListener(event -> setTaskStatus(selectedTask(),
                OnboardingTask.STATUS_WAIVED));

        JMenuItem reopen = new JMenuItem("Reopen");
        reopen.setEnabled(task != null && !pending && manage);
        reopen.addActionListener(event -> setTaskStatus(selectedTask(),
                OnboardingTask.STATUS_PENDING));

        menu.add(complete);
        menu.add(skip);
        menu.add(waive);
        menu.addSeparator();
        menu.add(reopen);
        return menu;
    }

    // ------------------------------------------------------------------
    // Template actions
    // ------------------------------------------------------------------

    private void openTemplateDialog(OnboardingTemplate existing) {
        TemplateDialog dialog = new TemplateDialog(swingWindow(), existing);
        if (dialog.showDialog() == TemplateDialog.Result.SAVED) {
            refreshTemplates();
        }
    }

    private JPopupMenu buildTemplateMenu() {
        JPopupMenu menu = new JPopupMenu();
        var template = selectedTemplate();
        boolean manage = SecurityService.can(Permissions.ONBOARDING_MANAGE);

        JMenuItem edit = new JMenuItem("Edit Template");
        edit.setEnabled(template != null && manage);
        edit.addActionListener(event -> openTemplateDialog(selectedTemplate()));

        JMenuItem deactivate = new JMenuItem("Deactivate");
        deactivate.setEnabled(template != null && template.isActive() && manage);
        deactivate.addActionListener(event -> changeTemplateActive(false));

        JMenuItem activate = new JMenuItem("Activate");
        activate.setEnabled(template != null && !template.isActive() && manage);
        activate.addActionListener(event -> changeTemplateActive(true));

        menu.add(edit);
        menu.addSeparator();
        menu.add(activate);
        menu.add(deactivate);
        return menu;
    }

    private void changeTemplateActive(boolean active) {
        var template = selectedTemplate();
        if (template == null) {
            return;
        }
        boolean confirmed = Dialogs.confirm(swingWindow(), active ? "Activate" : "Deactivate",
                (active ? "Activate" : "Deactivate") + " template '"
                        + template.getTaskName() + "'?");
        if (!confirmed) {
            return;
        }
        UiThread.runAsync("Update template status", () ->
                        ServiceRegistry.onboardingService()
                                .setTemplateActive(template.getId(), active));
        UiThread.runLater(this::refreshTemplates);
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }
}
