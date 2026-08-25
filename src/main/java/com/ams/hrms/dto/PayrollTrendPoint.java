package com.ams.hrms.dto;

import java.math.BigDecimal;

/** Approved payroll gross for one period (payroll cost trend). */
public record PayrollTrendPoint(String periodLabel, BigDecimal gross) {
}
