package com.ams.hrms.ui.main;

import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.time.LocalDate;
import java.util.function.Consumer;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;

import com.ams.hrms.component.HeaderPanel;
import com.ams.hrms.component.SidebarMenuPanel;
import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.NotificationController;
import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.ui.notification.NotificationsDialog;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.ThemeManager;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

/**
 * Main application shell (spec sections 5, 50). Wires the header, filtered
 * sidebar and {@link ContentPanel} together through {@link NavigationService};
 * modules are registered as lazy factories and never reference each other.
 * Owns the notification bell: unread badge polling, the notification center
 * dialog and the once-per-session operational scan (spec section 41).
 */
public class MainFrame extends JFrame {

    private static final int UNREAD_POLL_MS = 60_000; // Notification unread count ကို 1 minute တစ်ခါ စစ်မယ်။
    private static final int UNREAD_BADGE_CAP = 99; // Notification 150 ခုရှိရင် badge မှာ 99+ လို့ပြမယ်။
    /**
     * Operational scans repeat while the app stays open (dedup makes repeats safe).
     */
    private static final int OPERATIONAL_SCAN_INTERVAL_MS = 60 * 60 * 1000; // operational notification scan ကို
                                                                            // တစ်နာရီတစ်ခါ run လုပ်ပါတယ်။

    private final AuthService authService = ServiceRegistry.authService();
    private final NotificationController notificationController = new NotificationController(
            ServiceRegistry.notificationService());

    private final HeaderPanel header = new HeaderPanel();
    private final SidebarMenuPanel sidebar = new SidebarMenuPanel(MenuDefinition.visibleTo(SessionContext.permissions(),
            SessionContext.hasOnlyRole("EMPLOYEE")));
    private final ContentPanel contentPanel = new ContentPanel();

    /**
     * Rail holding the rounded sidebar card with a margin from the window
     * edges (reference design); the rail background matches the header so
     * the sidebar reads as an inset card in both themes.
     */
    private final JPanel sidebarRail = new JPanel(new BorderLayout());

    private final NavigationService navigation = new NavigationService(contentPanel, this::applyModuleTitle);

    /** Routes dashboard shortcuts (e.g. "Process Now") to the target module. */
    private final Consumer<Events.NavigateRequest> navigateRequestListener = request -> navigation
            .navigate(request.moduleId());

    /** Refreshes the header picture when a user account changed (own upload). */
    private final Consumer<Events.DataChanged> dataChangedListener = event -> {
        if (com.ams.hrms.service.UserService.DATA_SCOPE.equals(event.scope())) {
            loadHeaderAvatar();
        }
    };

    /** Polls the unread badge; stopped automatically when the frame is hidden. */
    private final Timer unreadPollTimer = new Timer(UNREAD_POLL_MS, event -> refreshUnreadBadge());

    /** Re-runs digests/expiry/birthday scans periodically (service dedupes). */
    private final Timer operationalScanTimer = new Timer(OPERATIONAL_SCAN_INTERVAL_MS, event -> runOperationalScan());

    /**
     * Locks the session after long inactivity and requires re-entry of the
     * password.
     */
    private final IdleLockManager idleLock = new IdleLockManager(this, this::signOutFromLock);

    public MainFrame() {
        super(AppConfig.get().appName());
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1440, 900);
        setMinimumSize(new java.awt.Dimension(1180, 740));
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        header.setTitle("Dashboard");
        header.setUser(SessionContext.currentUser().fullName(), SessionContext.primaryRoleName());
        header.setUnreadNotifications(0);
        header.refreshThemeIcon(ThemeManager.isDark());
        loadHeaderAvatar();

        header.onMenuToggle(sidebar::toggleExpanded);
        header.onThemeToggle(() -> {
            ThemeManager.toggle();
            header.refreshThemeIcon(ThemeManager.isDark());
            sidebarRail.setBackground(Palette.color(Palette.Role.CARD_BG));
            sidebar.repaint();
            contentPanel.repaint();
        });
        header.onNotificationsClick(this::openNotifications);
        header.onMyProfile(() -> com.ams.hrms.ui.profile.MyProfileDialog.show(this));

        sidebar.onSelection(navigation::navigate);
        navigation.onNavigated(sidebar::setSelectedId);
        sidebar.onLogout(this::confirmLogout);

        registerPanels();
        bindRefreshShortcut();

        sidebarRail.setOpaque(true);
        sidebarRail.setBackground(Palette.color(Palette.Role.CARD_BG));
        sidebarRail.setBorder(new EmptyBorder(10, 10, 10, 0));
        sidebarRail.add(sidebar, BorderLayout.CENTER);

        add(header, BorderLayout.NORTH);
        add(sidebarRail, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);

        SwingUtilities.invokeLater(() -> {
            navigation.navigate("dashboard");
            refreshUnreadBadge();
            runOperationalScan();
        });
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EventBus.subscribe(Events.NavigateRequest.class, navigateRequestListener);
        EventBus.subscribe(Events.DataChanged.class, dataChangedListener);
        unreadPollTimer.start();
        operationalScanTimer.start();
        idleLock.install();
    }

    @Override
    public void removeNotify() {
        EventBus.unsubscribe(Events.NavigateRequest.class, navigateRequestListener);
        EventBus.unsubscribe(Events.DataChanged.class, dataChangedListener);
        unreadPollTimer.stop();
        operationalScanTimer.stop();
        idleLock.uninstall();
        super.removeNotify();
    }

    // ------------------------------------------------------------------
    // Notifications (spec section 41)
    // ------------------------------------------------------------------

    /** Opens the modal notification center; the badge refreshes on close. */
    private void openNotifications() {
        new NotificationsDialog(this, this::refreshUnreadBadge).setVisible(true);
        refreshUnreadBadge();
    }

    private void refreshUnreadBadge() {
        notificationController
                .unreadCount(count -> header.setUnreadNotifications((int) Math.min(count, UNREAD_BADGE_CAP)));
    }

    /**
     * Loads the signed-in user's picture off the EDT; initials until it arrives.
     */
    private void loadHeaderAvatar() {
        UiThread.executeAsync("Load profile picture",
                () -> ServiceRegistry.userService().findOwnAvatar(),
                header::setAvatar);
    }

    /**
     * Generates digests, expiry warnings, birthdays and training reminders
     * once per session and then hourly; the service deduplicates, so repeats
     * are harmless.
     */
    private void runOperationalScan() {
        UiThread.executeAsync("Notification operational scan",
                () -> ServiceRegistry.notificationService().runOperationalScan(LocalDate.now()),
                summary -> {
                    if (summary.total() > 0) {
                        refreshUnreadBadge();
                    }
                });
    }

    // ------------------------------------------------------------------
    // Modules
    // ------------------------------------------------------------------

    /**
     * Real module panels register here; ids without a factory get the
     * pending-module placeholder from NavigationService.
     */
    private void registerPanels() {
        contentPanel.register("dashboard", com.ams.hrms.ui.dashboard.DashboardPanel::new);
        contentPanel.register("departments", com.ams.hrms.ui.org.DepartmentPanel::new);
        contentPanel.register("positions", com.ams.hrms.ui.org.PositionPanel::new);
        contentPanel.register("employees", com.ams.hrms.ui.employee.EmployeeListPanel::new);
        contentPanel.register("recruitment", com.ams.hrms.ui.recruitment.RecruitmentPanel::new);
        contentPanel.register("onboarding", com.ams.hrms.ui.onboarding.OnboardingPanel::new);
        contentPanel.register("performance", com.ams.hrms.ui.performance.PerformancePanel::new);
        contentPanel.register("training", com.ams.hrms.ui.training.TrainingPanel::new);
        contentPanel.register("assets", com.ams.hrms.ui.assets.AssetPanel::new);
        contentPanel.register("separation", com.ams.hrms.ui.separation.SeparationPanel::new);
        contentPanel.register("documents", com.ams.hrms.ui.documents.DocumentsPanel::new);
        contentPanel.register("shifts", com.ams.hrms.ui.shift.ShiftPanel::new);
        contentPanel.register("attendance", com.ams.hrms.ui.attendance.AttendancePanel::new);
        contentPanel.register("overtime", com.ams.hrms.ui.overtime.OvertimePanel::new);
        contentPanel.register("payroll", com.ams.hrms.ui.payroll.PayrollPanel::new);
        contentPanel.register("payslips", com.ams.hrms.ui.payroll.MyPayslipsPanel::new);
        contentPanel.register("leave", com.ams.hrms.ui.leave.LeavePanel::new);
        contentPanel.register("reports", com.ams.hrms.ui.reports.ReportsPanel::new);
        contentPanel.register("audit", com.ams.hrms.ui.audit.AuditPanel::new);
        contentPanel.register("settings", com.ams.hrms.ui.settings.SettingsPanel::new);
    }

    private void applyModuleTitle(String moduleTitle) {
        header.setTitle(moduleTitle);
        setTitle(AppConfig.get().appName() + " - " + moduleTitle);
    }

    /** F5 rebuilds the current module's panel. */
    private void bindRefreshShortcut() {
        var inputMap = getRootPane().getInputMap(javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        var actionMap = getRootPane().getActionMap();
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "refreshModule");
        actionMap.put("refreshModule", new javax.swing.AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                navigation.refreshCurrent();
            }
        });
    }

    /** Navigation handle - also used by development verification tools. */
    public NavigationService navigation() {
        return navigation;
    }

    // ------------------------------------------------------------------
    // Logout
    // ------------------------------------------------------------------

    private void confirmLogout() {
        boolean confirmed = Dialogs.confirm(this, "Sign Out",
                "Are you sure you want to sign out?");
        if (!confirmed) {
            return;
        }
        signOutFromLock();
    }

    /** Ends the session and returns to the login window (no confirmation). */
    private void signOutFromLock() {
        authService.logout();
        dispose();
        SwingUtilities.invokeLater(() -> new com.ams.hrms.ui.login.LoginFrame().setVisible(true));
    }
}
