package com.ams.hrms.ui.theme;

import java.awt.AlphaComposite;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.RootPaneContainer;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.border.Border;

import com.ams.hrms.exception.ConfigurationException;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.FlatLightLaf;

/**
 * Owns the application look & feel (spec section 35). Supports light and dark
 * themes built on FlatLaf with centralized custom defaults from
 * {@code resources/flatlaf/*.properties}. Theme switching updates every open
 * window (main frame, dialogs) and notifies listeners so custom-painted
 * components can repaint. Switches crossfade: each open window's old look is
 * snapshotted and faded out over the freshly themed UI, and each window fades
 * independently so an open dialog cannot cancel the main frame's animation.
 */
public final class ThemeManager {

    public enum Theme {
        LIGHT, DARK
    }

    private static final String DEFAULTS_SOURCE = "flatlaf";
    private static final List<Consumer<Theme>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final int CROSSFADE_MS = 360;
    private static final int CROSSFADE_TICK_MS = 16;

    private static volatile Theme current = Theme.LIGHT;
    private static boolean installed;
    private static final Map<RootPaneContainer, ActiveCrossfade> activeCrossfades =
            new ConcurrentHashMap<>();

    /** One running window fade: its timer and the glass pane it drives. */
    private record ActiveCrossfade(Timer timer, JComponent glass) {
    }

    /** One window's captured old look awaiting its fade. */
    private record PendingFade(RootPaneContainer container, BufferedImage previous) {
    }

    private ThemeManager() {
    }

    /** Registers FlatLaf default overrides and applies the initial theme. */
    public static synchronized void install() {
        if (installed) {
            return;
        }
        FlatLaf.registerCustomDefaultsSource(DEFAULTS_SOURCE);
        applyTheme(current);
        installed = true;
    }

    public static Theme current() {
        return current;
    }

    public static boolean isDark() {
        return current == Theme.DARK;
    }

    /**
     * Switches the theme and refreshes every open window. Order matters for
     * a smooth transition: snapshot the old look first, then update the
     * Swing UI delegates (hidden windows too, so they never reopen stale),
     * then let custom-painted components rebuild with the new palette, and
     * only after all restyling work is done start the crossfade - so no
     * heavy rebuild stutters the animation mid-fade.
     */
    public static void setTheme(Theme theme) {
        applyTheme(theme);

        List<PendingFade> pending = new java.util.ArrayList<>();
        for (Window window : Window.getWindows()) {
            if (!(window instanceof RootPaneContainer container) || !window.isDisplayable()) {
                continue;
            }
            if (window.isShowing()) {
                BufferedImage previous = snapshot(window);
                if (previous != null) {
                    pending.add(new PendingFade(container, previous));
                }
            }
            SwingUtilities.updateComponentTreeUI(window);
            window.repaint();
        }

        for (Consumer<Theme> listener : LISTENERS) {
            listener.accept(theme);
        }

        for (PendingFade fade : pending) {
            crossfade(fade.container(), fade.previous());
        }
    }

    /** Toggles between light and dark across the whole app. */
    public static void toggle() {
        setTheme(isDark() ? Theme.LIGHT : Theme.DARK);
    }

    public static void addListener(Consumer<Theme> listener) {
        LISTENERS.add(listener);
    }

    /** Removes a previously registered listener (components call this in removeNotify). */
    public static void removeListener(Consumer<Theme> listener) {
        LISTENERS.remove(listener);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Renders the window's current look into an offscreen image. */
    private static BufferedImage snapshot(java.awt.Window window) {
        if (window.getWidth() <= 0 || window.getHeight() <= 0) {
            return null;
        }
        BufferedImage image = new BufferedImage(window.getWidth(), window.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        window.printAll(image.getGraphics());
        return image;
    }

    /**
     * Fades the window's previous look out over its freshly themed UI. Each
     * window fades independently; a new fade on the same window replaces the
     * running one. The glass pane is click-through so the UI stays usable
     * during the animation.
     */
    private static void crossfade(RootPaneContainer container, BufferedImage previous) {
        ActiveCrossfade running = activeCrossfades.remove(container);
        if (running != null) {
            running.timer().stop();
            running.glass().setVisible(false);
        }
        float[] alpha = {1f};
        JComponent glass = new JComponent() {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setComposite(AlphaComposite.SrcOver.derive(alpha[0]));
                g.drawImage(previous, 0, 0, getWidth(), getHeight(), null);
                g.dispose();
            }

            @Override
            public boolean contains(int x, int y) {
                return false;
            }
        };
        glass.setOpaque(false);
        container.setGlassPane(glass);
        glass.setVisible(true);

        long start = System.currentTimeMillis();
        Timer timer = new Timer(CROSSFADE_TICK_MS, event -> {
            double progress = Math.min(1.0,
                    (System.currentTimeMillis() - start) / (double) CROSSFADE_MS);
            alpha[0] = (float) (1 - easeInOutCubic(progress));
            glass.repaint();
            if (progress >= 1.0) {
                ((Timer) event.getSource()).stop();
                glass.setVisible(false);
                activeCrossfades.remove(container);
            }
        });
        activeCrossfades.put(container, new ActiveCrossfade(timer, glass));
        timer.start();
    }

    private static double easeInOutCubic(double progress) {
        return progress < 0.5
                ? 4 * progress * progress * progress
                : 1 - Math.pow(-2 * progress + 2, 3) / 2;
    }

    private static void applyTheme(Theme theme) {
        try {
            UIManager.setLookAndFeel(theme == Theme.LIGHT ? new FlatLightLaf() : new FlatDarkLaf());
        } catch (UnsupportedLookAndFeelException e) {
            throw new ConfigurationException("Failed to initialize the " + theme + " theme", e);
        }
        // Publish the theme first: palette-derived UI defaults below must
        // resolve against the NEW theme, not the previous one.
        current = theme;
        applyBaseDefaults();
    }

    /**
     * Application-wide UI defaults applied after every theme switch.
     * Fonts are chosen once per platform; everything else comes from the
     * flatlaf properties files.
     */
    private static void applyBaseDefaults() {
        UIManager.put("defaultFont", baseFont());
        // FlatLaf paints the scroll-pane border around tables/lists in the
        // accent color when the view gains focus. Replace those borders with
        // a barely-there hairline (border color blended halfway into the
        // surface) so tables read as containers without a hard outline and
        // never show a blue selection border; re-applied on every theme
        // switch so the color follows the palette.
        javax.swing.border.Border hairline = new javax.swing.plaf.BorderUIResource(
                new javax.swing.border.LineBorder(
                        com.ams.hrms.util.UiGraphics.blend(
                                com.ams.hrms.ui.theme.Palette.color(
                                        com.ams.hrms.ui.theme.Palette.Role.CARD_BORDER),
                                com.ams.hrms.ui.theme.Palette.color(
                                        com.ams.hrms.ui.theme.Palette.Role.SURFACE_ALT),
                                0.55)));
        UIManager.put("ScrollPane.border", hairline);
        UIManager.put("Table.scrollPaneBorder", hairline);
        // FlatLaf outlines the focused table cell with a 2px accent ring.
        // Selection is communicated by the row highlight alone; the ring is
        // disabled app-wide (it leaks through Swing's built-in number/date
        // renderers and any renderer that passes hasFocus through).
        Border noCellFocus = BorderFactory.createEmptyBorder();
        UIManager.put("Table.focusCellHighlightBorder", noCellFocus);
        UIManager.put("Table.focusSelectedCellHighlightBorder", noCellFocus);
    }

    private static Font baseFont() {
        String preferred = "Segoe UI";
        if (!fontAvailable(preferred)) {
            return new Font(Font.DIALOG, Font.PLAIN, 13);
        }
        return new Font(preferred, Font.PLAIN, 13);
    }

    private static boolean fontAvailable(String familyName) {
        for (String family : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames()) {
            if (family.equalsIgnoreCase(familyName)) {
                return true;
            }
        }
        return false;
    }
}
