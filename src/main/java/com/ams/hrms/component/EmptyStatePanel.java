package com.ams.hrms.component;

import java.awt.Font;

import javax.swing.JLabel;
import javax.swing.JPanel;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import net.miginfocom.swing.MigLayout;

/**
 * Centered empty-state placeholder for lists and tables (spec section 36):
 * large muted icon, title and an optional hint line.
 */
public class EmptyStatePanel extends JPanel {

    private final String iconName;
    private final JLabel iconLabel;
    private final JLabel titleLabel;
    private final JLabel subtitleLabel;

    public EmptyStatePanel(String iconName, String title, String subtitle) {
        this.iconName = iconName;
        setOpaque(false);
        setLayout(new MigLayout("wrap 1, align center center, gap 8"));

        iconLabel = new JLabel();
        iconLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);

        titleLabel = new JLabel(title, javax.swing.SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 15f));

        add(iconLabel);
        add(titleLabel);

        JLabel addedSubtitle = null;
        if (subtitle != null && !subtitle.isBlank()) {
            addedSubtitle = new JLabel(subtitle, javax.swing.SwingConstants.CENTER);
            addedSubtitle.setFont(addedSubtitle.getFont().deriveFont(Font.PLAIN, 12f));
            add(addedSubtitle);
        }
        this.subtitleLabel = addedSubtitle;
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
        // FlatSVGIcon caches its rendered raster, so a currentColor icon keeps
        // the color it was first painted with - re-tint explicitly per theme.
        iconLabel.setIcon(IconLoader.tinted(iconName, 44, Palette.color(Role.TEXT_MUTED)));
        titleLabel.setForeground(Palette.color(Role.TEXT));
        if (subtitleLabel != null) {
            subtitleLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        }
    }
}
