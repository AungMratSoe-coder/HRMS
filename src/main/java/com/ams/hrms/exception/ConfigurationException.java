package com.ams.hrms.exception;

/**
 * Thrown when application configuration is missing, unreadable or invalid
 * (e.g. no database URL configured). Fatal at startup.
 */
public class ConfigurationException extends HrmsException {

    public ConfigurationException(String message) {
        super(message);
    }

    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
