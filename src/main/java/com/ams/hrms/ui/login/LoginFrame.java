package com.ams.hrms.ui.login;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JFrame;
import javax.swing.JPanel;

import com.ams.hrms.config.AppConfig;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.UiGraphics;

/**
 * Sign-in window (spec section 7): branded brand rail on the left, the
 * credential form on the right. On success the frame hands over to
 * {@link com.ams.hrms.ui.main.MainFrame} via the success handler.
 */
public class LoginFrame extends JFrame {

    public LoginFrame() {
        super(AppConfig.get().appName() + " - Sign In");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setResizable(false);
        setSize(940, 600);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        add(new BrandPanel(), BorderLayout.WEST);
        LoginPanel loginPanel = new LoginPanel(this::onLoginSuccess);
        add(loginPanel, BorderLayout.CENTER);
        getRootPane().setDefaultButton(loginPanel.defaultButton());
    }

    private void onLoginSuccess() {
        // Administrators can force a password change; the flag rides on the session.
        if (com.ams.hrms.security.SessionContext.currentUser().mustChangePassword()) {
            boolean changed = com.ams.hrms.component.ChangePasswordDialog.show(this, true);
            if (!changed) {
                // Refused the forced change: end the session, stay signed out.
                com.ams.hrms.config.ServiceRegistry.authService().logout();
                return;
            }
        }
        com.ams.hrms.ui.main.MainFrame mainFrame = new com.ams.hrms.ui.main.MainFrame();
        mainFrame.setVisible(true);
        dispose();
    }

    /** Dark brand rail: monogram, product name, tagline and version. */
    private static final class BrandPanel extends JPanel {

        BrandPanel() {
            setPreferredSize(new Dimension(380, 600));
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            UiGraphics.enableAntialiasing(g);

            g.setColor(Palette.color(Role.SIDEBAR_BG));
            g.fillRect(0, 0, getWidth(), getHeight());

            // Monogram tile
            int tile = 64;
            int tileX = 48;
            int tileY = getHeight() / 2 - 150;
            UiGraphics.fillRoundRect(g, tileX, tileY, tile, tile, 14, Palette.color(Role.ACCENT));
            g.setColor(Palette.readableForeground(Palette.color(Role.ACCENT)));
            g.setFont(g.getFont().deriveFont(Font.BOLD, 24f));
            java.awt.FontMetrics metrics = g.getFontMetrics();
            String monogram = "HR";
            g.drawString(monogram,
                    tileX + (tile - metrics.stringWidth(monogram)) / 2,
                    tileY + (tile + metrics.getAscent() - metrics.getDescent()) / 2);

            // Product name
            g.setFont(g.getFont().deriveFont(Font.BOLD, 22f));
            g.setColor(Palette.color(Role.SIDEBAR_ACTIVE_FG));
            g.drawString("HR Management System", tileX - 4, tileY + tile + 46);

            // Tagline
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 13f));
            g.setColor(Palette.color(Role.SIDEBAR_FG));
            g.drawString("People operations for growing companies.", tileX, tileY + tile + 72);

            // Footer
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
            g.setColor(Palette.color(Role.SIDEBAR_FG_MUTED));
            g.drawString("v" + AppConfig.get().appVersion() + "  ·  AMS", tileX,
                    getHeight() - 40);

            g.dispose();
        }
    }
}
