package com.ams.hrms.validator;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure-logic validation tests (spec section 55).
 */
class ValidatorsTest {

    @Test
    @DisplayName("required flags blank values only")
    void required() {
        List<String> errors = new ArrayList<>();
        Validators.required(errors, "  ", "Name");
        Validators.required(errors, "HR", "Code");

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("Name is required");
    }

    @Test
    @DisplayName("code pattern accepts HR-style codes and rejects others")
    void codePattern() {
        List<String> errors = new ArrayList<>();
        Validators.pattern(errors, "IT-DEV_2", "Code", Validators.CODE_PATTERN, "HR");
        Validators.pattern(errors, "bad code!", "Code", Validators.CODE_PATTERN, "HR");
        Validators.pattern(errors, "", "Code", Validators.CODE_PATTERN, "HR"); // blank skipped

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0)).contains("format is invalid");
    }

    @Test
    @DisplayName("money parsing: blank -> null, bad text -> error, scale enforced")
    void moneyParsing() {
        assertThat(Validators.parseMoney(new ArrayList<>(), "  ", "Salary")).isNull();

        List<String> errors = new ArrayList<>();
        assertThat(Validators.parseMoney(errors, "abc", "Salary")).isNull();
        assertThat(errors).hasSize(1);

        List<String> scaleErrors = new ArrayList<>();
        assertThat(Validators.parseMoney(scaleErrors, "10.999", "Salary")).isNull();
        assertThat(scaleErrors).hasSize(1);

        BigDecimal ok = Validators.parseMoney(new ArrayList<>(), "1499.50", "Salary");
        assertThat(ok).isEqualByComparingTo("1499.50");
    }

    @Test
    @DisplayName("salary range rejects inverted envelopes")
    void salaryRange() {
        List<String> errors = new ArrayList<>();
        Validators.salaryRange(errors, new BigDecimal("2000"), new BigDecimal("1000"));
        assertThat(errors).hasSize(1);

        errors.clear();
        Validators.salaryRange(errors, new BigDecimal("1000"), new BigDecimal("2000"));
        assertThat(errors).isEmpty();
    }
}
