package com.ams.hrms.dto;

import java.math.BigDecimal;

/** Leave days consumed per leave type (current year). */
public record TypeUsage(String label, BigDecimal days) {
}
