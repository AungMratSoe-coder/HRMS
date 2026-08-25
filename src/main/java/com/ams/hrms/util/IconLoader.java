package com.ams.hrms.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Icon;

import com.formdev.flatlaf.extras.FlatSVGIcon;

/**
 * Loads and caches SVG icons from {@code resources/icons}. All icons are
 * authored with {@code currentColor} strokes, so they automatically adopt the
 * foreground color of the component that displays them - hover states and
 * theme switches re-color them for free.
 *
 * <p>Instances are cached per (name, size); icons are immutable after
 * creation, so sharing them is safe.</p>
 */
public final class IconLoader {

    private static final Map<String, FlatSVGIcon> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, FlatSVGIcon> TINTED_CACHE = new ConcurrentHashMap<>();

    /** Standard UI icon size (sidebar, buttons). */
    public static final int SIZE_DEFAULT = 20;

    /** Small inline icon size (form fields, table actions). */
    public static final int SIZE_SMALL = 16;

    private IconLoader() {
    }

    /** Returns a 20px icon. */
    public static Icon icon(String name) {
        return icon(name, SIZE_DEFAULT);
    }

    /** Returns a cached icon rendered at the given square size. */
    public static Icon icon(String name, int size) {
        String key = name + "@" + size;
        return CACHE.computeIfAbsent(key, k -> new FlatSVGIcon("icons/" + name + ".svg", size, size));
    }

    /** Returns an icon scaled for dense list rows. */
    public static Icon small(String name) {
        return icon(name, SIZE_SMALL);
    }

    /**
     * Returns a cached icon re-colored to {@code color} regardless of theme.
     * Uses a separate cache from {@link #icon(String, int)} so the shared
     * theme-following instances are never mutated.
     */
    public static Icon tinted(String name, int size, java.awt.Color color) {
        String key = name + "@" + size + "@" + Integer.toHexString(color.getRGB());
        return TINTED_CACHE.computeIfAbsent(key, k -> {
            FlatSVGIcon icon = new FlatSVGIcon("icons/" + name + ".svg", size, size);
            icon.setColorFilter(new FlatSVGIcon.ColorFilter(source -> color));
            return icon;
        });
    }
}
