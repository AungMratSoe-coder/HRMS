package com.ams.hrms.exception;

import java.util.List;

/**
 * Thrown when input validation fails. Carries every individual problem so a
 * form can show all errors at once instead of one per attempt. The user
 * message is the joined problems themselves - dialogs and the central
 * {@link ErrorHandler} display the actual reasons, never a generic banner.
 */
public class ValidationException extends HrmsException {

    private final List<String> errors;

    public ValidationException(List<String> errors) {
        super(join(errors), "Please correct the highlighted problems.");
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("ValidationException requires at least one error");
        }
        this.errors = List.copyOf(errors);
    }

    public ValidationException(String message) {
        super(message);
        this.errors = List.of(message);
    }

    public ValidationException(String message, String userMessage) {
        super(message, userMessage);
        this.errors = List.of(message);
    }

    /** The individual problems, joined for direct display to the user. */
    @Override
    public String getUserMessage() {
        return String.join("; ", errors);
    }

    public List<String> getErrors() {
        return errors;
    }

    private static String join(List<String> errors) {
        return errors == null || errors.isEmpty()
                ? "Validation failed"
                : "Validation failed: " + String.join("; ", errors);
    }
}
