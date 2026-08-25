package com.ams.hrms.exception;

import java.awt.Component;
import java.awt.GraphicsEnvironment;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Central UI-side exception handler. Converts exceptions into professional,
 * non-technical dialogs while writing full technical detail to the log.
 * Stack traces are never shown to end users.
 */
public final class ErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ErrorHandler.class);

    private ErrorHandler() {
    }

    /**
     * Handles any exception thrown by a UI action and presents the appropriate
     * dialog to the user.
     */
    public static void handle(Component parent, Exception error) {
        switch (error) {
            case ValidationException e -> warn(parent, "Validation", e.getUserMessage());
            case BusinessException e -> warn(parent, "Operation Not Allowed", e.getUserMessage());
            case AuthorizationException e -> warn(parent, "Access Denied", e.getUserMessage());
            case AuthenticationException e -> warn(parent, "Sign In Failed", e.getUserMessage());
            case DataAccessException e -> {
                LOG.error("Database error: {}", e.getMessage(), e);
                errorDialog(parent, "Database Error", e.getUserMessage());
            }
            case HrmsException e -> {
                LOG.error("Application error: {}", e.getMessage(), e);
                errorDialog(parent, "Error", e.getUserMessage());
            }
            default -> {
                LOG.error("Unexpected error", error);
                errorDialog(parent, "Unexpected Error",
                        "An unexpected problem occurred. The details have been logged. Please try again.");
            }
        }
    }

    /** Convenience overload for callers without a parent component. */
    public static void handle(Exception error) {
        handle(null, error);
    }

    /** Logs a fatal startup-level failure without any UI dependency. */
    public static void fatal(String context, Throwable error) {
        LOG.error("FATAL - {}", context, error);
    }

    private static void warn(Component parent, String title, String message) {
        LOG.debug("{}: {}", title, message);
        if (!isHeadless()) {
            JOptionPane.showMessageDialog(parent, message, title, JOptionPane.WARNING_MESSAGE);
        }
    }

    private static void errorDialog(Component parent, String title, String message) {
        if (!isHeadless()) {
            JOptionPane.showMessageDialog(parent, message, title, JOptionPane.ERROR_MESSAGE);
        }
    }

    private static boolean isHeadless() {
        return GraphicsEnvironment.isHeadless();
    }
}
