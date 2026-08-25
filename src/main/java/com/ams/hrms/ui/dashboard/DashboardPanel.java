package com.ams.hrms.ui.dashboard;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

import org.jfree.chart.ChartPanel;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import com.ams.hrms.component.DashboardCard;
import com.ams.hrms.component.EmptyStatePanel;
import com.ams.hrms.component.LoadingPanel;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.controller.DashboardController;
import com.ams.hrms.controller.PayrollController;
import com.ams.hrms.dto.CategoryCount;
import com.ams.hrms.dto.DashboardData;
import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.repository.PayrollRepository;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.ui.theme.ThemeManager;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.util.UiGraphics;
import com.ams.hrms.util.UiThread;

import net.miginfocom.swing.MigLayout;

/**
 * Main HR dashboard (spec section 9): greeting, eight live stat cards, the
 * latest payroll strip and five charts. Data loads off the EDT; the panel
 * rebuilds itself on theme switches and reloads when modules publish
 * DataChanged("dashboard").
 */
public class DashboardPanel extends JPanel {

    private DashboardData lastData;
    private boolean loading;

    private final DashboardController controller =
            new DashboardController(ServiceRegistry.dashboardService());

    private final Consumer<ThemeManager.Theme> themeListener =
            theme -> {
                // Runs on the EDT during ThemeManager.setTheme, before the
                // crossfade starts - rebuild synchronously so the charts are
                // already restyled when the fade begins.
                if (lastData != null) {
                    render(lastData);
                }
            };

    private final Consumer<Events.DataChanged> dataListener = event -> {
        if ("dashboard".equals(event.scope())) {
            reload();
        }
    };

    public DashboardPanel() {
        super(new BorderLayout());
        add(new LoadingPanel("Loading dashboard..."), BorderLayout.CENTER);
        controller.load(this::render);
    }

    @Override
    public void addNotify() {
        super.addNotify();
        ThemeManager.addListener(themeListener);
        EventBus.subscribe(Events.DataChanged.class, dataListener);
    }

    @Override
    public void removeNotify() {
        EventBus.unsubscribe(Events.DataChanged.class, dataListener);
        ThemeManager.removeListener(themeListener);
        super.removeNotify();
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    private void render(DashboardData data) {
        lastData = data;
        loading = false;
        removeAll();

        JScrollPane scroll = new JScrollPane(buildContent(data));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private void reload() {
        if (loading) {
            return;
        }
        loading = true;
        controller.load(this::render);
    }

    private JPanel buildContent(DashboardData data) {
        JPanel content = new ScrollTrackingPanel(new MigLayout(
                "wrap, insets 22 26, gapx 16, gapy 20",
                "[grow,fill]",
                // One constraint per row - a trailing "push" on the last row
                // keeps the gaps between the chart sections fixed at gapy.
                "[][][][][][]push"));

        content.add(buildHeader(), "growx");
        content.add(buildStatCards(data), "growx, wrap");
        content.add(buildPayrollStrip(data), "growx, wrap");
        content.add(buildDepartmentAndStatusCharts(data), "growx, wrap");
        content.add(buildAttendanceTrendSection(data), "growx, wrap");
        content.add(buildLeaveAndPayrollCharts(data), "growx");

        content.setBackground(Palette.color(Role.SURFACE_ALT));
        return content;
    }

    // ------------------------------------------------------------------
    // Header
    // ------------------------------------------------------------------

    private JPanel buildHeader() {
        JPanel header = new JPanel(new MigLayout("insets 0, gap 4", "[grow,fill][]"));
        header.setOpaque(false);

        JLabel greeting = new JLabel(greetingText());
        greeting.setFont(greeting.getFont().deriveFont(Font.BOLD, 22f));
        greeting.setForeground(Palette.color(Role.TEXT));

        JLabel subline = new JLabel("Signed in as "
                + SessionContext.currentUser().username()
                + " · " + SessionContext.primaryRoleName());
        subline.setFont(subline.getFont().deriveFont(Font.PLAIN, 13f));
        subline.setForeground(Palette.color(Role.TEXT_MUTED));

        JButton refresh = ModernButton.iconOnly("refresh", "Reload dashboard");
        refresh.addActionListener(event -> reload());

        JPanel titleStack = new JPanel(new MigLayout("wrap 1, insets 0, gap 2"));
        titleStack.setOpaque(false);
        titleStack.add(greeting);
        titleStack.add(subline);

        header.add(titleStack, "growx");
        header.add(refresh, "aligny top");
        return header;
    }

    private String greetingText() {
        var user = SessionContext.currentUser();
        String firstName = user.fullName().isBlank()
                ? user.username()
                : user.fullName().split("\\s+")[0];
        int hour = java.time.LocalTime.now().getHour();
        if (hour < 12) {
            return "Good morning, " + firstName;
        }
        return hour < 18 ? "Good afternoon, " + firstName : "Good evening, " + firstName;
    }

    // ------------------------------------------------------------------
    // Stat cards
    // ------------------------------------------------------------------

    private JPanel buildStatCards(DashboardData data) {
        var stats = data.stats();

        DashboardCard total = new DashboardCard("Total Employees", "employees", Role.ACCENT);
        total.setValue(String.valueOf(stats.totalEmployees()));

        DashboardCard active = new DashboardCard("Active", "check", Role.SUCCESS);
        active.setValue(String.valueOf(stats.activeEmployees()));
        active.setBadge("check");

        DashboardCard fresh = new DashboardCard("New This Month", "plus", Role.WARNING);
        fresh.setValue(String.valueOf(stats.newEmployeesThisMonth()));

        DashboardCard onLeave = new DashboardCard("On Leave Today", "leave", Role.WARNING);
        onLeave.setValue(String.valueOf(stats.onLeaveToday()));

        DashboardCard present = new DashboardCard("Present Today", "attendance", Role.SUCCESS);
        present.setValue(String.valueOf(stats.presentToday()));
        present.setBadge("present");

        DashboardCard late = new DashboardCard("Late Today", "attendance", Role.WARNING);
        late.setValue(String.valueOf(stats.lateToday()));
        late.setBadge("delay");

        DashboardCard absent = new DashboardCard("Absent Today", "close", Role.DANGER);
        absent.setValue(String.valueOf(stats.absentToday()));
        absent.setBadge("absent");

        DashboardCard pending = new DashboardCard("Pending Leaves", "bell", Role.INFO);
        pending.setValue(String.valueOf(stats.pendingLeaveRequests()));
        pending.setBadge("pending");

        JPanel grid = new JPanel(new MigLayout(
                "insets 0, gapx 14, gapy 14, wrap 4", "[grow][grow][grow][grow]"));
        grid.setOpaque(false);
        grid.add(total);
        grid.add(active);
        grid.add(fresh);
        grid.add(onLeave);
        grid.add(present);
        grid.add(late);
        grid.add(absent);
        grid.add(pending);
        return grid;
    }

    private JPanel buildPayrollStrip(DashboardData data) {
        PayrollStripPanel strip = new PayrollStripPanel(data);
        JPanel wrapper = new JPanel(new MigLayout("insets 0", "[grow,fill]"));
        wrapper.setOpaque(false);
        wrapper.add(strip, "height 112!");
        return wrapper;
    }

    /**
     * "Latest Payroll" banner matching the reference design: icon chip and
     * title on the left, a period selector on the top right, a summary line
     * and a "Process Now" shortcut that opens the Payroll module.
     */
    private static final class PayrollStripPanel extends JPanel {

        private final DashboardData data;
        private final JComboBox<String> periodCombo = new JComboBox<>();
        private final JLabel summaryLabel = new JLabel(" ");

        private PayrollStripPanel(DashboardData data) {
            super(new MigLayout("insets 18 20, gapx 8, gapy 14",
                    "[grow,fill][]", "[center][center]"));
            this.data = data;
            setOpaque(false);

            JLabel title = new JLabel("LATEST PAYROLL");
            title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
            title.setForeground(Palette.color(Role.TEXT_MUTED));

            summaryLabel.setFont(summaryLabel.getFont().deriveFont(Font.PLAIN, 12.5f));
            summaryLabel.setForeground(Palette.color(Role.TEXT_MUTED));

            periodCombo.setEnabled(false);
            periodCombo.addActionListener(event -> updateSummary());

            ModernButton process = new ModernButton("Process Now", ModernButton.Variant.OUTLINE);
            process.setToolTipText("Open the Payroll module");
            process.addActionListener(event ->
                    EventBus.publish(new Events.NavigateRequest("payroll")));

            add(title, "gapleft 52");

            // The period selector reads the gated payroll service; viewers
            // without PAYROLL_VIEW (e.g. self-service employees) keep the
            // summary line only, so no access-denied dialog fires on login.
            if (SessionContext.has(Permissions.PAYROLL_VIEW)) {
                add(periodCombo, "w 170!");
                add(summaryLabel, "gapleft 52");
                add(process);
                loadPeriods();
            } else {
                add(summaryLabel, "gapleft 52, span 2, growx");
            }

            updateSummary();
        }

        /** Summary line: full detail for the latest period, plain otherwise. */
        private void updateSummary() {
            if (data.lastPayrollPeriod() == null) {
                summaryLabel.setText("No payroll processed yet");
                return;
            }
            Object selected = periodCombo.getSelectedItem();
            if (selected == null || selected.equals(data.lastPayrollPeriod())) {
                summaryLabel.setText(data.lastPayrollPeriod() + " Payroll - All departments · Net "
                        + data.formattedMoney(data.lastPayrollNet())
                        + " / Gross " + data.formattedMoney(data.lastPayrollGross()));
            } else {
                summaryLabel.setText(selected + " Payroll - All departments");
            }
        }

        /** Fills the period selector from the payroll service (newest first). */
        private void loadPeriods() {
            new PayrollController(ServiceRegistry.payrollService())
                    .loadPeriods(periods -> {
                        periodCombo.removeAllItems();
                        if (periods.isEmpty()) {
                            periodCombo.addItem("No period yet");
                            periodCombo.setEnabled(false);
                            return;
                        }
                        for (PayrollRepository.Period period : periods) {
                            periodCombo.addItem(period.periodName());
                        }
                        periodCombo.setEnabled(true);
                        periodCombo.setSelectedIndex(0);
                        updateSummary();
                    });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            Color background = Palette.statCardBackground(Role.ACCENT);
            Color border = Palette.statCardBorder(Role.ACCENT);
            if (!Palette.isDarkUi()) {
                UiGraphics.fillRoundRect(g2, 3, 4, getWidth() - 6, getHeight() - 4, 14,
                        new Color(15, 23, 42, 18));
            }
            UiGraphics.fillRoundRect(g2, 0, 0, getWidth(), getHeight(), 14, background);
            UiGraphics.drawRoundRect(g2, 0, 0, getWidth(), getHeight(), 14, border);
            UiGraphics.fillRoundRect(g2, 20, 20, 42, 42, 10,
                    UiGraphics.blend(background, border, 0.28));
            IconLoader.tinted("payroll", 22, border).paintIcon(this, g2, 30, 30);
            g2.dispose();
        }
    }

    // ------------------------------------------------------------------
    // Charts
    // ------------------------------------------------------------------

    private JPanel buildDepartmentAndStatusCharts(DashboardData data) {
        JPanel row = new JPanel(new MigLayout("insets 0, gap 20", "[grow,50%][grow,50%]"));
        row.setOpaque(false);

        DefaultCategoryDataset deptDataset = new DefaultCategoryDataset();
        for (var entry : data.employeesByDepartment()) {
            deptDataset.addValue(entry.count(), "Employees", entry.label());
        }
        row.add(ChartTheme.sectionCard("Employees by Department",
                ChartTheme.wrap(ChartTheme.barChart("Employees", deptDataset))),
                "grow");

        DefaultPieDataset statusDataset = new DefaultPieDataset();
        for (var entry : data.employeesByStatus()) {
            statusDataset.setValue(prettify(entry.label()), entry.count());
        }
        JPanel statusRow = new JPanel(new MigLayout("insets 0, gap 12", "[grow,fill][]"));
        statusRow.setOpaque(false);
        statusRow.add(ChartTheme.wrap(ChartTheme.donutChart(statusDataset),
                ChartTheme.DONUT_PREFERRED_WIDTH, ChartTheme.CHART_PREFERRED_HEIGHT), "grow");
        statusRow.add(buildStatusLegend(data.employeesByStatus()), "top");
        row.add(ChartTheme.sectionCard("Employee Status", statusRow), "grow");
        return row;
    }

    /** Swing legend with colored dots and per-status counts (donut companion). */
    private JPanel buildStatusLegend(List<CategoryCount> entries) {
        JPanel legend = new JPanel(new MigLayout("wrap 1, insets 2 0, gap 12"));
        legend.setOpaque(false);
        for (int i = 0; i < entries.size(); i++) {
            CategoryCount entry = entries.get(i);
            int colorIndex = i;
            JPanel line = new JPanel(new MigLayout("insets 0, gap 8"));
            line.setOpaque(false);
            JPanel dot = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    g.setColor(ChartTheme.pieSliceColor(entry.label(), colorIndex));
                    g.fillOval(0, 0, 10, 10);
                }
            };
            dot.setOpaque(false);
            dot.setPreferredSize(new Dimension(10, 10));
            JLabel name = new JLabel(prettify(entry.label()));
            name.setForeground(Palette.color(Role.TEXT));
            JLabel count = new JLabel(String.valueOf(entry.count()));
            count.setForeground(Palette.color(Role.TEXT));
            count.setFont(count.getFont().deriveFont(Font.BOLD));
            line.add(dot);
            line.add(name);
            line.add(count, "gapleft 4");
            legend.add(line);
        }
        return legend;
    }

    private JPanel buildAttendanceTrendSection(DashboardData data) {
        int totalMarked = 0;
        for (var day : data.attendanceTrend()) {
            totalMarked += day.present() + day.late() + day.absent();
        }
        if (totalMarked == 0) {
            return emptySection("Attendance - Last 14 Days", "attendance",
                    "No attendance recorded in the last 14 days.");
        }
        TimeSeriesCollection dataset = new TimeSeriesCollection();
        TimeSeries present = new TimeSeries("Present");
        TimeSeries late = new TimeSeries("Late");
        TimeSeries absent = new TimeSeries("Absent");
        for (var day : data.attendanceTrend()) {
            Day chartDay = new Day(day.date().getDayOfMonth(),
                    day.date().getMonthValue(), day.date().getYear());
            present.add(chartDay, day.present());
            late.add(chartDay, day.late());
            absent.add(chartDay, day.absent());
        }
        dataset.addSeries(present);
        dataset.addSeries(late);
        dataset.addSeries(absent);

        ChartPanel chartPanel = ChartTheme.wrap(ChartTheme.lineChart(dataset, true));
        // Breathing room between the chart legend and the card's bottom edge
        chartPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        return ChartTheme.sectionCard("Attendance - Last 14 Days", chartPanel);
    }

    private JPanel buildLeaveAndPayrollCharts(DashboardData data) {
        JPanel row = new JPanel(new MigLayout("insets 0, gap 20", "[grow,50%][grow,50%]"));
        row.setOpaque(false);

        DefaultCategoryDataset leaveDataset = new DefaultCategoryDataset();
        for (var usage : data.leaveUsageByType()) {
            leaveDataset.addValue(usage.days(), "Days used", usage.label());
        }
        row.add(ChartTheme.sectionCard("Leave Usage This Year",
                ChartTheme.wrap(ChartTheme.multiBarChart(leaveDataset, false))),
                "grow");

        double totalGross = 0;
        for (var point : data.payrollCostTrend()) {
            totalGross += point.gross().doubleValue();
        }
        if (totalGross <= 0) {
            row.add(emptySection("Payroll Cost", "payroll",
                    "Payroll cost appears after the first approved payroll."), "grow");
            return row;
        }
        TimeSeriesCollection payrollDataset = new TimeSeriesCollection();
        TimeSeries gross = new TimeSeries("Gross");
        for (var point : data.payrollCostTrend()) {
            String[] parts = point.periodLabel().split("-");
            gross.add(new Day(1, Integer.parseInt(parts[1]), Integer.parseInt(parts[0])),
                    point.gross().doubleValue());
        }
        payrollDataset.addSeries(gross);
        row.add(ChartTheme.sectionCard("Payroll Cost by Period",
                ChartTheme.wrap(ChartTheme.lineChart(payrollDataset, false))),
                "grow");
        return row;
    }

    /** Card-styled placeholder shown when a chart has no data yet. */
    private JPanel emptySection(String title, String iconKey, String message) {
        // Same card geometry as ChartTheme.sectionCard (insets + fixed-height
        // scrollable body) so the always-visible scroll bars sit inside the
        // card beside the content, aligned with the chart cards.
        JPanel card = new JPanel(new net.miginfocom.swing.MigLayout(
                "wrap 1, insets 16 18 14 18",
                "[grow,fill]",
                "[grow,fill]"));
        card.setBackground(Palette.color(Role.CARD_BG));
        JPanel inner = new JPanel(new net.miginfocom.swing.MigLayout("wrap 1, align center center"));
        inner.setOpaque(false);
        inner.add(new EmptyStatePanel(iconKey, title, message));

        JScrollPane scroll = new JScrollPane(inner);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setViewportBorder(null);
        scroll.setBackground(Palette.color(Role.CARD_BG));
        scroll.getViewport().setBackground(Palette.color(Role.CARD_BG));
        scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_ALWAYS);
        card.add(scroll, "height " + ChartTheme.BODY_VIEWPORT_HEIGHT + "!");
        return card;
    }

    private static String prettify(String status) {
        return status == null ? "" : status.replace('_', ' ');
    }

    /**
     * Scroll content that always matches the viewport width exactly. Without
     * this, JViewport sizes the view to max(preferred, viewport) and the
     * percent-based MigLayout columns can inflate the preferred width during
     * resizes, pushing cards and charts past the right edge (clipped layout).
     */
    private static final class ScrollTrackingPanel extends JPanel implements Scrollable {

        private ScrollTrackingPanel(MigLayout layout) {
            super(layout);
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
        }
    }
}
