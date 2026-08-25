package com.ams.hrms.component;

import java.awt.Font;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;

import net.miginfocom.swing.MigLayout;

/**
 * Labeled form row: title (with required marker), editor and an inline error
 * message slot (spec sections 36 and 31). Dialogs compose forms from these
 * rows; validation code sets/clears errors without touching layout.
 */
public class FormField extends JPanel {

    private static final String OUTLINE_KEY = "JComponent.outline";

    private final JLabel titleLabel = new JLabel();
    private final JComponent editor;
    private final JLabel errorLabel = new JLabel();

    private FormField(String label, boolean required, JComponent editor) {
        this.editor = editor;
        setOpaque(false);
        setLayout(new MigLayout("wrap 1, insets 0, gap 3", "[grow,fill]", "[][][grow]"));

        titleLabel.setText(required ? label + " *" : label);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.PLAIN, 12f));

        errorLabel.setFont(errorLabel.getFont().deriveFont(Font.PLAIN, 11f));
        errorLabel.setVisible(false);

        if (editor instanceof JTextArea textArea) {
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new java.awt.Dimension(240, 84));
            add(titleLabel);
            add(scrollPane, "height 80::");
            add(errorLabel);
        } else {
            add(titleLabel);
            add(editor, "height 34!");
            add(errorLabel);
        }
    }

    /** Re-resolves palette colors after a theme switch. */
    @Override
    public void updateUI() {
        super.updateUI();
        if (titleLabel != null) {
            titleLabel.setForeground(Palette.color(Role.TEXT_MUTED));
            errorLabel.setForeground(Palette.color(Role.DANGER));
        }
    }

    // ------------------------------------------------------------------
    // Factories
    // ------------------------------------------------------------------

    public static FormField textField(String label, boolean required) {
        return new FormField(label, required, new JTextField());
    }

    public static FormField comboBox(String label, Object[] items, boolean required) {
        JComboBox<Object> combo = new JComboBox<>(items);
        return new FormField(label, required, combo);
    }

    public static FormField datePicker(String label, boolean required) {
        return new FormField(label, required, new DatePickerField());
    }

    public static FormField textArea(String label, boolean required) {
        JTextArea area = new JTextArea(3, 20);
        return new FormField(label, required, area);
    }

    public static FormField custom(String label, boolean required, JComponent component) {
        return new FormField(label, required, component);
    }

    // ------------------------------------------------------------------
    // Values
    // ------------------------------------------------------------------

    public String getText() {
        if (editor instanceof JTextField field) {
            return field.getText().trim();
        }
        if (editor instanceof JComboBox<?> combo && combo.getSelectedItem() != null) {
            return String.valueOf(combo.getSelectedItem());
        }
        if (editor instanceof JTextArea area) {
            return area.getText().trim();
        }
        return "";
    }

    public void setText(String value) {
        if (editor instanceof JTextField field) {
            field.setText(value == null ? "" : value);
        } else if (editor instanceof JTextArea area) {
            area.setText(value == null ? "" : value);
        } else if (editor instanceof JComboBox<?> combo) {
            combo.setSelectedItem(value);
        }
        clearError();
    }

    /** Returns the picked date, or null when the field is empty/invalid. */
    public LocalDate getDate() {
        if (editor instanceof DatePickerField picker) {
            return picker.getDate();
        }
        return null;
    }

    public void setDate(LocalDate date) {
        if (editor instanceof DatePickerField picker) {
            picker.setDate(date);
        }
        clearError();
    }

    /** Parses {@link #getText()} as a LocalDate when the editor holds dates. */
    public LocalDate getDateFromText() {
        String raw = getText();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException e) {
            setError("Invalid date format (expected YYYY-MM-DD)");
            return null;
        }
    }

    public JComponent editor() {
        return editor;
    }

    // ------------------------------------------------------------------
    // Validation feedback
    // ------------------------------------------------------------------

    public void setError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        markEditorOutline("error");
        revalidate();
        repaint();
    }

    public void clearError() {
        errorLabel.setText("");
        errorLabel.setVisible(false);
        markEditorOutline(null);
    }

    private void markEditorOutline(String outlineValue) {
        editor.putClientProperty(OUTLINE_KEY, outlineValue);
        editor.repaint();
    }
}
