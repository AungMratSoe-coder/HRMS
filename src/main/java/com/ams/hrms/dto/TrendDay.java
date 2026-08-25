package com.ams.hrms.dto;

import java.time.LocalDate;

/** One day of attendance aggregates for the trend chart. */
public record TrendDay(LocalDate date, long present, long late, long absent) {
}
