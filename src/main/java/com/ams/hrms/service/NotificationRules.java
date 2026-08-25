package com.ams.hrms.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.ams.hrms.model.Notification;
import com.ams.hrms.validator.Validators;

/**
 * Pure business rules for notifications (spec sections 31 and 41):
 * field validation against the {@code notifications} column limits and the
 * human-readable wording for every generated alert. No JDBC, no Swing -
 * verified entirely by unit tests.
 */
public final class NotificationRules {

    public static final int TITLE_MAX = 150;
    public static final int MESSAGE_MAX = 1000;
    public static final int REFERENCE_MODULE_MAX = 50;
    public static final int TRAINING_REMINDER_DAYS = 7;

    private static final DateTimeFormatter SESSION_FORMAT =
            DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm");

    private NotificationRules() {
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /** Returns one human-readable error per violated rule; empty when valid. */
    public static List<String> validate(String title, String message,
                                        String type, String referenceModule) {
        List<String> errors = new ArrayList<>();
        Validators.required(errors, title, "Title");
        Validators.maxLength(errors, title, TITLE_MAX, "Title");
        Validators.required(errors, message, "Message");
        Validators.maxLength(errors, message, MESSAGE_MAX, "Message");
        Validators.maxLength(errors, referenceModule, REFERENCE_MODULE_MAX,
                "Reference module");
        if (type == null || !Notification.TYPES.contains(type)) {
            errors.add("Notification type is invalid.");
        }
        return errors;
    }

    // ------------------------------------------------------------------
    // Document expiry wording
    // ------------------------------------------------------------------

    /** Relative wording for a document expiry date ("expires in 10 days"). */
    public static String expiryWording(LocalDate expiryDate, LocalDate today) {
        if (expiryDate == null) {
            return "has no expiry recorded";
        }
        long days = java.time.temporal.ChronoUnit.DAYS.between(today, expiryDate);
        if (days < 0) {
            return days == -1 ? "expired yesterday" : "expired " + (-days) + " days ago";
        }
        if (days == 0) {
            return "expires today";
        }
        if (days == 1) {
            return "expires tomorrow";
        }
        return "expires in " + days + " days";
    }

    public static String documentExpiryTitle(String employeeName) {
        String name = Validators.normalize(employeeName);
        return "Document expiring - " + (name.isEmpty() ? "employee" : name);
    }

    public static String documentExpiryMessage(String documentType, String fileName,
                                               LocalDate expiryDate, LocalDate today) {
        String file = Validators.normalize(fileName);
        return Validators.normalize(documentType)
                + " document" + (file.isEmpty() ? "" : " '" + file + "'")
                + " " + expiryWording(expiryDate, today) + ".";
    }

    // ------------------------------------------------------------------
    // Leave wording
    // ------------------------------------------------------------------

    public static String leaveDigestTitle(int pendingCount) {
        return "Pending leave approvals (" + pendingCount + ")";
    }

    public static String leaveDigestMessage(int pendingCount) {
        return pendingCount == 1
                ? "1 leave request is waiting for approval."
                : pendingCount + " leave requests are waiting for approval.";
    }

    public static String leaveDecidedTitle(String leaveCode, boolean approved) {
        return Validators.normalize(leaveCode) + (approved ? " approved" : " rejected");
    }

    public static String leaveDecidedMessage(String leaveCode, boolean approved,
                                             String decidedByName, long employeeId) {
        String decider = Validators.normalize(decidedByName);
        return "Your leave request " + Validators.normalize(leaveCode)
                + (approved ? " was approved" : " was rejected")
                + (decider.isEmpty() ? "." : " by " + decider + ".");
    }

    public static String leaveRequestedTitle(String requesterName) {
        String name = Validators.normalize(requesterName);
        return "Leave request submitted - " + (name.isEmpty() ? "employee" : name);
    }

    public static String leaveRequestedMessage(String leaveCode, String requesterName) {
        return Validators.normalize(requesterName) + " submitted leave request "
                + Validators.normalize(leaveCode) + " which is waiting for approval.";
    }

    // ------------------------------------------------------------------
    // Birthday / training / payroll wording
    // ------------------------------------------------------------------

    public static String birthdayTitle(String employeeName) {
        return "Happy Birthday - " + Validators.normalize(employeeName);
    }

    public static String birthdayMessage(String employeeName, String departmentName) {
        return Validators.normalize(employeeName) + " ("
                + Validators.normalize(departmentName) + ") celebrates a birthday today.";
    }

    public static String trainingReminderTitle(String programName) {
        return "Training reminder - " + Validators.normalize(programName);
    }

    public static String trainingReminderMessage(String programName,
                                                 LocalDateTime startDateTime,
                                                 String location) {
        String place = Validators.normalize(location);
        return "'" + Validators.normalize(programName) + "' starts on "
                + startDateTime.format(SESSION_FORMAT)
                + (place.isEmpty() ? "." : " at " + place + ".");
    }

    public static String payrollProcessedTitle(String stage, String periodLabel) {
        return "PAID".equals(stage)
                ? "Payroll paid - " + Validators.normalize(periodLabel)
                : "Payroll calculated - " + Validators.normalize(periodLabel);
    }

    public static String payrollProcessedMessage(String stage, String periodLabel,
                                                 int recordCount) {
        String action = "PAID".equals(stage) ? "marked as paid for" : "calculated for";
        return recordCount == 1
                ? "Payroll " + action + " " + Validators.normalize(periodLabel)
                        + " (1 employee)."
                : "Payroll " + action + " " + Validators.normalize(periodLabel)
                        + " (" + recordCount + " employees).";
    }
}
