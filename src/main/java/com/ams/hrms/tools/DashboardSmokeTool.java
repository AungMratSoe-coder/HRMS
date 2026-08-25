package com.ams.hrms.tools;

import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.dto.DashboardData;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.DashboardService;
import com.ams.hrms.ui.main.MainFrame;
import com.ams.hrms.ui.theme.ThemeManager;

/**
 * Development-only Phase 6 verification: loads live dashboard data and
 * prints it, then renders the real MainFrame dashboard in both themes.
 */
public final class DashboardSmokeTool {

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        DashboardService dashboardService = ServiceRegistry.dashboardService();

        authService.login("admin", "Admin@123");

        // Live data print (same call the panel makes).
        DashboardData data = dashboardService.load();
        var s = data.stats();
        System.out.println("--- live dashboard data ---");
        System.out.println("employees total=" + s.totalEmployees() + " active=" + s.activeEmployees()
                + " newThisMonth=" + s.newEmployeesThisMonth());
        System.out.println("today: present=" + s.presentToday() + " late=" + s.lateToday()
                + " absent=" + s.absentToday() + " onLeave=" + s.onLeaveToday());
        System.out.println("pendingLeaves=" + s.pendingLeaveRequests());
        System.out.println("departments=" + data.employeesByDepartment().size()
                + " statuses=" + data.employeesByStatus().size()
                + " leaveTypes=" + data.leaveUsageByType().size()
                + " trendDays=" + data.attendanceTrend().size()
                + " payrollPeriods=" + data.payrollCostTrend().size());
        System.out.println("latestPayroll: " + (data.lastPayrollPeriod() == null
                ? "none yet"
                : data.lastPayrollPeriod() + " gross=" + data.formattedMoney(data.lastPayrollGross())));

        renderAndCapture();

        authService.logout();
        DatabaseConfig.close();
        System.exit(0);
    }

    private static void renderAndCapture() throws Exception {
        final MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            frame.setSize(1290, 800);
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
            frame.toFront();
            holder[0] = frame;
        });
        Thread.sleep(2500); // async dashboard load + chart layout

        capture(holder[0], "screenshots/dashboard-light.png");
        captureScrolled(holder[0], "screenshots/dashboard-charts.png");
        SwingUtilities.invokeAndWait(() -> ThemeManager.toggle());
        Thread.sleep(1200); // theme switch rebuilds charts
        capture(holder[0], "screenshots/dashboard-dark.png");

        SwingUtilities.invokeAndWait(() -> {
            holder[0].setAlwaysOnTop(false);
            holder[0].dispose();
        });
        Thread.sleep(200);
    }

    /** Scrolls the dashboard down and captures the chart sections. */
    private static void captureScrolled(java.awt.Window window, String relativePath)
            throws AWTException, java.io.IOException, InterruptedException {
        Robot robot = new Robot(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice());
        robot.mouseMove(window.getX() + window.getWidth() / 2,
                window.getY() + window.getHeight() / 2);
        robot.mouseWheel(12);
        Thread.sleep(400);
        capture(window, relativePath);
    }

    private static void capture(java.awt.Window window, String relativePath) throws AWTException,
            java.io.IOException {
        File out = new File(relativePath);
        out.getParentFile().mkdirs();
        Rectangle bounds = window.getBounds();
        GraphicsDevice device = GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice();
        Robot robot = new Robot(device);
        BufferedImage image = robot.createScreenCapture(bounds);
        ImageIO.write(image, "png", out);
        System.out.println("captured " + out.getAbsolutePath());
    }
}
