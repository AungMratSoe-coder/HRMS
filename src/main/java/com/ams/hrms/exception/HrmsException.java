package com.ams.hrms.exception;

/**
 * Root of the HRMS exception hierarchy. Everything thrown intentionally by
 * this application derives from this class; only truly unexpected defects
 * surface as other RuntimeExceptions.
 */
public class HrmsException extends RuntimeException {

    private final String userMessage;

    public HrmsException(String message) {
        super(message);
        this.userMessage = message;
    }

    public HrmsException(String message, String userMessage) {
        super(message);
        this.userMessage = userMessage;
    }

    public HrmsException(String message, Throwable cause) {
        super(message, cause);
        this.userMessage = message;
    }

    public HrmsException(String message, String userMessage, Throwable cause) {
        super(message, cause);
        this.userMessage = userMessage;
    }

    /**
     * Safe, non-technical message suitable for display to end users. Never
     * contains SQL, stack traces or credentials.
     */
    public String getUserMessage() {
        return userMessage;
    }
}
