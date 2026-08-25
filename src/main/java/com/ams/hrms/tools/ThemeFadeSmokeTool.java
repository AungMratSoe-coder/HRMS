package com.ams.hrms.tools;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.ams.hrms.ui.theme.ThemeManager;

/**
 * Dev verification for the theme crossfade. Opens a plain frame, switches to
 * dark and back to light while sampling the window region with a Robot and
 * printing the average brightness of each sample. A working fade shows a
 * gradual ramp between the light and dark values; an instant switch shows a
 * single jump.
 */
public final class ThemeFadeSmokeTool {

    private ThemeFadeSmokeTool() {
    }

    public static void main(String[] args) throws Exception {
        ThemeManager.install();

        Rectangle[] bounds = new Rectangle[1];
        SwingUtilities.invokeAndWait(() -> {
            JFrame frame = new JFrame("Fade test");
            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(new Color(0xF8FAFC));
            panel.add(new JLabel("FADE TEST", JLabel.CENTER));
            frame.setContentPane(panel);
            frame.setSize(900, 600);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
            bounds[0] = new Rectangle(frame.getLocationOnScreen(), frame.getSize());
        });
        Thread.sleep(900);

        System.out.println("== switch LIGHT -> DARK ==");
        double[] ramp1 = sampleDuringSwitch(bounds[0],
                () -> SwingUtilities.invokeLater(
                        () -> ThemeManager.setTheme(ThemeManager.Theme.DARK)));
        report(ramp1);

        Thread.sleep(500);
        System.out.println("== switch DARK -> LIGHT ==");
        double[] ramp2 = sampleDuringSwitch(bounds[0],
                () -> SwingUtilities.invokeLater(
                        () -> ThemeManager.setTheme(ThemeManager.Theme.LIGHT)));
        report(ramp2);

        System.exit(0);
    }

    private interface Switcher {
        void run();
    }

    /** Samples brightness roughly every 30ms around a theme switch. */
    private static double[] sampleDuringSwitch(Rectangle bounds, Switcher switcher)
            throws Exception {
        double[] samples = new double[16];
        Robot robot = new Robot();
        int i = 0;
        for (; i < 4; i++) {
            samples[i] = brightness(robot.createScreenCapture(bounds));
            Thread.sleep(30);
        }
        switcher.run();
        for (; i < samples.length; i++) {
            samples[i] = brightness(robot.createScreenCapture(bounds));
            Thread.sleep(30);
        }
        return samples;
    }

    private static double brightness(BufferedImage image) {
        long total = 0;
        int count = 0;
        for (int y = 0; y < image.getHeight(); y += 6) {
            for (int x = 0; x < image.getWidth(); x += 6) {
                int rgb = image.getRGB(x, y);
                total += ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
                count++;
            }
        }
        return count == 0 ? 0 : (total / (double) count) / 255.0;
    }

    private static void report(double[] samples) {
        StringBuilder line = new StringBuilder();
        for (double sample : samples) {
            line.append(String.format("%.3f ", sample));
        }
        System.out.println(line.toString().trim());
    }
}
