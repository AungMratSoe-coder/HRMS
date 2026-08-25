package com.ams.hrms.exception;

/**
 * Thrown when a business rule prevents an operation (e.g. approving a payroll
 * that is already paid). The technical message targets logs, the user message
 * is shown in dialogs.
 */
public class BusinessException extends HrmsException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, String userMessage) {
        super(message, userMessage);
    }

    public BusinessException(String message, String userMessage, Throwable cause) {
        super(message, userMessage, cause);
    }
}
