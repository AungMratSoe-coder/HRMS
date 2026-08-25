package com.ams.hrms.tools;

import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import com.ams.hrms.component.DashboardCard;
import com.ams.hrms.component.DatePickerField;
import com.ams.hrms.component.EmptyStatePanel;
import com.ams.hrms.component.ErrorPanel;
import com.ams.hrms.component.FormField;
import com.ams.hrms.component.HeaderPanel;
import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.LoadingPanel;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.PaginationPanel;
import com.ams.hrms.component.SearchField;
import com.ams.hrms.component.SidebarMenuPanel;
import com.ams.hrms.ui.main.MenuDefinition;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.ui.theme.ThemeManager;
import com.ams.hrms.ui.theme.ThemeManager.Theme;

import net.miginfocom.swing.MigLayout;

/**
 * Development-only visual verification harness (like HashTool, not part of
 * the shipped application). Builds a full-frame showcase of every reusable
 * component, captures light and dark screenshots into {@code screenshots/},
 * and exits. Used to verify UI rendering quality after component changes.
 */
public final class UiSmokeTool {

    private static final java.util.List<SidebarMenuPanel.MenuItem> MENU = MenuDefinition.ALL;

    public static void main(String[] args) throws Exception {
        ThemeManager.install();

        JFrame frame = new JFrame("HRMS - UI Verification");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1280, 840);
        frame.setLocationRelativeTo(null);

        HeaderPanel header = new HeaderPanel();
        header.setTitle("Dashboard");
        header.setUser("Aung Kyaw", "HR Manager");
        header.setUnreadNotifications(3);
        header.refreshThemeIcon(ThemeManager.isDark());
        header.onThemeToggle(() -> {
            ThemeManager.toggle();
            header.refreshThemeIcon(ThemeManager.isDark());
        });
        header.onMenuToggle(() -> { /* sidebar wired below */ });

        SidebarMenuPanel sidebar = new SidebarMenuPanel(MENU);
        header.onMenuToggle(sidebar::toggleExpanded);

        JPanel showcase = buildShowcase(frame);

        JPanel content = new JPanel(new java.awt.BorderLayout());
        content.setOpaque(false);
        content.add(header, java.awt.BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(showcase);
        scrollPane.setBorder(null);
        content.add(scrollPane, java.awt.BorderLayout.CENTER);

        frame.setLayout(new java.awt.BorderLayout());
        frame.add(sidebar, java.awt.BorderLayout.WEST);
        frame.add(content, java.awt.BorderLayout.CENTER);

        sidebar.onSelection(id -> System.out.println("menu selected: " + id));
        sidebar.select("dashboard");

        frame.setVisible(true);

        // Let layout + fonts settle before capturing.
        Thread.sleep(900);
        capture(frame, "screenshots/ui-light.png");

        SwingUtilities.invokeAndWait(() -> {
            ThemeManager.setTheme(Theme.DARK);
            header.refreshThemeIcon(true);
        });
        Thread.sleep(700);
        capture(frame, "screenshots/ui-dark.png");

        SwingUtilities.invokeAndWait(frame::dispose);
        System.out.println("Screenshots captured.");
        System.exit(0);
    }

    private static JPanel buildShowcase(JFrame frame) {
        JPanel showcase = new JPanel(new MigLayout(
                "wrap 1, insets 20 24, gap 16",
                "[grow,fill]",
                "[][][][grow,fill][]"));

        // --- Stat cards -------------------------------------------------
        JPanel cards = new JPanel(new MigLayout("insets 0, gap 14", "[grow][grow][grow][grow]"));
        DashboardCard total = new DashboardCard("Total Employees", "employees", Role.ACCENT);
        total.setValue("128");
        total.setDelta("5 this month", true);
        DashboardCard present = new DashboardCard("Present Today", "attendance", Role.SUCCESS);
        present.setValue("112");
        present.setDelta("87.5%", true);
        DashboardCard onLeave = new DashboardCard("On Leave", "leave", Role.WARNING);
        onLeave.setValue("9");
        DashboardCard pending = new DashboardCard("Pending Leaves", "bell", Role.DANGER);
        pending.setValue("14");
        pending.setDelta("4 overdue", false);
        cards.add(total);
        cards.add(present);
        cards.add(onLeave);
        cards.add(pending);
        cards.setOpaque(false);
        showcase.add(cards, "height 112!, wrap");

        // --- Table + pagination ------------------------------------------
        HrmsTable table = HrmsTable.builder("Code", "Name", "Department", "Position", "Status")
                .fixedColumn(0, 90)
                .badgeColumn(4)
                .build();
        table.setRows(List.of(
                new Object[]{"EMP-0001", "Aung Kyaw", "IT", "IT Manager", "ACTIVE"},
                new Object[]{"EMP-0002", "Su Su Hlaing", "HR", "HR Manager", "ACTIVE"},
                new Object[]{"EMP-0003", "Kyaw Kyaw Win", "IT", "Software Developer", "PENDING"},
                new Object[]{"EMP-0004", "Thiri Aung", "FIN", "Finance Manager", "ACTIVE"},
                new Object[]{"EMP-0005", "Myint Mo Tun", "HR", "HR Officer", "REJECTED"},
                new Object[]{"EMP-0006", "Zaw Zaw Lwin", "SAL", "Sales Executive", "TERMINATED"}));

        PaginationPanel pagination = new PaginationPanel();
        pagination.onPageChange(page -> System.out.println("page -> " + page));
        pagination.updateTotal(137);

        JPanel tableCard = new JPanel(new MigLayout("wrap 1, insets 14 16, gap 10",
                "[grow,fill]", "[][grow,fill][]"));
        tableCard.setOpaque(false);
        tableCard.add(buildToolbar(table), "growx");
        tableCard.add(new JScrollPane(table), "height 190::");
        tableCard.add(pagination, "alignx left");
        showcase.add(tableCard);

        // --- Form controls -----------------------------------------------
        JPanel forms = new JPanel(new MigLayout("insets 0, gap 14", "[grow][grow][grow]"));
        FormField nameField = FormField.textField("Employee Name", true);
        nameField.setText("Aung Kyaw");
        FormField typeField = FormField.comboBox("Employment Type",
                new String[]{"FULL_TIME", "PART_TIME", "CONTRACT"}, false);
        FormField joinDate = FormField.datePicker("Join Date", true);
        joinDate.setDate(java.time.LocalDate.now());
        FormField notes = FormField.textArea("Remarks", false);
        FormField broken = FormField.textField("Email", true);
        broken.setError("Email address is not valid");
        forms.add(nameField, "width 220!");
        forms.add(typeField, "width 200!");
        forms.add(joinDate, "width 200!");
        forms.add(notes, "width 260!, height 90!");
        forms.add(broken, "width 220!");
        forms.setOpaque(false);
        showcase.add(forms);

        // --- State panels --------------------------------------------------
        JPanel states = new JPanel(new MigLayout("insets 0, gap 14", "[grow][grow][grow]", "[120!]"));
        JPanel loadingCard = card(new LoadingPanel("Loading employees..."));
        JPanel emptyCard = card(new EmptyStatePanel("documents", "No documents yet",
                "Uploaded employee documents will appear here."));
        JPanel errorCard = card(new ErrorPanel("Could not reach the server. Check your connection.",
                () -> System.out.println("retry clicked")));
        states.add(loadingCard, "grow");
        states.add(emptyCard, "grow");
        states.add(errorCard, "grow");
        states.setOpaque(false);
        showcase.add(states);

        // --- Buttons + toast trigger ---------------------------------------
        JPanel buttons = new JPanel(new MigLayout("insets 0, gap 10"));
        buttons.add(new ModernButton("New Employee", "plus"));
        buttons.add(new ModernButton("Save", "check", ModernButton.Variant.SUCCESS));
        buttons.add(new ModernButton("Delete", "trash", ModernButton.Variant.DANGER));
        buttons.add(new ModernButton("Export", "export", ModernButton.Variant.OUTLINE));
        buttons.add(new ModernButton("Refresh", "refresh", ModernButton.Variant.GHOST));
        JButton toastButton = new ModernButton("Show toast");
        toastButton.addActionListener(event -> com.ams.hrms.component.Toast.show(
                frame, com.ams.hrms.component.Toast.Type.SUCCESS, "Employee saved successfully"));
        buttons.add(toastButton);
        buttons.setOpaque(false);
        showcase.add(buttons, "alignx left");

        showcase.setOpaque(false);
        return showcase;
    }

    private static JPanel buildToolbar(HrmsTable table) {
        JPanel toolbar = new JPanel(new net.miginfocom.swing.MigLayout(
                "insets 0, gap 10", "[grow,fill][]"));
        toolbar.setOpaque(false);
        SearchField search = new SearchField("Search by name, code, phone...");
        search.onTextChanged(text -> System.out.println("search -> " + text));
        toolbar.add(search);
        toolbar.add(new ModernButton("New Employee", "plus"));
        return toolbar;
    }

    private static JPanel card(JPanel inner) {
        JPanel card = new JPanel(new java.awt.BorderLayout());
        card.add(inner);
        return card;
    }

    private static void capture(JFrame frame, String relativePath) throws Exception {
        File out = new File(relativePath);
        out.getParentFile().mkdirs();
        Rectangle bounds = frame.getBounds();
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
        Robot robot = new Robot(device);
        BufferedImage image = robot.createScreenCapture(bounds);
        ImageIO.write(image, "png", out);
        System.out.println("captured " + out.getAbsolutePath());
    }
}
