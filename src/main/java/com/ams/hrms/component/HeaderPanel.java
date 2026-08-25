package com.ams.hrms.component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.util.UiGraphics;

import net.miginfocom.swing.MigLayout;

/**
 * Top application bar (spec sections 5 and 36): sidebar toggle, module title,
 * theme switch, notification bell with unread badge, and the user chip.
 * The header owns no navigation logic - it exposes callbacks only.
 */
public class HeaderPanel extends JPanel {

    private static final int HEIGHT = 60;

    private final JButton menuToggle = ModernButton.iconOnly("menu", "Toggle menu");
    private final JButton themeToggle = ModernButton.iconOnly("moon", "Switch to dark theme");
    private final NotificationButton bellButton = new NotificationButton();
    private final AvatarChip avatar = new AvatarChip();
    private final JLabel titleLabel = new JLabel();
    private final JLabel userNameLabel = new JLabel();
    private final JLabel userRoleLabel = new JLabel();
    private Runnable myProfileHandler;

    public HeaderPanel() {
        setLayout(new MigLayout(
                "insets 0 16 0 18, aligny center",
                "[][grow][]8[]12[]14[]",
                "[center]"));

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        userNameLabel.setFont(userNameLabel.getFont().deriveFont(Font.BOLD, 13f));
        userNameLabel.setForeground(Palette.color(Role.TEXT));
        userRoleLabel.setFont(userRoleLabel.getFont().deriveFont(Font.PLAIN, 11f));
        userRoleLabel.setForeground(Palette.color(Role.TEXT_MUTED));

        JPanel nameStack = new JPanel(new MigLayout("wrap 1, insets 0, gap 0"));
        nameStack.setOpaque(false);
        nameStack.add(userNameLabel);
        nameStack.add(userRoleLabel);

        // Clicking the user chip opens the profile dialog directly; the
        // dialog itself offers the change-password action.
        java.awt.event.MouseAdapter profileOpener = new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (myProfileHandler != null) {
                    myProfileHandler.run();
                }
            }
        };
        avatar.addMouseListener(profileOpener);
        nameStack.addMouseListener(profileOpener);
        avatar.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        nameStack.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));

        add(menuToggle);
        add(titleLabel, "gap 12, growx");
        add(bellButton);
        add(themeToggle);
        add(new javax.swing.JSeparator(javax.swing.SwingConstants.VERTICAL), "h 28!");
        add(avatar, "gapleft 6");
        add(nameStack, "gapleft 4");
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (titleLabel != null) {
            titleLabel.setForeground(Palette.color(Role.TEXT));
            userNameLabel.setForeground(Palette.color(Role.TEXT));
            userRoleLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(super.getPreferredSize().width, HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(Palette.color(Role.CARD_BG));
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Palette.color(Role.CARD_BORDER));
        g.fillRect(0, getHeight() - 1, getWidth(), 1);
        g.dispose();
    }

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    public void setTitle(String title) {
        titleLabel.setText(title == null ? "" : title);
        repaint();
    }

    public void setUser(String fullName, String roleName) {
        userNameLabel.setText(fullName == null ? "" : fullName);
        userRoleLabel.setText(roleName == null ? "" : roleName);
        avatar.setInitials(initialsOf(fullName));
        repaint();
    }

    /**
     * Shows the signed-in user's profile picture (stored square JPEG
     * thumbnail); falls back to the initials chip when null or undecodable.
     */
    public void setAvatar(byte[] jpegBytes) {
        avatar.setPhoto(decode(jpegBytes));
    }

    private static java.awt.Image decode(byte[] jpegBytes) {
        if (jpegBytes == null || jpegBytes.length == 0) {
            return null;
        }
        try {
            return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpegBytes));
        } catch (java.io.IOException e) {
            return null;
        }
    }

    public void setUnreadNotifications(int count) {
        bellButton.setUnreadCount(count);
    }

    /**
     * Shows sun icon in dark mode, moon in light mode. All header icons are
     * tinted to the sidebar text color so they match the menu.
     */
    public void refreshThemeIcon(boolean dark) {
        Color fg = Palette.color(Role.SIDEBAR_FG);
        menuToggle.setIcon(IconLoader.tinted("menu", IconLoader.SIZE_DEFAULT, fg));
        themeToggle.setIcon(IconLoader.tinted(dark ? "sun" : "moon", IconLoader.SIZE_DEFAULT, fg));
        themeToggle.setToolTipText(dark ? "Switch to light theme" : "Switch to dark theme");
        bellButton.setBellColor(fg);
    }

    // ------------------------------------------------------------------
    // Callbacks
    // ------------------------------------------------------------------

    public void onMenuToggle(Runnable handler) {
        menuToggle.addActionListener(event -> handler.run());
    }

    public void onThemeToggle(Runnable handler) {
        for (var listener : themeToggle.getActionListeners()) {
            themeToggle.removeActionListener(listener);
        }
        themeToggle.addActionListener(event -> handler.run());
    }

    public void onNotificationsClick(Runnable handler) {
        bellButton.addActionListener(event -> handler.run());
    }

    /** Registers the handler that opens the self-service profile dialog. */
    public void onMyProfile(Runnable handler) {
        this.myProfileHandler = handler;
    }

    private static String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "?";
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder initials = new StringBuilder();
        initials.append(Character.toUpperCase(parts[0].charAt(0)));
        if (parts.length > 1) {
            initials.append(Character.toUpperCase(parts[parts.length - 1].charAt(0)));
        }
        return initials.toString();
    }

    /** Bell button that paints a red dot when unread notifications exist. */
    private static final class NotificationButton extends JButton {

        private int unreadCount;

        NotificationButton() {
            setIcon(IconLoader.tinted("bell", IconLoader.SIZE_DEFAULT, Palette.color(Role.SIDEBAR_FG)));
            setToolTipText("Notifications");
            putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                    com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
            setFocusPainted(false);
        }

        void setBellColor(Color color) {
            setIcon(IconLoader.tinted("bell", IconLoader.SIZE_DEFAULT, color));
        }

        void setUnreadCount(int count) {
            this.unreadCount = Math.max(0, count);
            setToolTipText(unreadCount > 0 ? unreadCount + " unread notification(s)" : "Notifications");
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (unreadCount <= 0) {
                return;
            }
            Graphics2D g = (Graphics2D) graphics.create();
            UiGraphics.enableAntialiasing(g);
            g.setColor(Palette.color(Role.DANGER));
            int dot = 9;
            g.fillOval(getWidth() - 15, 9, dot, dot);
            g.dispose();
        }
    }

    /** Circular chip showing the user's picture, or their initials. */
    private static final class AvatarChip extends JComponent {

        private String initials = "?";
        private java.awt.Image photo;

        AvatarChip() {
            Font base = javax.swing.UIManager.getFont("defaultFont");
            if (base == null) {
                base = new JLabel("?").getFont();
            }
            setFont(base.deriveFont(Font.BOLD, 12f));
            setForeground(Color.WHITE);
            setPreferredSize(new Dimension(34, 34));
        }

        void setInitials(String value) {
            this.initials = value == null || value.isBlank() ? "?" : value;
            repaint();
        }

        void setPhoto(java.awt.Image image) {
            this.photo = image;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            UiGraphics.enableAntialiasing(g);
            int size = Math.min(getWidth(), getHeight()) - 2;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            if (photo != null && photo.getWidth(this) > 0) {
                java.awt.Shape circle = new java.awt.geom.Ellipse2D.Float(x, y, size, size);
                g.setClip(circle);
                g.drawImage(photo, x, y, size, size, null);
                g.setClip(null);
                g.setColor(Palette.color(Role.CARD_BORDER));
                g.draw(circle);
                g.dispose();
                return;
            }

            g.setColor(Palette.color(Role.ACCENT));
            g.fillOval(x, y, size, size);

            g.setColor(Palette.readableForeground(Palette.color(Role.ACCENT)));
            Font font = getFont();
            g.setFont(font);
            java.awt.FontMetrics metrics = g.getFontMetrics();
            int textX = (getWidth() - metrics.stringWidth(initials)) / 2;
            int textY = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2;
            g.drawString(initials, textX, textY);
            g.dispose();
        }
    }
}
