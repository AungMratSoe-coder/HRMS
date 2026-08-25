package com.ams.hrms.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * One employee's fully-computed payroll for a period before persistence.
 * Built by {@code PayrollCalculator}; consumed by {@code PayrollService}.
 */
public record PayrollComputation(
        long employeeId,
        String employeeCode,
        String fullName,
        String departmentName,
        BigDecimal basicSalary,
        List<Line> allowanceLines,
        List<Line> bonusLines,
        List<Line> overtimeLines,
        List<Line> deductionLines,
        BigDecimal taxAmount,
        BigDecimal socialSecurity,
        BigDecimal otherDeduction,
        BigDecimal grossSalary,
        BigDecimal totalDeduction,
        BigDecimal netSalary,
        String currency) {

    /** One payroll line item referencing its source row. */
    public record Line(String category, String description,
                       BigDecimal amount, String referenceTable, Long referenceId) {
    }
}
