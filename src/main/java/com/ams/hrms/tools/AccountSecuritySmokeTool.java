package com.ams.hrms.tools;

import java.io.File;

import javax.swing.SwingUtilities;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.UserAccount;
import com.ams.hrms.repository.UserRepository;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.ui.main.MainFrame;

/**
 * Verifies the account-security feature: self password change (wrong current,
 * policy violations, success), forced-change flag, admin create/reset/
 * deactivate, and renders the Settings User Accounts tab. Idempotent: it
 * recovers from earlier crashed runs and always restores Admin@123.
 */
public final class AccountSecuritySmokeTool {

    private static final String ORIGINAL_ADMIN_PASSWORD = "Admin@123";
    private static final String TEMP_ADMIN_PASSWORD = "NewPass@123";
    private static final String SMOKE_USERNAME = "smoke-user";

    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService auth = ServiceRegistry.authService();
        var userService = ServiceRegistry.userService();
        UserRepository userRepository = new UserRepository();

        String adminPassword = loginAsAdmin(auth);
        String currentAdminPassword = adminPassword;

        // 1. wrong current password is refused
            check("wrong current password refused", () -> {
                try {
                    auth.changePassword("WrongPass@1", "OtherPass@123");
                    return false;
                } catch (ValidationException e) {
                    return true;
                }
            });

            // 2. policy violations are refused
            check("weak password refused", () -> {
                try {
                    auth.changePassword(adminPassword, "weak");
                    return false;
                } catch (ValidationException e) {
                    return true;
                }
            });

            // 3. successful self change + login with the new password
            String stepThreePassword = "Step3@123".equals(currentAdminPassword)
                    ? "Step4@123" : "Step3@123";
            auth.changePassword(currentAdminPassword, stepThreePassword);
            currentAdminPassword = stepThreePassword;
            auth.logout();
            auth.login("admin", stepThreePassword);
            check("login works with changed password", () -> SessionContext.isAuthenticated());
            auth.logout();
            auth.login("admin", stepThreePassword);

            // 4. admin creates (or recovers) the smoke user; forced flag set
            var hrOfficerRole = userService.findRoles().stream()
                    .filter(role -> role.code().equals("HR_OFFICER"))
                    .findFirst().orElseThrow();
            long smokeUserId;
            UserAccount existing = userRepository.findAccountByUsername(SMOKE_USERNAME).orElse(null);
            if (existing == null) {
                smokeUserId = userService.createUser(SMOKE_USERNAME, "Smoke User",
                        "smoke-user@example.com", "Start@123", java.util.List.of(hrOfficerRole.id()));
            } else {
                smokeUserId = existing.id();
                userService.setActive(smokeUserId, true);
                userService.resetPassword(smokeUserId, "Start@123");
                userService.updateRoles(smokeUserId, java.util.List.of(hrOfficerRole.id()));
            }
            auth.logout();
            auth.login(SMOKE_USERNAME, "Start@123");
            check("created user flagged for forced change",
                    () -> SessionContext.currentUser().mustChangePassword());

            // 5. forced change completes and clears the flag
            auth.completeForcedPasswordChange("Changed@123");
            check("forced change clears flag",
                    () -> !SessionContext.currentUser().mustChangePassword());
            auth.logout();
            auth.login(SMOKE_USERNAME, "Changed@123");
            check("login works after forced change", () -> SessionContext.isAuthenticated());

            // 6. admin resets + deactivates the test user
            auth.logout();
            auth.login("admin", currentAdminPassword);
            userService.resetPassword(smokeUserId, "Reset@123");
            userService.setActive(smokeUserId, false);
            auth.logout();
            check("deactivated user cannot sign in", () -> {
                try {
                    auth.login(SMOKE_USERNAME, "Reset@123");
                    return false;
                } catch (com.ams.hrms.exception.AuthenticationException e) {
                    return true;
                }
            });

            // 6b. restore the canonical admin password while the pool is alive
            auth.logout();
            auth.login("admin", currentAdminPassword);
            if (!ORIGINAL_ADMIN_PASSWORD.equals(currentAdminPassword)) {
                auth.changePassword(currentAdminPassword, ORIGINAL_ADMIN_PASSWORD);
                currentAdminPassword = ORIGINAL_ADMIN_PASSWORD;
            }
            // 7. render Settings with the User Accounts tab
            auth.login("admin", currentAdminPassword);
            SwingUtilities.invokeAndWait(() -> {
                MainFrame frame = new MainFrame();
                frame.setSize(1290, 800);
                frame.setAlwaysOnTop(true);
                frame.setVisible(true);
                frame.toFront();
                SwingUtilities.invokeLater(() -> frame.navigation().navigate("settings"));
            });
            Thread.sleep(2500);
            capture("screenshots/settings-users.png");

        System.out.println("passed=" + passed + " failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    /** Signs in as admin, tolerating a leftover password from a crashed run. */
    private static String loginAsAdmin(AuthService auth) throws Exception {
        for (String candidate : new String[] {ORIGINAL_ADMIN_PASSWORD, TEMP_ADMIN_PASSWORD, "Step3@123"}) {
            try {
                auth.login("admin", candidate);
                return candidate;
            } catch (Exception e) {
                // try the next candidate
            }
        }
        throw new IllegalStateException("Could not sign in as admin");
    }

    private static void check(String name, java.util.concurrent.Callable<Boolean> assertion) {
        try {
            boolean ok = Boolean.TRUE.equals(assertion.call());
            System.out.println((ok ? "[PASS] " : "[FAIL] ") + name);
            if (ok) {
                passed++;
            } else {
                failed++;
            }
        } catch (Exception e) {
            System.out.println("[FAIL] " + name + " (" + e.getMessage() + ")");
            failed++;
        }
    }

    private static void capture(String relativePath) throws Exception {
        java.awt.Window active = null;
        for (var window : java.awt.Window.getWindows()) {
            if (window.isActive() || active == null) {
                active = window;
            }
        }
        File out = new File(relativePath);
        out.getParentFile().mkdirs();
        java.awt.image.BufferedImage image = new java.awt.Robot(
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice())
                .createScreenCapture(active.getBounds());
        javax.imageio.ImageIO.write(image, "png", out);
        System.out.println("captured " + out.getAbsolutePath());

        for (var window : java.awt.Window.getWindows()) {
            window.dispose();
        }
        DatabaseConfig.close();
    }
}
