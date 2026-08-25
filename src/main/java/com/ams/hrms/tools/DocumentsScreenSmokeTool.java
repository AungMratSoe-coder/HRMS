package com.ams.hrms.tools;

import java.io.File;

import javax.swing.SwingUtilities;

import com.ams.hrms.config.DatabaseConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.ui.main.MainFrame;
import com.ams.hrms.ui.theme.ThemeManager;

/**
 * Renders the Documents module in the real main frame and captures light and
 * dark screenshots for visual verification.
 */
public final class DocumentsScreenSmokeTool {

    private DocumentsScreenSmokeTool() {
    }

    public static void main(String[] args) throws Exception {
        var config = com.ams.hrms.config.AppConfig.get();
        com.ams.hrms.db.DbChecker.check(config);
        com.ams.hrms.config.DatabaseConfig.initialize(config);
        com.ams.hrms.db.DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();

        AuthService authService = ServiceRegistry.authService();
        authService.login("admin", "Admin@123");

        // Seed one real document so the list has content, cleaned up after.
        java.nio.file.Path sample = java.nio.file.Files.createTempFile("hrms-doc-smoke", ".pdf");
        java.nio.file.Files.writeString(sample, "%PDF-1.4 smoke test");
        long seededId = ServiceRegistry.documentService().upload(
                1L, "NRC", sample, java.time.LocalDate.now().plusDays(10), "smoke test");

        final MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            frame.setSize(1290, 800);
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
            frame.toFront();
            holder[0] = frame;
        });
        Thread.sleep(1500);

        SwingUtilities.invokeAndWait(() ->
                holder[0].navigation().navigate("documents"));
        Thread.sleep(1200);

        // Click into the table so it gains focus (verifies no blue focus border).
        java.awt.Robot robot = new java.awt.Robot();
        robot.mouseMove(holder[0].getX() + holder[0].getWidth() / 2,
                holder[0].getY() + holder[0].getHeight() / 2);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(400);

        capture(holder[0], "screenshots/documents-light.png");
        SwingUtilities.invokeAndWait(() -> ThemeManager.toggle());
        Thread.sleep(1200);
        capture(holder[0], "screenshots/documents-dark.png");

        SwingUtilities.invokeAndWait(() -> {
            holder[0].setAlwaysOnTop(false);
            holder[0].dispose();
        });
        Thread.sleep(200);

        ServiceRegistry.documentService().delete(seededId);
        java.nio.file.Files.deleteIfExists(sample);
        authService.logout();
        DatabaseConfig.close();
        System.exit(0);
    }

    private static void capture(java.awt.Window window, String relativePath) throws Exception {
        File out = new File(relativePath);
        out.getParentFile().mkdirs();
        java.awt.image.BufferedImage image = new java.awt.Robot(
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                        .getDefaultScreenDevice())
                .createScreenCapture(window.getBounds());
        javax.imageio.ImageIO.write(image, "png", out);
        System.out.println("captured " + out.getAbsolutePath());
    }
}
