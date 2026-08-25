package com.ams.hrms.util;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.geom.RoundRectangle2D;

/**
 * Low-level painting helpers shared by custom-painted components. Keeps
 * anti-aliasing setup and rounded-shape drawing consistent everywhere.
 */
public final class UiGraphics {

    private UiGraphics() {
    }

    /** Enables high-quality rendering hints on the given graphics context. */
    public static void enableAntialiasing(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** Fills an antialiased rounded rectangle with the given color. */
    public static void fillRoundRect(Graphics2D graphics, int x, int y, int width, int height,
                                     int arcRadius, java.awt.Color color) {
        enableAntialiasing(graphics);
        graphics.setColor(color);
        graphics.fill(new RoundRectangle2D.Float(x, y, width, height, arcRadius * 2, arcRadius * 2));
    }

    /** Draws an antialiased rounded rectangle border with the given color. */
    public static void drawRoundRect(Graphics2D graphics, int x, int y, int width, int height,
                                     int arcRadius, java.awt.Color color) {
        enableAntialiasing(graphics);
        graphics.setColor(color);
        graphics.draw(new RoundRectangle2D.Float(x + 0.5f, y + 0.5f, width - 1f, height - 1f,
                arcRadius * 2, arcRadius * 2));
    }

    /** Mixes two colors; factor 0 = base, 1 = overlay. */
    public static java.awt.Color blend(java.awt.Color base, java.awt.Color overlay, double factor) {
        int r = (int) Math.round(base.getRed() * (1 - factor) + overlay.getRed() * factor);
        int g = (int) Math.round(base.getGreen() * (1 - factor) + overlay.getGreen() * factor);
        int b = (int) Math.round(base.getBlue() * (1 - factor) + overlay.getBlue() * factor);
        return new java.awt.Color(r, g, b);
    }

    /** True when per-window translucency can be used for effects like fades. */
    public static boolean supportsWindowTranslucency(Window window) {
        if (window == null) {
            return false;
        }
        try {
            return window.getGraphicsConfiguration().getDevice()
                    .isWindowTranslucencySupported(java.awt.GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
