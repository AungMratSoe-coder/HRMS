package com.ams.hrms.exception;

/**
 * Thrown when authentication fails (unknown email, bad password, disabled
 * account). The default user message is deliberately vague to avoid revealing
 * whether an account exists.
 */
public class AuthenticationException extends HrmsException {

    public static final String DEFAULT_USER_MESSAGE = "Invalid email or password.";

    public AuthenticationException() {
        super("Authentication failed", DEFAULT_USER_MESSAGE);
    }

    public AuthenticationException(String message) {
        super(message, DEFAULT_USER_MESSAGE);
    }

    public AuthenticationException(String message, String userMessage) {
        super(message, userMessage);
    }
}
