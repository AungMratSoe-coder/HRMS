package com.ams.hrms.ui.theme;

import java.awt.Color;
import java.util.EnumMap;
import java.util.Map;

import javax.swing.UIManager;

/**
 * Centralized UI palette (spec section 35). Every custom-painted component
 * reads its colors from here - never from hard-coded values inside panels.
 *
 * <p>The dark theme surfaces are {@code #121212} (window), {@code #18181A}
 * (sidebar) and {@code #1E1F22} (cards/panels); text is {@code #F8FAFC}
 * primary and {@code #94A3B8} muted; accents are {@code #3B82F6} (blue),
 * {@code #10B981} (emerald), {@code #F59E0B} (amber), {@code #EF4444}
 * (red) and {@code #8B5CF6} (purple); grid/border lines use
 * {@code #2A2D32}. The light theme uses an {@code #F8FAFC} window,
 * {@code #F1F5F9} sidebar, white cards, {@code #0F172A}/{@code #64748B}
 * text and the same accent family. Statistic cards carry per-accent tinted
 * surfaces in both themes.</p>
 */
public final class Palette {

    /** Named color roles used by components. */
    public enum Role {
        ACCENT, ACCENT_SOFT,
        SUCCESS, WARNING, DANGER, INFO,
        NEUTRAL,
        CARD_BG, CARD_BORDER,
        TEXT, TEXT_MUTED,
        SURFACE_ALT,
        SIDEBAR_BG, SIDEBAR_ACTIVE_BG, SIDEBAR_HOVER_BG, SIDEBAR_FG, SIDEBAR_FG_MUTED, SIDEBAR_ACTIVE_FG
    }

    private static final Map<ThemeManager.Theme, Map<Role, Color>> THEMES = new EnumMap<>(ThemeManager.Theme.class);

    /**
     * Dark-mode statistic card background tints keyed by the card's accent
     * role: muted blue #212431, emerald #1C2925, amber #2C2820, soft red
     * #2C1F22, muted purple #25202E and a neutral slate surface. Blended
     * toward the card surface (#1E1F22) so cards stay subtle on dark.
     */
    private static final Map<Role, Color> DARK_STAT_TINTS = Map.of(
            Role.ACCENT, new Color(0x212431),
            Role.SUCCESS, new Color(0x1C2925),
            Role.WARNING, new Color(0x2C2820),
            Role.DANGER, new Color(0x2C1F22),
            Role.INFO, new Color(0x25202E),
            Role.NEUTRAL, new Color(0x232529));

    /**
     * Dark-mode statistic card borders - the accent blended halfway toward
     * the card surface, giving a quiet glow instead of a saturated outline.
     */
    private static final Map<Role, Color> DARK_STAT_BORDERS = Map.of(
            Role.ACCENT, new Color(0x2C508C),
            Role.SUCCESS, new Color(0x176C51),
            Role.WARNING, new Color(0x895E16),
            Role.DANGER, new Color(0x863133),
            Role.INFO, new Color(0x543D8C),
            Role.NEUTRAL, new Color(0x45494F));

    /**
     * Light-mode statistic card tints: whisper-soft blue #F7FBFF, green
     * #F8FEFA, amber #FFF9E3, red #FEF8F8, purple #FAF9FF and a neutral
     * slate surface for unaccented cards.
     */
    private static final Map<Role, Color> LIGHT_STAT_TINTS = Map.of(
            Role.ACCENT, new Color(0xF7FBFF),
            Role.SUCCESS, new Color(0xF8FEFA),
            Role.WARNING, new Color(0xFFF9E3),
            Role.DANGER, new Color(0xFEF8F8),
            Role.INFO, new Color(0xFAF9FF),
            Role.NEUTRAL, new Color(0xF6F9FB));

    /**
     * Light-mode statistic card borders - the accent blended halfway toward
     * white, giving a pastel outline instead of a saturated one.
     */
    private static final Map<Role, Color> LIGHT_STAT_BORDERS = Map.of(
            Role.ACCENT, new Color(0x9DC0FA),
            Role.SUCCESS, new Color(0x87DCC0),
            Role.WARNING, new Color(0xFACE85),
            Role.DANGER, new Color(0xF7A1A1),
            Role.INFO, new Color(0xC5ADFA),
            Role.NEUTRAL, new Color(0xCBD5E1));

    static {
        // ------------------------------------------------------------------
        // LIGHT theme
        // ------------------------------------------------------------------
        Map<Role, Color> light = new EnumMap<>(Role.class);
        light.put(Role.ACCENT, new Color(0x2563EB));
        light.put(Role.ACCENT_SOFT, new Color(0xEAF1FE));
        light.put(Role.SUCCESS, new Color(0x16A34A));
        light.put(Role.WARNING, new Color(0xD97706));
        light.put(Role.DANGER, new Color(0xDC2626));
        light.put(Role.INFO, new Color(0x0891B2));
        light.put(Role.CARD_BG, Color.WHITE);
        light.put(Role.CARD_BORDER, new Color(0xE2E8F0));
        light.put(Role.TEXT, new Color(0x0F172A));
        light.put(Role.TEXT_MUTED, new Color(0x64748B));
        light.put(Role.SURFACE_ALT, new Color(0xF8FAFC));
        putSidebar(light, false);
        THEMES.put(ThemeManager.Theme.LIGHT, light);

        // ------------------------------------------------------------------
        // DARK theme - surfaces #121212 / #18181A / #1E1F22, text
        // #F8FAFC / #94A3B8, accents #3B82F6 / #10B981 / #F59E0B /
        // #EF4444 / #8B5CF6, grid/border #2A2D32.
        // ------------------------------------------------------------------
        Map<Role, Color> dark = new EnumMap<>(Role.class);
        dark.put(Role.ACCENT, new Color(0x3B82F6));
        dark.put(Role.ACCENT_SOFT, new Color(59, 130, 246, 48));
        dark.put(Role.SUCCESS, new Color(0x10B981));
        dark.put(Role.WARNING, new Color(0xF59E0B));
        dark.put(Role.DANGER, new Color(0xEF4444));
        dark.put(Role.INFO, new Color(0x8B5CF6));
        dark.put(Role.CARD_BG, new Color(0x1E1F22));
        dark.put(Role.CARD_BORDER, new Color(0x2A2D32));
        dark.put(Role.TEXT, new Color(0xF8FAFC));
        dark.put(Role.TEXT_MUTED, new Color(0x94A3B8));
        dark.put(Role.SURFACE_ALT, new Color(0x121212));
        putSidebar(dark, true);
        THEMES.put(ThemeManager.Theme.DARK, dark);
    }

    private Palette() {
    }

    /**
     * Sidebar tones. The light theme uses the soft gray surface #F1F5F9
     * with a raised white active pill and slate labels; the dark theme uses
     * the charcoal surface #18181A.
     */
    private static void putSidebar(Map<Role, Color> map, boolean dark) {
        if (dark) {
            map.put(Role.SIDEBAR_BG, new Color(0x18181A));
            map.put(Role.SIDEBAR_ACTIVE_BG, new Color(0x2A2D32));
            map.put(Role.SIDEBAR_HOVER_BG, new Color(255, 255, 255, 14));
            map.put(Role.SIDEBAR_FG, new Color(0x94A3B8));
            map.put(Role.SIDEBAR_FG_MUTED, new Color(148, 163, 184, 150));
            map.put(Role.SIDEBAR_ACTIVE_FG, new Color(0xF8FAFC));
        } else {
            map.put(Role.SIDEBAR_BG, new Color(0xF1F5F9));
            map.put(Role.SIDEBAR_ACTIVE_BG, Color.WHITE);
            map.put(Role.SIDEBAR_HOVER_BG, new Color(15, 23, 42, 10));
            map.put(Role.SIDEBAR_FG, new Color(0x64748B));
            map.put(Role.SIDEBAR_FG_MUTED, new Color(0x94A3B8));
            map.put(Role.SIDEBAR_ACTIVE_FG, new Color(0x0F172A));
        }
    }

    /** Resolves a semantic role against the active theme. */
    public static Color color(Role role) {
        return THEMES.get(ThemeManager.current()).get(role);
    }

    /**
     * Statistic card surface for the card's accent role - muted tinted
     * backgrounds in dark mode (#212431 blue, #1C2925 emerald, #2C2820
     * amber, #2C1F22 red, #25202E purple) and pastel equivalents in light
     * mode. Unknown roles fall back to the plain card surface.
     */
    public static Color statCardBackground(Role role) {
        Map<Role, Color> tints = isDarkUi() ? DARK_STAT_TINTS : LIGHT_STAT_TINTS;
        return tints.getOrDefault(role, color(Role.CARD_BG));
    }

    /**
     * Statistic card border for the card's accent role - the pure accent
     * (e.g. #3B82F6, #10B981) in dark mode, softened accents in light mode.
     */
    public static Color statCardBorder(Role role) {
        Map<Role, Color> borders = isDarkUi() ? DARK_STAT_BORDERS : LIGHT_STAT_BORDERS;
        return borders.getOrDefault(role, color(Role.CARD_BORDER));
    }

    /** Convenience: opaque tint of the accent used for selected rows/chips. */
    public static Color accentSoft() {
        return color(Role.ACCENT_SOFT);
    }

    /**
     * Black or white - whichever reads against {@code background}. Used by
     * components that paint content on an accent fill, so the white dark-mode
     * accent automatically switches their content to black.
     */
    public static Color readableForeground(Color background) {
        double luminance = 0.2126 * background.getRed()
                + 0.7152 * background.getGreen()
                + 0.0722 * background.getBlue();
        return luminance >= 140 ? Color.BLACK : Color.WHITE;
    }

    /** FlatLaf outline marker for error states on text components. */
    public static String errorOutline() {
        return "error";
    }

    /** True when the UIManager background is dark (used by derived painters). */
    public static boolean isDarkUi() {
        Color window = UIManager.getColor("Panel.background");
        return window != null && window.getRed() + window.getGreen() + window.getBlue() < 3 * 100;
    }
}
