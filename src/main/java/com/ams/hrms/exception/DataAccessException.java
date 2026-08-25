package com.ams.hrms.exception;

/**
 * Wraps every low-level JDBC failure. SQL details (statements, vendor codes)
 * stay in the technical message for the log file; users only ever see
 * {@link #getUserMessage()}.
 */
public class DataAccessException extends HrmsException {

    public static final String DEFAULT_USER_MESSAGE =
            "A database problem prevented the operation. Please try again, or contact your administrator if the problem persists.";

    public DataAccessException(String message, Throwable cause) {
        super(message, DEFAULT_USER_MESSAGE, cause);
    }

    public DataAccessException(String message, String userMessage, Throwable cause) {
        super(message, userMessage, cause);
    }
}
