package com.ams.hrms.tools;

import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.ui.main.MainFrame;
import com.ams.hrms.ui.theme.ThemeManager;

/**
 * Development-only Phase 5 verification: signs in, builds the real MainFrame,
 * navigates through NavigationService, and captures screenshots.
 * Run A: admin - dashboard home, then employees placeholder.
 * Run B: officer - forbidden settings shows access-denied; leave opens.
 */
public final class MainFrameSmokeTool {

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();
        ThemeManager.install();

        AuthService authService = ServiceRegistry.authService();

        // --- admin ---------------------------------------------------------
        authService.login("admin@ams.local", "Admin@123");
        openAndRun("admin", frame -> {
            Thread.sleep(400); // let the initial dashboard navigation settle
            boolean navigated = frame.navigation().navigate("employees");
            System.out.println(navigated
                    ? "OK   admin navigated to employees placeholder"
                    : "FAIL: employees navigation refused for admin");
            Thread.sleep(500);
            capture(frame, "screenshots/mainframe-admin-employees.png");
        });
        authService.logout();

        // --- officer (restricted) -------------------------------------------
        authService.login("officer@ams.local", "Officer@123");
        System.out.println("officer menu items visible to RBAC filter: "
                + SessionContext.permissions().size() + " permissions");
        openAndRun("officer", frame -> {
            Thread.sleep(400);
            boolean denied = !frame.navigation().navigate("settings");
            System.out.println(denied
                    ? "OK   settings navigation denied for officer"
                    : "FAIL: settings opened for officer");
            Thread.sleep(400);
            capture(frame, "screenshots/mainframe-officer-denied.png");

            boolean leaveOk = frame.navigation().navigate("leave");
            System.out.println(leaveOk
                    ? "OK   leave module opens for officer"
                    : "FAIL: leave denied for officer");
        });
        authService.logout();

        DatabaseConfig.close();
        System.exit(0);
    }

    /** Builds the MainFrame on the EDT, runs the action, then disposes it. */
    private static void openAndRun(String label, FrameAction action) throws Exception {
        final Exception[] failure = new Exception[1];
        final MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                MainFrame frame = new MainFrame();
                frame.setAlwaysOnTop(true);
                frame.setVisible(true);
                frame.toFront();
                holder[0] = frame;
            } catch (Exception t) {
                failure[0] = t;
            }
        });
        if (failure[0] != null) {
            throw failure[0];
        }
        MainFrame frame = holder[0];
        try {
            action.run(frame);
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
            frame.setAlwaysOnTop(false);
            Thread.sleep(200);
            System.out.println("session closed: " + label);
        }
    }

    @FunctionalInterface
    private interface FrameAction {
        void run(MainFrame frame) throws Exception;
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
