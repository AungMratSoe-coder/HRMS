package com.ams.hrms.util;

import java.awt.Component;

import javax.swing.JOptionPane;

/**
 * Small helpers around standardized modal dialogs so confirmation styling is
 * consistent across modules.
 */
public final class Dialogs {

    private Dialogs() {
    }

    /** Yes/No confirmation; true only when the user explicitly confirms. */
    public static boolean confirm(Component parent, String title, String message) {
        int choice = JOptionPane.showConfirmDialog(
                parent,
                message,
                title,
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        return choice == JOptionPane.OK_OPTION;
    }

    /** Informational message dialog. */
    public static void info(Component parent, String title, String message) {
        JOptionPane.showMessageDialog(parent, message, title, JOptionPane.INFORMATION_MESSAGE);
    }

    /** Single-line text input; null when cancelled, trimmed otherwise. */
    public static String prompt(Component parent, String title, String message) {
        Object result = JOptionPane.showInputDialog(
                parent, message, title, JOptionPane.QUESTION_MESSAGE, null, null, "");
        return result == null ? null : result.toString().trim();
    }
}
