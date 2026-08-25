package com.ams.hrms.component;

import java.awt.Color;
import java.awt.Font;
import javax.swing.JButton;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * Application-standard button (spec section 36). Variants map to semantic
 * palette colors; FlatLaf derives hover/pressed/focus states automatically
 * from the configured background, so no custom painting is needed.
 */
public class ModernButton extends JButton {

    public enum Variant {
        PRIMARY, SUCCESS, DANGER, OUTLINE, GHOST
    }

    private Variant variant;
    private final String iconName;

    public ModernButton(String text) {
        this(text, Variant.PRIMARY);
    }

    public ModernButton(String text, Variant variant) {
        super(text);
        this.iconName = null;
        applyVariant(variant);
    }

    public ModernButton(String text, String iconName) {
        this(text, iconName, Variant.PRIMARY);
    }

    public ModernButton(String text, String iconName, Variant variant) {
        super(text);
        this.iconName = iconName;
        setIconTextGap(8);
        applyVariant(variant);
    }

    /** Compact square button showing only an icon (toolbars, headers). */
    public static JButton iconOnly(String iconName, String toolTipText) {
        JButton button = new JButton() {
            @Override
            public void updateUI() {
                super.updateUI();
                // FlatSVGIcon caches its raster - re-tint per theme.
                setIcon(IconLoader.tinted(iconName, IconLoader.SIZE_DEFAULT,
                        Palette.color(Role.TEXT)));
            }
        };
        button.setToolTipText(toolTipText);
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE,
                FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        button.setFocusPainted(false);
        return button;
    }

    /** Re-resolves variant colors after a theme switch. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (variant != null) {
            applyVariant(variant);
        }
    }

    private void applyVariant(Variant variant) {
        this.variant = variant;
        setFocusPainted(false);
        setFont(getFont().deriveFont(Font.PLAIN, 13f));
        switch (variant) {
            case PRIMARY -> {
                Color background = Palette.color(Role.ACCENT);
                setBackground(background);
                setForeground(Palette.readableForeground(background));
            }
            case SUCCESS -> {
                Color background = Palette.color(Role.SUCCESS);
                setBackground(background);
                setForeground(Palette.readableForeground(background));
            }
            case DANGER -> {
                Color background = Palette.color(Role.DANGER);
                setBackground(background);
                setForeground(Palette.readableForeground(background));
            }
            case OUTLINE -> {
                setBackground(Palette.color(Role.CARD_BG));
                setForeground(Palette.color(Role.TEXT));
            }
            case GHOST -> {
                putClientProperty(FlatClientProperties.BUTTON_TYPE,
                        FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
                setForeground(Palette.color(Role.TEXT));
            }
        }
        if (iconName != null) {
            setIcon(IconLoader.tinted(iconName, IconLoader.SIZE_SMALL, getForeground()));
        }
    }
}
