package com.ams.hrms.tools;

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.db.DatabaseMigrator;
import com.ams.hrms.db.DbChecker;
import com.ams.hrms.model.Employee;
import com.ams.hrms.repository.Sql;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.service.DocumentService;
import com.ams.hrms.service.EmployeeService;
import com.ams.hrms.ui.employee.EmployeeProfileDialog;
import com.ams.hrms.ui.theme.ThemeManager;

/** Development-only: renders + captures the Employee Profile dialog. */
public final class ProfileSmokeTool {

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();
        ThemeManager.install();

        AuthService authService = ServiceRegistry.authService();
        DocumentService documents = ServiceRegistry.documentService();
        EmployeeService employees = ServiceRegistry.employeeService();

        // Seed one visible document for EMP-0001 (purged afterwards).
        Path tempFile = Files.createTempFile("profile-seed-", ".pdf");
        Files.writeString(tempFile, "%PDF-1.4 employment contract");
        authService.login("admin", "Admin@123");
        long employeeId = employees.findAll(
                        new com.ams.hrms.repository.EmployeeRepository.Filter(
                                "EMP-0001", null, null, null))
                .stream().findFirst().orElseThrow().getId();
        long seededDocId = documents.upload(employeeId, "CONTRACT", tempFile,
                LocalDate.now().plusYears(2), null);

        final JFrame[] frameHolder = new JFrame[1];
        try {
            SwingUtilities.invokeAndWait(() -> {
                Employee profileEmployee = employees.findById(employeeId);
                EmployeeProfileDialog dialog =
                        new EmployeeProfileDialog(null, profileEmployee);

                JFrame frame = new JFrame("Employee Profile - verification");
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setLayout(new BorderLayout());
                frame.add(dialog.getContentPane(), BorderLayout.CENTER);
                frame.setSize(960, 700);
                frame.setLocationRelativeTo(null);
                frame.setAlwaysOnTop(true);
                frameHolder[0] = frame;
                frame.setVisible(true);
                frame.toFront();
            });
            Thread.sleep(1500); // async loads
            capture(frameHolder[0], "screenshots/profile-summary.png");

            SwingUtilities.invokeAndWait(() -> {
                JTabbedPane tabs = findTabbedPane(frameHolder[0]);
                if (tabs != null) {
                    tabs.setSelectedIndex(1);
                }
            });
            Thread.sleep(1200); // documents load
            capture(frameHolder[0], "screenshots/profile-documents.png");
        } finally {
            try {
                if (frameHolder[0] != null) {
                    SwingUtilities.invokeAndWait(() -> {
                        frameHolder[0].setAlwaysOnTop(false);
                        frameHolder[0].dispose();
                    });
                }
            } catch (Exception ignored) {
                // best-effort close
            }

            // cleanup seed document
            new Sql().executeUpdate(
                    "DELETE FROM employee_documents WHERE id = ?", seededDocId);
            try (var paths = Files.walk(Path.of(AppConfig.get().documentsRoot(), "documents"))) {
                paths.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().contains("profile-seed-"))
                        .forEach(p -> {
                            try {
                                Files.deleteIfExists(p);
                            } catch (Exception ignored) {
                            }
                        });
            }
            Files.deleteIfExists(tempFile);
        }

        authService.logout();
        DatabaseConfig.close();
        System.exit(0);
    }

    /** Depth-first search for the profile tab pane. */
    private static JTabbedPane findTabbedPane(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JTabbedPane tabbedPane) {
                return tabbedPane;
            }
            if (component instanceof Container nested) {
                JTabbedPane found = findTabbedPane(nested);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static void capture(java.awt.Window window, String relativePath) throws Exception {
        File out = new File(relativePath);
        out.getParentFile().mkdirs();
        Rectangle bounds = window.getBounds();
        Robot robot = new Robot(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice());
        BufferedImage image = robot.createScreenCapture(bounds);
        ImageIO.write(image, "png", out);
        System.out.println("captured " + out.getAbsolutePath());
    }
}
