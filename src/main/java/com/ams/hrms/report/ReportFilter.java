package com.ams.hrms.report;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;

/**
 * Filter parameters for report generation (spec section 38): an optional
 * date range plus optional department, keyword and status constraints.
 * Which fields are meaningful is decided by {@link ReportDefinition}; the
 * service validates the combination before querying.
 */
public record ReportFilter(
        LocalDate dateFrom,
        LocalDate dateTo,
        Long departmentId,
        String departmentName,
        String keyword,
        String status) {

    private static final DateTimeFormatter RANGE = DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH);

    /** Compact filter with only a date range. */
    public static ReportFilter between(LocalDate from, LocalDate to) {
        return new ReportFilter(from, to, null, null, null, null);
    }

    /**
     * Human-readable echo of everything the user selected; shown under the
     * report title and inside exported files.
     */
    public String describe() {
        StringBuilder text = new StringBuilder();
        if (dateFrom != null && dateTo != null) {
            if (text.length() > 0) {
                text.append("  |  ");
            }
            text.append(dateFrom.format(RANGE)).append(" - ").append(dateTo.format(RANGE));
        }
        if (departmentName != null && !departmentName.isBlank()) {
            append(text, "Department: " + departmentName);
        }
        if (keyword != null && !keyword.isBlank()) {
            append(text, "Search: \"" + keyword.trim() + "\"");
        }
        if (status != null && !status.isBlank()) {
            append(text, "Status: " + status.replace('_', ' '));
        }
        return text.toString().isBlank() ? "All records" : text.toString();
    }

    /** Normalized keyword for LIKE queries ({@code null} when blank). */
    public String keywordLike() {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ReportFilter filter
                && Objects.equals(dateFrom, filter.dateFrom)
                && Objects.equals(dateTo, filter.dateTo)
                && Objects.equals(departmentId, filter.departmentId)
                && Objects.equals(departmentName, filter.departmentName)
                && Objects.equals(keyword, filter.keyword)
                && Objects.equals(status, filter.status);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dateFrom, dateTo, departmentId, departmentName, keyword, status);
    }

    private static void append(StringBuilder text, String part) {
        if (text.length() > 0) {
            text.append("  |  ");
        }
        text.append(part);
    }
}
