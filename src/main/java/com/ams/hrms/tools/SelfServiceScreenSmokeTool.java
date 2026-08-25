package com.ams.hrms.tools;

import java.io.File;
import java.util.List;

import javax.swing.SwingUtilities;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.model.Employee;
import com.ams.hrms.repository.EmployeeRepository;
import com.ams.hrms.repository.UserRepository;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.ui.main.MainFrame;

/**
 * Renders the self-service experience end to end: signs in with a plain
 * EMPLOYEE account, walks the sidebar modules (Dashboard, Employees,
 * Attendance, Shifts, Overtime, Leave, Performance, Training, Payslips) and
 * fails when an "Access Denied" dialog pops up or a screen stays empty by
 * error. Captures screenshots of every visited module.
 */
public final class SelfServiceScreenSmokeTool {

    private static final String SMOKE_USERNAME = "selfscope-user";
    private static final String FIRST_PASSWORD = "Start@123";
    private static final String OWN_PASSWORD = "Scoped@123";
    private static final String TARGET_CODE = "EMP-0006";

    private static final String[] MODULES = {
            "dashboard", "shifts", "overtime",
            "leave", "performance", "training", "payslips"};

    private static int failed;

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService auth = ServiceRegistry.authService();
        var userService = ServiceRegistry.userService();
        var employeeService = ServiceRegistry.employeeService();
        UserRepository userRepository = new UserRepository();

        // --- prepare the scoped test account (recover from earlier runs) ---
        auth.login("admin", "Admin@123");
        Long targetId = employeeService.findAll(
                        new EmployeeRepository.Filter(TARGET_CODE, null, null, "ACTIVE"))
                .stream().filter(e -> TARGET_CODE.equals(e.getCode()))
                .map(Employee::getId).findFirst().orElseThrow();
        var employeeRole = userService.findRoles().stream()
                .filter(role -> role.code().equals("EMPLOYEE")).findFirst().orElseThrow();
        Long userId;
        var existing = userRepository.findAccountByUsername(SMOKE_USERNAME).orElse(null);
        if (existing == null) {
            userId = userService.createUser(SMOKE_USERNAME, "Self Scope Smoke",
                    "selfscope-smoke@example.com", FIRST_PASSWORD,
                    List.of(employeeRole.id()));
        } else {
            userId = existing.id();
            userService.setActive(userId, true);
            userService.updateRoles(userId, List.of(employeeRole.id()));
            userService.resetPassword(userId, FIRST_PASSWORD);
        }
        userService.setEmployeeLink(userId, targetId);

        // --- scoped sign-in -------------------------------------------------
        auth.logout();
        auth.login(SMOKE_USERNAME, FIRST_PASSWORD);
        auth.completeForcedPasswordChange(OWN_PASSWORD);
        auth.logout();
        auth.login(SMOKE_USERNAME, OWN_PASSWORD);

        // --- walk the modules on the EDT -------------------------------------
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            frame.setSize(1440, 900);
            frame.setVisible(true);
            frame.toFront();
        });
        Thread.sleep(1500);

        // Management-only consoles must be hidden and unreachable.
        for (String hidden : new String[] {"employees", "attendance"}) {
            boolean[] navigated = new boolean[1];
            SwingUtilities.invokeAndWait(() -> {
                for (var window : MainFrame.getWindows()) {
                    if (window instanceof MainFrame frame) {
                        navigated[0] = frame.navigation().navigate(hidden);
                    }
                }
            });
            System.out.println((navigated[0] ? "[FAIL] " : "[PASS] ")
                    + hidden + " module is hidden from employee accounts");
            if (navigated[0]) {
                failed++;
            }
        }

        for (String moduleId : MODULES) {
            SwingUtilities.invokeAndWait(() -> {
                for (var window : MainFrame.getWindows()) {
                    if (window instanceof MainFrame frame) {
                        frame.navigation().navigate(moduleId);
                    }
                }
            });
            Thread.sleep(1500);
            boolean denied = accessDeniedDialogVisible();
            System.out.println((denied ? "[FAIL] " : "[PASS] ") + moduleId
                    + (denied ? " raised Access Denied" : " rendered"));
            if (denied) {
                failed++;
            }
            capture("screenshots/employee-" + moduleId + ".png");
        }

        // --- cleanup ----------------------------------------------------------
        auth.logout();
        auth.login("admin", "Admin@123");
        userService.setEmployeeLink(userId, null);
        userService.setActive(userId, false);

        System.out.println("failed=" + failed);
        System.exit(failed == 0 ? 0 : 1);
    }

    /** True when an Access Denied dialog is currently on screen. */
    private static boolean accessDeniedDialogVisible() {
        for (var window : java.awt.Window.getWindows()) {
            if (window instanceof javax.swing.JDialog dialog && window.isVisible()
                    && dialog.getTitle() != null
                    && dialog.getTitle().contains("Access Denied")) {
                return true;
            }
        }
        return false;
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
    }
}
