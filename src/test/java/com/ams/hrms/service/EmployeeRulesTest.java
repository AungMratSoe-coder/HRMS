package com.ams.hrms.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ams.hrms.model.Employee;
import com.ams.hrms.service.EmployeeRules;

/**
 * Employee validation (spec sections 10, 31, 46 and 55): record-local field
 * rules, canonical normalization and the position salary envelope - pure
 * logic, verified without UI or database.
 */
class EmployeeRulesTest {

    /** A payload that passes every record-local rule. */
    private static Employee validEmployee() {
        Employee employee = new Employee();
        employee.setCode("emp-0100");
        employee.setFirstName("Aung");
        employee.setLastName("Kyaw");
        employee.setGender("MALE");
        employee.setDateOfBirth(LocalDate.of(1995, 4, 12));
        employee.setJoinDate(LocalDate.of(2024, 1, 2));
        employee.setEmploymentType("FULL_TIME");
        employee.setEmail("aung.kyaw@example.com");
        employee.setBasicSalary(new BigDecimal("1200"));
        employee.setDepartmentId(1L);
        employee.setPositionId(2L);
        return employee;
    }

    // ------------------------------------------------------------------
    // Field validation
    // ------------------------------------------------------------------

    @Test
    void validEmployeeHasNoErrors() {
        assertThat(EmployeeRules.validate(validEmployee())).isEmpty();
    }

    @Test
    void requiredFieldsAreEnforced() {
        List<String> errors = EmployeeRules.validate(new Employee());
        assertThat(errors)
                .anyMatch(e -> e.contains("Employee code"))
                .anyMatch(e -> e.contains("First name"))
                .anyMatch(e -> e.contains("Last name"))
                .anyMatch(e -> e.contains("Gender"))
                .anyMatch(e -> e.contains("Join date"))
                .anyMatch(e -> e.contains("Employment type"))
                .anyMatch(e -> e.contains("Basic salary"))
                .anyMatch(e -> e.contains("Department"))
                .anyMatch(e -> e.contains("Position"));
    }

    @Test
    void codeFormatIsChecked() {
        Employee employee = validEmployee();
        employee.setCode("bad code!");
        assertThat(EmployeeRules.validate(employee))
                .anyMatch(e -> e.contains("Employee code format"));
    }

    @Test
    void vocabulariesAreClosed() {
        Employee employee = validEmployee();
        employee.setGender("UNSPECIFIED");
        employee.setEmploymentType("GIG");
        List<String> errors = EmployeeRules.validate(employee);
        assertThat(errors).anyMatch(e -> e.contains("MALE, FEMALE or OTHER"));
        assertThat(errors).anyMatch(e -> e.contains("Employment type is required"));
    }

    @Test
    void birthMustPrecedeJoin() {
        Employee employee = validEmployee();
        employee.setDateOfBirth(LocalDate.of(2024, 1, 2));
        employee.setJoinDate(LocalDate.of(2024, 1, 2));
        assertThat(EmployeeRules.validate(employee))
                .anyMatch(e -> e.contains("before the join date"));

        // Equal dates are invalid; one day earlier is fine.
        employee.setDateOfBirth(LocalDate.of(2024, 1, 1));
        assertThat(EmployeeRules.validate(employee))
                .noneMatch(e -> e.contains("join date"));
    }

    @Test
    void contactFormatsAndMoneyBounds() {
        Employee employee = validEmployee();
        employee.setEmail("not-an-email");
        employee.setPhone("call me maybe");
        employee.setBasicSalary(new BigDecimal("-1"));
        List<String> errors = EmployeeRules.validate(employee);
        assertThat(errors)
                .anyMatch(e -> e.contains("Email"))
                .anyMatch(e -> e.contains("Phone"))
                .anyMatch(e -> e.contains("negative"));
    }

    @Test
    void selfManagementIsRejected() {
        Employee employee = validEmployee();
        employee.setId(7L);
        employee.setManagerId(7L);
        assertThat(EmployeeRules.validate(employee))
                .anyMatch(e -> e.contains("own manager"));

        // A different manager is fine.
        employee.setManagerId(8L);
        assertThat(EmployeeRules.validate(employee)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Normalization
    // ------------------------------------------------------------------

    @Test
    void normalizeUpperCasesCodeAndTrimsNames() {
        Employee employee = validEmployee();
        employee.setNrc("");
        EmployeeRules.normalize(employee);

        assertThat(employee.getCode()).isEqualTo("EMP-0100");
        assertThat(employee.getFirstName()).isEqualTo("Aung");
        assertThat(employee.getEmail()).isEqualTo("aung.kyaw@example.com");
        // Blank NRC becomes null: nullable unique columns must not hold "".
        assertThat(employee.getNrc()).isNull();
    }

    // ------------------------------------------------------------------
    // Salary envelope
    // ------------------------------------------------------------------

    @Test
    void salaryInsideEnvelopePasses() {
        assertThat(EmployeeRules.salaryEnvelopeErrors(
                new BigDecimal("1500"), new BigDecimal("900"), new BigDecimal("2200")))
                .isEmpty();
    }

    @Test
    void salaryOutsideEnvelopeIsRejected() {
        List<String> below = EmployeeRules.salaryEnvelopeErrors(
                new BigDecimal("800"), new BigDecimal("900"), new BigDecimal("2200"));
        assertThat(below).anyMatch(e -> e.contains("below the position minimum of 900"));

        List<String> above = EmployeeRules.salaryEnvelopeErrors(
                new BigDecimal("2500"), new BigDecimal("900"), new BigDecimal("2200"));
        assertThat(above).anyMatch(e -> e.contains("above the position maximum of 2200"));
    }

    @Test
    void openEndedEnvelopeChecksOnlyTheDefinedSide() {
        assertThat(EmployeeRules.salaryEnvelopeErrors(
                new BigDecimal("500"), null, new BigDecimal("2200"))).isEmpty();
        assertThat(EmployeeRules.salaryEnvelopeErrors(
                new BigDecimal("5000"), new BigDecimal("900"), null))
                .isEmpty();
    }
}
