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
import com.ams.hrms.exception.AuthenticationException;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.ui.login.LoginFrame;
import com.ams.hrms.ui.theme.ThemeManager;
import com.ams.hrms.ui.theme.ThemeManager.Theme;

/**
 * Development-only Phase 3 verification: renders the real LoginFrame
 * (light + dark screenshots), then exercises the full authentication flow
 * against the live database - successful sign-in, wrong password, unknown
 * user, brute-force lockout and logout - printing results and exiting.
 */
public final class LoginSmokeTool {

    public static void main(String[] args) throws Exception {
        // Core boot (same sequence as Bootstrapper, no UI yet).
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();
        ThemeManager.install();

        captureLoginScreens();
        exerciseAuthentication(ServiceRegistry.authService());

        DatabaseConfig.close();
        System.exit(0);
    }

    private static void captureLoginScreens() throws Exception {
        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.add(new LoginFrame().getContentPane());
        frame.setSize(940, 600);
        frame.setLocationRelativeTo(null);
        frame.setAlwaysOnTop(true); // ensure the capture region shows OUR window
        frame.setVisible(true);
        frame.toFront();
        Thread.sleep(800);

        capture(frame, "screenshots/login-light.png");

        SwingUtilities.invokeAndWait(() -> ThemeManager.setTheme(Theme.DARK));
        Thread.sleep(600);
        capture(frame, "screenshots/login-dark.png");

        SwingUtilities.invokeAndWait(() -> {
            frame.setAlwaysOnTop(false);
            frame.dispose();
        });
    }

    private static void exerciseAuthentication(AuthService authService) {
        System.out.println("--- authentication flow ---");

        try {
            authService.login("admin", "wrong-password");
            System.out.println("FAIL: wrong password was accepted");
        } catch (AuthenticationException expected) {
            System.out.println("OK   wrong password rejected: " + expected.getUserMessage());
        }

        try {
            authService.login("no.such.user", "whatever");
            System.out.println("FAIL: unknown user accepted");
        } catch (AuthenticationException expected) {
            System.out.println("OK   unknown user rejected with generic message: "
                    + expected.getUserMessage());
        }

        var user = authService.login("admin", "Admin@123");
        System.out.println("OK   signed in as " + user.fullName()
                + " | roles=" + SessionContext.roles().size()
                + " | permissions=" + SessionContext.permissions().size()
                + " | primary role=" + SessionContext.primaryRoleName());
        System.out.println("     has PAYROLL_VIEW=" + SessionContext.has(com.ams.hrms.security.Permissions.PAYROLL_VIEW)
                + ", SETTINGS_MANAGE=" + SessionContext.has(com.ams.hrms.security.Permissions.SETTINGS_MANAGE));

        // Brute-force guard: 5 failures for a second username then expect lockout.
        for (int i = 0; i < 5; i++) {
            try {
                authService.login("hr.officer", "bad-pass-" + i);
            } catch (AuthenticationException ignored) {
                // expected
            }
        }
        try {
            authService.login("hr.officer", "bad-pass-final");
            System.out.println("FAIL: locked username still accepted");
        } catch (BusinessException lockout) {
            System.out.println("OK   brute-force lockout engaged: " + lockout.getUserMessage());
        } catch (AuthenticationException unexpected) {
            System.out.println("FAIL: expected lockout but got normal auth failure");
        }
        SessionContext.clear(); // do not leave hr.officer counters in this process

        authService.login("admin", "Admin@123");
        authService.logout();
        System.out.println("OK   logout cleared session: authenticated="
                + SessionContext.isAuthenticated());
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
