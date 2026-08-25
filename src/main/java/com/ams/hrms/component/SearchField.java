package com.ams.hrms.component;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;

import javax.swing.JTextField;
import javax.swing.Timer;

import com.formdev.flatlaf.FlatClientProperties;

/**
 * Search input with a magnifier icon, placeholder text and a built-in clear
 * button (provided by FlatLaf). Text changes are debounced (300 ms) before
 * delivery; pressing Enter fires immediately.
 */
public class SearchField extends JTextField {

    private static final int DEBOUNCE_MS = 300;

    private final String iconName;
    private final Timer debounceTimer;
    private java.util.function.Consumer<String> textListener;

    public SearchField(String placeholder) {
        this(placeholder, "search");
    }

    public SearchField(String placeholder, String iconName) {
        super(20);
        this.iconName = iconName;
        setFont(getFont().deriveFont(Font.PLAIN, 13f));
        setPreferredSize(new Dimension(260, 36));
        putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
        putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        applyLeadingIcon();

        debounceTimer = new Timer(DEBOUNCE_MS, (ActionEvent event) -> fireTextChanged());
        debounceTimer.setRepeats(false);

        getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                schedule();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                schedule();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                schedule();
            }
        });

        addActionListener(event -> {
            debounceTimer.stop();
            fireTextChanged();
        });
    }

    /** Re-tints the leading icon after a theme switch. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (iconName != null) {
            applyLeadingIcon();
        }
    }

    private void applyLeadingIcon() {
        // FlatSVGIcon caches its raster - re-tint explicitly per theme.
        putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                com.ams.hrms.util.IconLoader.tinted(iconName,
                        com.ams.hrms.util.IconLoader.SIZE_SMALL, getForeground()));
    }

    /** Registers a debounced change listener receiving the current text. */
    public void onTextChanged(java.util.function.Consumer<String> listener) {
        this.textListener = listener;
    }

    private void schedule() {
        debounceTimer.restart();
    }

    private void fireTextChanged() {
        if (textListener != null) {
            textListener.accept(getText());
        }
    }
}
