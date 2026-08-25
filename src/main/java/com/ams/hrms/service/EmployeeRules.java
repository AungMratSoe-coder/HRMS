package com.ams.hrms.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.ams.hrms.model.Employee;
import com.ams.hrms.validator.Validators;

/**
 * Pure employee validation and normalization (spec sections 10, 31 and 55):
 * field rules, gender/employment vocabularies, join-date logic, self-manager
 * guard and position salary-envelope checks - no database, no UI.
 *
 * <p>Database-backed uniqueness checks (code, NRC) stay in
 * {@link EmployeeService}; this class owns everything decidable from the
 * submitted record alone.</p>
 */
public final class EmployeeRules {

    public static final Set<String> GENDERS = Set.of("MALE", "FEMALE", "OTHER");
    public static final Set<String> EMPLOYMENT_TYPES =
            Set.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERN", "PROBATION");

    private EmployeeRules() {
    }

    /** All record-local problems; empty when the payload is acceptable. */
    public static List<String> validate(Employee employee) {
        List<String> errors = new ArrayList<>();

        Validators.required(errors, employee.getCode(), "Employee code");
        Validators.pattern(errors, employee.getCode(), "Employee code",
                Validators.CODE_PATTERN, "EMP-0001");
        Validators.required(errors, employee.getFirstName(), "First name");
        Validators.required(errors, employee.getLastName(), "Last name");
        Validators.maxLength(errors, employee.getFirstName(), 75, "First name");
        Validators.maxLength(errors, employee.getLastName(), 75, "Last name");

        if (employee.getGender() == null || employee.getGender().isBlank()) {
            errors.add("Gender is required.");
        } else if (!GENDERS.contains(employee.getGender())) {
            errors.add("Gender must be MALE, FEMALE or OTHER.");
        }
        if (employee.getJoinDate() == null) {
            errors.add("Join date is required.");
        }
        if (employee.getDateOfBirth() != null && employee.getJoinDate() != null
                && !employee.getDateOfBirth().isBefore(employee.getJoinDate())) {
            errors.add("Date of birth must be before the join date.");
        }
        if (employee.getEmploymentType() == null
                || !EMPLOYMENT_TYPES.contains(employee.getEmploymentType())) {
            errors.add("Employment type is required.");
        }
        Validators.email(errors, employee.getEmail(), "Email");
        Validators.phone(errors, employee.getPhone(), "Phone");
        Validators.maxLength(errors, employee.getAddress(), 300, "Address");
        Validators.maxLength(errors, employee.getNrc(), 80, "NRC");

        if (employee.getBasicSalary() == null) {
            errors.add("Basic salary is required.");
        } else if (employee.getBasicSalary().signum() < 0) {
            errors.add("Basic salary cannot be negative.");
        }
        if (employee.getDepartmentId() == null) {
            errors.add("Department is required.");
        }
        if (employee.getPositionId() == null) {
            errors.add("Position is required.");
        }

        if (employee.getManagerId() != null && employee.getId() != null
                && employee.getManagerId().equals(employee.getId())) {
            errors.add("An employee cannot be their own manager.");
        }
        return errors;
    }

    /** Canonical stored form; blank unique columns become null (rule-safe). */
    public static void normalize(Employee employee) {
        employee.setCode(Validators.normalize(employee.getCode()).toUpperCase());
        employee.setFirstName(Validators.normalize(employee.getFirstName()));
        employee.setLastName(Validators.normalize(employee.getLastName()));
        employee.setEmail(Validators.normalize(employee.getEmail()));
        // Nullable unique columns must store NULL, never an empty string,
        // otherwise the second blank value violates the unique index.
        employee.setNrc(Validators.normalize(employee.getNrc()).isEmpty()
                ? null : Validators.normalize(employee.getNrc()));
    }

    /** Salary-envelope rule: basic salary inside [min, max] when defined. */
    public static List<String> salaryEnvelopeErrors(BigDecimal basicSalary,
                                                    BigDecimal minSalary,
                                                    BigDecimal maxSalary) {
        List<String> errors = new ArrayList<>();
        if (basicSalary == null || (minSalary == null && maxSalary == null)) {
            return errors;
        }
        if (minSalary != null && basicSalary.compareTo(minSalary) < 0) {
            errors.add("Basic salary is below the position minimum of "
                    + minSalary.toPlainString() + ".");
        }
        if (maxSalary != null && basicSalary.compareTo(maxSalary) > 0) {
            errors.add("Basic salary is above the position maximum of "
                    + maxSalary.toPlainString() + ".");
        }
        return errors;
    }
}
