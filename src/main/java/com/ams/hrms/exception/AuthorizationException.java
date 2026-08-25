package com.ams.hrms.exception;

/**
 * Thrown when the current user lacks the permission required for an operation.
 * Raised by SecurityService inside the service layer, so hiding a menu item in
 * the UI is never the only line of defense.
 */
public class AuthorizationException extends HrmsException {

    public static final String DEFAULT_USER_MESSAGE = "You do not have permission to perform this action.";

    public AuthorizationException(String message) {
        super(message, DEFAULT_USER_MESSAGE);
    }

    public AuthorizationException(String message, String userMessage) {
        super(message, userMessage);
    }
}
