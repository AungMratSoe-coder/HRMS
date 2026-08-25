package com.ams.hrms.component;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.JLabel;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.util.UiGraphics;

/**
 * Dashboard statistic card (spec sections 9 and 36): accent-tinted surface,
 * matching accent border, colored icon chip, title, large value, an optional
 * trend line and an optional top-right badge pill (e.g. "present", "late").
 */
public class DashboardCard extends javax.swing.JPanel {

    private static final int CARD_ARC = 14;
    private static final int ICON_CHIP = 42;
    private static final float TITLE_FONT_SIZE = 10f;
    private static final float BADGE_FONT_SIZE = 9f;
    private static final int BADGE_PADDING = 12;
    private static final int BADGE_HEIGHT = 15;

    private final String title;
    private final String iconName;
    private final Role accentRole;

    private String value = "-";
    private String deltaText;
    private boolean deltaPositive = true;
    private boolean noteOnly;
    private String badgeText;

    public DashboardCard(String title, String iconName, Role accentRole) {
        this.title = title.toUpperCase();
        this.iconName = iconName;
        this.accentRole = accentRole;
        setOpaque(false);
        setPreferredSize(new Dimension(240, 112));
    }

    public void setValue(String newValue) {
        this.value = newValue;
        repaint();
    }

    /** Sets the small trend text under the value; color follows positive flag. */
    public void setDelta(String text, boolean positive) {
        this.deltaText = text;
        this.deltaPositive = positive;
        this.noteOnly = false;
        repaint();
    }

    /** Neutral gray caption under the value (no trend arrow). */
    public void setNote(String note) {
        this.deltaText = note;
        this.noteOnly = true;
        repaint();
    }

    /** Short pill rendered in the card's top-right corner (e.g. "late"). */
    public void setBadge(String text) {
        this.badgeText = text == null || text.isBlank() ? null : text.toLowerCase();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        int width = getWidth();
        int height = getHeight();

        Color background = Palette.statCardBackground(accentRole);
        Color border = Palette.statCardBorder(accentRole);

        // Soft drop shadow for depth on the light theme's white cards
        if (!Palette.isDarkUi()) {
            UiGraphics.fillRoundRect(g, 3, 4, width - 6, height - 4, CARD_ARC,
                    new Color(15, 23, 42, 18));
        }

        UiGraphics.fillRoundRect(g, 0, 0, width, height, CARD_ARC, background);
        UiGraphics.drawRoundRect(g, 0, 0, width, height, CARD_ARC, border);

        // Icon chip - slightly raised over the tint; blended a bit stronger
        // than before so the chip stays visible with the muted card borders
        UiGraphics.fillRoundRect(g, 20, 20, ICON_CHIP, ICON_CHIP, 10,
                UiGraphics.blend(background, border, 0.28));
        IconLoader.tinted(iconName, 22, border).paintIcon(this, g,
                20 + (ICON_CHIP - 22) / 2, 20 + (ICON_CHIP - 22) / 2);

        // Title
        Font titleFont = g.getFont().deriveFont(Font.BOLD, TITLE_FONT_SIZE);
        g.setColor(Palette.color(Role.TEXT_MUTED));
        g.setFont(titleFont);
        int titleX = 20 + ICON_CHIP + 10;
        int titleMaxWidth = width - titleX - 12;
        if (badgeText != null) {
            titleMaxWidth -= badgeWidth(g) + 6;
        }
        g.drawString(fitText(title, titleFont, titleMaxWidth), titleX, 33);

        drawBadge(g, background, border);

        // Value and trend line align with the title column (right of the
        // icon chip), matching the reference design.
        int textX = 20 + ICON_CHIP + 10;
        g.setColor(Palette.color(Role.TEXT));
        g.setFont(g.getFont().deriveFont(Font.BOLD, 21f));
        g.drawString(value, textX, 78);

        // Delta
        if (deltaText != null && !deltaText.isBlank()) {
            g.setFont(g.getFont().deriveFont(Font.PLAIN, 11f));
            if (noteOnly) {
                g.setColor(Palette.color(Role.TEXT_MUTED));
                g.drawString(deltaText, textX, 98);
            } else {
                g.setColor(deltaPositive ? Palette.color(Role.SUCCESS) : Palette.color(Role.DANGER));
                g.drawString((deltaPositive ? "\u25B2 " : "\u25BC ") + deltaText, textX, 98);
            }
        }
        g.dispose();
    }

    private void drawBadge(Graphics2D g, Color background, Color border) {
        if (badgeText == null) {
            return;
        }
        Font font = g.getFont().deriveFont(Font.BOLD, BADGE_FONT_SIZE);
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics(font);
        int pillWidth = metrics.stringWidth(badgeText) + BADGE_PADDING;
        int x = getWidth() - pillWidth - 11;
        int y = 10;

        UiGraphics.fillRoundRect(g, x, y, pillWidth, BADGE_HEIGHT, BADGE_HEIGHT / 2,
                UiGraphics.blend(background, border, 0.30));

        int textY = y + (BADGE_HEIGHT + metrics.getAscent() - metrics.getDescent()) / 2;
        g.setColor(border);
        g.drawString(badgeText, x + BADGE_PADDING / 2, textY);
    }

    private int badgeWidth(Graphics2D g) {
        Font font = g.getFont().deriveFont(Font.BOLD, BADGE_FONT_SIZE);
        return g.getFontMetrics(font).stringWidth(badgeText) + BADGE_PADDING;
    }

    private String fitText(String text, Font font, int maxWidth) {
        FontMetrics metrics = getFontMetrics(font);
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        while (text.length() > 1 && metrics.stringWidth(text + ellipsis) > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text + ellipsis;
    }
}
