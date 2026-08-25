package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Notification rules (spec sections 31, 41 and 55): validation against the
 * notifications column limits and the generated wording - pure logic,
 * verified without UI or database.
 */
class NotificationRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 23);

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    @Test
    void validNotificationPasses() {
        assertThat(NotificationRules.validate(
                "Title", "Body text", "INFO", "EMPLOYEE_DOCUMENT")).isEmpty();
    }

    @Test
    void blankTitleAndMessageRejected() {
        List<String> errors = NotificationRules.validate("  ", "", "INFO", null);
        assertThat(errors).anyMatch(e -> e.contains("Title"));
        assertThat(errors).anyMatch(e -> e.contains("Message"));
    }

    @Test
    void unknownTypeRejected() {
        assertThat(NotificationRules.validate("T", "M", "MAGIC", null))
                .anyMatch(e -> e.contains("type"));
        assertThat(NotificationRules.validate("T", "M", null, null))
                .anyMatch(e -> e.contains("type"));
    }

    @Test
    void oversizeFieldsRejected() {
        String longTitle = "x".repeat(NotificationRules.TITLE_MAX + 1);
        String longMessage = "x".repeat(NotificationRules.MESSAGE_MAX + 1);
        String longModule = "x".repeat(NotificationRules.REFERENCE_MODULE_MAX + 1);

        assertThat(NotificationRules.validate(longTitle, "M", "INFO", null))
                .anyMatch(e -> e.contains("Title"));
        assertThat(NotificationRules.validate("T", longMessage, "INFO", null))
                .anyMatch(e -> e.contains("Message"));
        assertThat(NotificationRules.validate("T", "M", "INFO", longModule))
                .anyMatch(e -> e.contains("Reference module"));
    }

    // ------------------------------------------------------------------
    // Expiry wording
    // ------------------------------------------------------------------

    @Test
    void missingExpiryHasNeutralWording() {
        assertThat(NotificationRules.expiryWording(null, TODAY))
                .isEqualTo("has no expiry recorded");
    }

    @Test
    void pastExpiriesUsePastWording() {
        assertThat(NotificationRules.expiryWording(TODAY.minusDays(1), TODAY))
                .isEqualTo("expired yesterday");
        assertThat(NotificationRules.expiryWording(TODAY.minusDays(9), TODAY))
                .isEqualTo("expired 9 days ago");
    }

    @Test
    void imminentExpiriesUseFutureWording() {
        assertThat(NotificationRules.expiryWording(TODAY, TODAY))
                .isEqualTo("expires today");
        assertThat(NotificationRules.expiryWording(TODAY.plusDays(1), TODAY))
                .isEqualTo("expires tomorrow");
        assertThat(NotificationRules.expiryWording(TODAY.plusDays(10), TODAY))
                .isEqualTo("expires in 10 days");
    }

    @Test
    void documentAlertIncludesTypeFileAndWording() {
        String message = NotificationRules.documentExpiryMessage(
                "NRC", "nrc.pdf", TODAY.plusDays(3), TODAY);
        assertThat(message)
                .contains("NRC").contains("nrc.pdf").contains("expires in 3 days");

        String anonymous = NotificationRules.documentExpiryMessage(
                "PASSPORT", "  ", TODAY, TODAY);
        assertThat(anonymous).doesNotContain("'").contains("expires today");
    }

    // ------------------------------------------------------------------
    // Leave wording
    // ------------------------------------------------------------------

    @Test
    void digestWordingPluralizes() {
        assertThat(NotificationRules.leaveDigestTitle(1)).isEqualTo("Pending leave approvals (1)");
        assertThat(NotificationRules.leaveDigestMessage(1)).contains("1 leave request is");
        assertThat(NotificationRules.leaveDigestMessage(4)).contains("4 leave requests are");
    }

    @Test
    void decisionWordingReflectsOutcome() {
        assertThat(NotificationRules.leaveDecidedTitle("LR-0007", true))
                .isEqualTo("LR-0007 approved");
        assertThat(NotificationRules.leaveDecidedTitle("LR-0007", false))
                .isEqualTo("LR-0007 rejected");
        assertThat(NotificationRules.leaveDecidedMessage("LR-0007", true, "HR Manager", 5))
                .contains("approved").contains("by HR Manager");
    }

    @Test
    void requestedWoldingNamesRequester() {
        assertThat(NotificationRules.leaveRequestedTitle("Aung Kyaw"))
                .isEqualTo("Leave request submitted - Aung Kyaw");
        assertThat(NotificationRules.leaveRequestedMessage("LR-0099", "Aung Kyaw"))
                .contains("LR-0099").contains("waiting for approval");
    }

    // ------------------------------------------------------------------
    // Birthday / training / payroll wording
    // ------------------------------------------------------------------

    @Test
    void birthdayWordingNamesEmployeeAndDepartment() {
        assertThat(NotificationRules.birthdayTitle("Su Su")).isEqualTo("Happy Birthday - Su Su");
        assertThat(NotificationRules.birthdayMessage("Su Su", "IT"))
                .contains("Su Su").contains("IT").contains("today");
    }

    @Test
    void trainingReminderFormatsSessionAndOptionalLocation() {
        LocalDateTime start = LocalDateTime.of(2026, 8, 25, 9, 0);

        assertThat(NotificationRules.trainingReminderMessage("Safety Basics", start, "Room 4"))
                .contains("Safety Basics").contains("2026").contains("09:00").contains("Room 4");
        assertThat(NotificationRules.trainingReminderMessage("Safety Basics", start, null))
                .endsWith(".");
    }

    @Test
    void payrollWordingReflectsStageAndCount() {
        assertThat(NotificationRules.payrollProcessedTitle("CALCULATED", "2026-08"))
                .isEqualTo("Payroll calculated - 2026-08");
        assertThat(NotificationRules.payrollProcessedTitle("PAID", "2026-08"))
                .isEqualTo("Payroll paid - 2026-08");

        assertThat(NotificationRules.payrollProcessedMessage("PAID", "2026-08", 1))
                .contains("marked as paid").contains("(1 employee)");
        assertThat(NotificationRules.payrollProcessedMessage("CALCULATED", "2026-08", 12))
                .contains("calculated for").contains("(12 employees)");
    }
}
