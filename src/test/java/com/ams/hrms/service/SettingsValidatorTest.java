package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Settings value rules: type checks, key-specific ranges, ISO currency,
 * IANA timezone and normalization - pure logic, no UI or database.
 */
class SettingsValidatorTest {

    private List<String> validate(String key, String type, String raw) {
        return SettingsValidator.validate(key, type, raw, "Test setting");
    }

    // ------------------------------------------------------------------
    // BOOLEAN
    // ------------------------------------------------------------------

    @Test
    void booleansAcceptAnyCaseAndNormalizeToLowercase() {
        assertThat(validate("leave.carry_forward_enabled", "BOOLEAN", "TRUE")).isEmpty();
        assertThat(validate("leave.carry_forward_enabled", "BOOLEAN", "False")).isEmpty();

        assertThat(SettingsValidator.normalize("BOOLEAN", "TRUE")).isEqualTo("true");
        assertThat(SettingsValidator.normalize("BOOLEAN", " False ")).isEqualTo("false");
    }

    @Test
    void booleanRejectsAnythingElse() {
        assertThat(validate("leave.carry_forward_enabled", "BOOLEAN", "yes"))
                .containsExactly("Test setting must be true or false.");
        assertThat(validate("leave.carry_forward_enabled", "BOOLEAN", ""))
                .isNotEmpty();
    }

    // ------------------------------------------------------------------
    // NUMBER with ranges
    // ------------------------------------------------------------------

    @Test
    void numberAcceptsIntegersAndDecimalsWithinRange() {
        assertThat(validate("payroll.tax_rate_percent", "NUMBER", "5")).isEmpty();
        assertThat(validate("payroll.overtime_rate_multiplier", "NUMBER", "1.5")).isEmpty();
        assertThat(validate("payroll.working_days_per_month", "NUMBER", "22")).isEmpty();
        assertThat(validate("documents.expiry_warning_days", "NUMBER", "0")).isEmpty();
        assertThat(validate("documents.expiry_warning_days", "NUMBER", "365")).isEmpty();
    }

    @Test
    void numberEnforcesKnownRangesPerKey() {
        assertThat(validate("payroll.tax_rate_percent", "NUMBER", "101"))
                .containsExactly("Test setting must be between 0 and 100.");
        assertThat(validate("payroll.working_days_per_month", "NUMBER", "0"))
                .containsExactly("Test setting must be between 1 and 31.");
        assertThat(validate("payroll.overtime_rate_multiplier", "NUMBER", "-1"))
                .containsExactly("Test setting must be between 0 and 10.");
    }

    @Test
    void numberWithoutRangeRuleAcceptsAnyValueButNotGarbage() {
        assertThat(SettingsValidator.NUMBER_RANGES)
                .doesNotContainKey("app.unknown_number");
        assertThat(validate("app.unknown_number", "NUMBER", "99999")).isEmpty();
        assertThat(validate("app.unknown_number", "NUMBER", "abc"))
                .containsExactly("Test setting must be a number (e.g. 22 or 1.5).");
        assertThat(validate("payroll.tax_rate_percent", "NUMBER", ""))
                .containsExactly("Test setting is required.");
    }

    // ------------------------------------------------------------------
    // STRING refinements
    // ------------------------------------------------------------------

    @Test
    void requiredKeysMustNotBeBlank() {
        assertThat(validate("company.name", "STRING", "  "))
                .containsExactly("Test setting is required.");
        assertThat(validate("attendance.default_shift_code", "STRING", ""))
                .containsExactly("Test setting is required.");
        assertThat(validate("company.address", "STRING", "")).isEmpty();
    }

    @Test
    void currencyMustBeThreeUppercaseLetters() {
        assertThat(validate("payroll.currency", "STRING", "USD")).isEmpty();
        assertThat(validate("payroll.currency", "STRING", "usd"))
                .containsExactly("Test setting must be a 3-letter ISO code (e.g. USD).");
        assertThat(validate("payroll.currency", "STRING", "USDT"))
                .containsExactly("Test setting must be a 3-letter ISO code (e.g. USD).");
    }

    @Test
    void timezoneMustBeAValidIANAZone() {
        assertThat(validate("app.timezone", "STRING", "Asia/Yangon")).isEmpty();
        assertThat(validate("app.timezone", "STRING", "UTC")).isEmpty();
        assertThat(validate("app.timezone", "STRING", "Mars/Olympus"))
                .containsExactly(
                        "Test setting is not a valid timezone (e.g. Asia/Yangon).");
    }

    @Test
    void ordinaryStringsHaveNoFormatRulesBeyondLength() {
        assertThat(validate("company.logo_path", "STRING",
                "D:/logos/logo with spaces.png")).isEmpty();
        assertThat(validate("company.name", "STRING", "x".repeat(1001)))
                .containsExactly("Test setting must be at most 1000 characters.");
    }

    // ------------------------------------------------------------------
    // Unknown type & label handling
    // ------------------------------------------------------------------

    @Test
    void unknownValueTypeIsReported() {
        assertThat(validate("anything.key", "TEXT", "value"))
                .containsExactly("Test setting has an unknown value type.");
    }

    @Test
    void keysTurnIntoReadableLabels() {
        assertThat(SettingsService.friendlyLabel("company.name"))
                .isEqualTo("Company Name");
        assertThat(SettingsService.friendlyLabel(
                "payroll.social_security_employee_percent"))
                .isEqualTo("Payroll Social Security Employee Percent");
    }
}
