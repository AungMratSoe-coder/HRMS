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
import com.ams.hrms.service.AuthService;
import com.ams.hrms.ui.main.MainFrame;
import com.ams.hrms.ui.theme.ThemeManager;

/** Development-only: captures the Reports module screen via real navigation. */
public final class ReportsScreenSmokeTool {

    public static void main(String[] args) throws Exception {
        AppConfig config = AppConfig.get();
        DbChecker.check(config);
        DatabaseConfig.initialize(config);
        DatabaseMigrator.migrate(config);
        ServiceRegistry.initialize();
        ThemeManager.install();

        AuthService authService = ServiceRegistry.authService();
        authService.login("admin", "Admin@123");

        final MainFrame[] holder = new MainFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            MainFrame frame = new MainFrame();
            frame.setAlwaysOnTop(true);
            frame.setVisible(true);
            frame.toFront();
            holder[0] = frame;
        });
        Thread.sleep(2200);
        SwingUtilities.invokeAndWait(() -> holder[0].navigation().navigate("reports"));
        Thread.sleep(1500);

        // Click the report catalog list so it gains focus (verifies no blue
        // selection border), then capture.
        Robot robot = new Robot(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getDefaultScreenDevice());
        robot.mouseMove(holder[0].getX() + 330, holder[0].getY() + 260);
        robot.mousePress(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(java.awt.event.InputEvent.BUTTON1_DOWN_MASK);
        Thread.sleep(500);

        File out = new File("screenshots/reports.png");
        out.getParentFile().mkdirs();
        Rectangle bounds = holder[0].getBounds();
        BufferedImage image = robot.createScreenCapture(bounds);
        ImageIO.write(image, "png", out);
        System.out.println("captured " + out.getAbsolutePath());

        SwingUtilities.invokeAndWait(() -> {
            holder[0].setAlwaysOnTop(false);
            holder[0].dispose();
        });
        authService.logout();
        DatabaseConfig.close();
        System.exit(0);
    }
}
