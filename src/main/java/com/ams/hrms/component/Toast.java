package com.ams.hrms.component;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JWindow;
import javax.swing.Timer;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.util.UiGraphics;

/**
 * Transient notification toast anchored to the bottom-right of a window
 * (spec section 36). Fades in, holds, fades out; never steals focus.
 */
public final class Toast {

    public enum Type {
        SUCCESS(Role.SUCCESS, "check"),
        ERROR(Role.DANGER, "warning"),
        WARNING(Role.WARNING, "warning"),
        INFO(Role.INFO, "info");

        final Role role;
        final String iconName;

        Type(Role role, String iconName) {
            this.role = role;
            this.iconName = iconName;
        }
    }

    private static final int DISPLAY_MS = 2600;
    private static final int FADE_STEP_MS = 40;
    private static final int MARGIN = 24;

    private Toast() {
    }

    /** Shows a toast at the bottom-right of {@code owner}. Safe from any thread. */
    public static void show(Window owner, Type type, String message) {
        Runnable showAction = () -> new ToastWindow(owner, type, message).display();
        if (java.awt.EventQueue.isDispatchThread()) {
            showAction.run();
        } else {
            java.awt.EventQueue.invokeLater(showAction);
        }
    }

    private static final class ToastWindow extends JWindow {

        // Note: qualified name - java.awt.Window.Type would otherwise shadow
        // our Type enum inside this Window subclass.
        private final com.ams.hrms.component.Toast.Type type;
        private float opacity = 0f;
        private final Timer fadeIn;
        private final Timer fadeOut;

        ToastWindow(Window owner, com.ams.hrms.component.Toast.Type type, String message) {
            super(owner);
            this.type = type;

            setFocusableWindowState(false);
            setBackground(new Color(0, 0, 0, 0));

            JComponent content = new ToastPanel(type, message);
            setContentPane(content);
            setSize(content.getPreferredSize());

            int x = owner.getX() + owner.getWidth() - getWidth() - MARGIN;
            int y = owner.getY() + owner.getHeight() - getHeight() - MARGIN;
            setLocation(x, y);

            boolean translucencySupported = UiGraphics.supportsWindowTranslucency(this);
            if (translucencySupported) {
                setOpacity(0f);
                fadeIn = new Timer(FADE_STEP_MS, event -> {
                    opacity = Math.min(1f, opacity + 0.15f);
                    setOpacity(opacity);
                    if (opacity >= 1f) {
                        ((Timer) event.getSource()).stop();
                    }
                });
                fadeOut = new Timer(FADE_STEP_MS, event -> {
                    opacity = Math.max(0f, opacity - 0.12f);
                    setOpacity(Math.max(opacity, 0f));
                    if (opacity <= 0f) {
                        ((Timer) event.getSource()).stop();
                        dispose();
                    }
                });
            } else {
                fadeIn = null;
                fadeOut = null;
            }
        }

        void display() {
            setVisible(true);
            if (fadeIn != null) {
                fadeIn.start();
            }
            Timer dismiss = new Timer(DISPLAY_MS, event -> {
                if (fadeOut != null) {
                    fadeOut.start();
                } else {
                    dispose();
                }
            });
            dismiss.setRepeats(false);
            dismiss.start();
        }
    }

    /** Rounded pill painted with the semantic color of the toast type. */
    private static final class ToastPanel extends JComponent {

        private final com.ams.hrms.component.Toast.Type type;
        private final String message;

        ToastPanel(com.ams.hrms.component.Toast.Type type, String message) {
            this.type = type;
            this.message = message;
            Font base = javax.swing.UIManager.getFont("defaultFont");
            if (base == null) {
                base = new JLabel().getFont();
            }
            Font font = base.deriveFont(Font.PLAIN, 13f);
            java.awt.FontMetrics metrics = getFontMetrics(font);

            JLabel measurer = new JLabel(message);
            measurer.setFont(font);
            int textWidth = measurer.getPreferredSize().width;

            setFont(font);
            int width = textWidth + 64;
            int height = metrics.getHeight() + 26;
            setPreferredSize(new Dimension(width, height));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            Color semantic = Palette.color(type.role);

            UiGraphics.fillRoundRect(g, 0, 0, getWidth(), getHeight(), 10,
                    UiGraphics.blend(Palette.color(Role.CARD_BG), semantic, 0.85));
            UiGraphics.drawRoundRect(g, 0, 0, getWidth(), getHeight(), 10, semantic);

            IconLoader.small(type.iconName).paintIcon(this, g, 16, (getHeight() - 16) / 2);

            g.setFont(getFont());
            g.setColor(Color.WHITE);
            java.awt.FontMetrics metrics = g.getFontMetrics();
            int baseline = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
            g.drawString(message, 42, baseline);
            g.dispose();
        }
    }
}
