package com.ams.hrms.component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.Timer;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.UiGraphics;

/**
 * Centered "working..." state with an animated spinner (spec sections 36 and
 * 44). Show it while a {@link com.ams.hrms.util.UiThread} task is running.
 */
public class LoadingPanel extends JPanel {

    private final Spinner spinner = new Spinner();
    private final JLabel messageLabel;

    public LoadingPanel() {
        this("Loading...");
    }

    public LoadingPanel(String message) {
        setOpaque(false);
        setLayout(new net.miginfocom.swing.MigLayout("wrap 1, align center center, gap 14"));

        messageLabel = new JLabel(message, javax.swing.SwingConstants.CENTER);
        messageLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.PLAIN, 13f));

        add(spinner);
        add(messageLabel);
    }

    /** Re-resolves cached palette colors after a theme switch. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (messageLabel != null) {
            messageLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        }
    }

    @Override
    public void addNotify() {
        super.addNotify();
        spinner.start();
    }

    @Override
    public void removeNotify() {
        spinner.stop();
        super.removeNotify();
    }

    /** Small rotating arc spinner painted with the accent color. */
    private static final class Spinner extends JComponent {

        private static final int SIZE = 30;
        private static final int STROKE_WIDTH = 3;

        private int angle;

        private final Timer timer = new Timer(50, (ActionEvent event) -> {
            angle = (angle + 8) % 360;
            repaint();
        });

        Spinner() {
            setPreferredSize(new Dimension(SIZE + 8, SIZE + 8));
            setOpaque(false);
        }

        void start() {
            timer.start();
        }

        void stop() {
            timer.stop();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            UiGraphics.enableAntialiasing(g);
            g.setStroke(new java.awt.BasicStroke(STROKE_WIDTH, java.awt.BasicStroke.CAP_ROUND,
                    java.awt.BasicStroke.JOIN_ROUND));

            int pad = 4;
            Color track = UiGraphics.blend(Palette.color(Role.CARD_BG),
                    Palette.color(Role.TEXT_MUTED), 0.25);
            g.setColor(track);
            g.drawArc(pad, pad, SIZE, SIZE, 0, 360);

            g.setColor(Palette.color(Role.ACCENT));
            g.rotate(Math.toRadians(-angle), getWidth() / 2.0, getHeight() / 2.0);
            g.drawArc(pad, pad, SIZE, SIZE, 90, 100);
            g.dispose();
        }
    }
}
