package com.ams.hrms.component;

import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Centered error state with a retry action (spec sections 36 and 32).
 * Technical details stay in the log file; users see a clear message and a way
 * forward.
 */
public class ErrorPanel extends JPanel {

    private final JLabel iconLabel;
    private final JLabel titleLabel;
    private final JLabel messageLabel;

    public ErrorPanel(String message) {
        this(message, null);
    }

    public ErrorPanel(String message, Runnable retryAction) {
        setOpaque(false);
        setLayout(new MigLayout("wrap 1, align center center, gap 10"));

        iconLabel = new JLabel();
        iconLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

        titleLabel = new JLabel("Something went wrong", javax.swing.SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));

        messageLabel = new JLabel(
                "<html><div style='text-align:center;width:360px'>" + escape(message)
                        + "</div></html>",
                javax.swing.SwingConstants.CENTER);
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.PLAIN, 12f));

        add(iconLabel);
        add(titleLabel);
        add(messageLabel);

        if (retryAction != null) {
            ModernButton retryButton = new ModernButton("Try again", "refresh");
            retryButton.addActionListener(event -> retryAction.run());
            add(retryButton, "gaptop 8");
        }
        refreshColors();
    }

    /** Re-resolves palette colors after a theme switch. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (titleLabel != null) {
            refreshColors();
        }
    }

    private void refreshColors() {
        // FlatSVGIcon caches its rendered raster - re-tint explicitly per theme.
        iconLabel.setIcon(IconLoader.tinted("warning", 44, Palette.color(Role.DANGER)));
        titleLabel.setForeground(Palette.color(Role.TEXT));
        messageLabel.setForeground(Palette.color(Role.TEXT_MUTED));
    }

    private static String escape(String text) {
        return text == null ? "" : text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
