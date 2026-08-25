package com.ams.hrms.report;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Metadata for one report column (spec section 27): header caption, value
 * kind (drives alignment and formatting in tables, PDF and Excel) and a
 * relative width hint used by the PDF layout.
 */
public record ReportColumn(String header, Kind kind, int widthHint) {

    public enum Kind {
        TEXT, NUMBER, MONEY, DATE, TIME
    }

    public static ReportColumn text(String header) {
        return new ReportColumn(header, Kind.TEXT, 10);
    }

    public static ReportColumn number(String header) {
        return new ReportColumn(header, Kind.NUMBER, 7);
    }

    public static ReportColumn money(String header) {
        return new ReportColumn(header, Kind.MONEY, 9);
    }

    public static ReportColumn date(String header) {
        return new ReportColumn(header, Kind.DATE, 8);
    }

    public static ReportColumn time(String header) {
        return new ReportColumn(header, Kind.TIME, 6);
    }

    /**
     * Human-readable display form for UI tables. Raw typed values remain
     * available to the PDF/Excel writers which apply their own styling.
     */
    public String display(Object value) {
        if (value == null) {
            return "";
        }
        return switch (kind) {
            case TEXT -> String.valueOf(value);
            case NUMBER -> value instanceof BigDecimal decimal
                    ? decimal.stripTrailingZeros().toPlainString()
                    : String.valueOf(value);
            case MONEY -> String.format(Locale.US, "%,.2f", value);
            case DATE -> formatDate(value);
            case TIME -> formatTime(value);
        };
    }

    /** True when values of this column render right-aligned. */
    public boolean isRightAligned() {
        return kind == Kind.NUMBER || kind == Kind.MONEY;
    }

    private static String formatDate(Object value) {
        if (value instanceof LocalDate date) {
            return date.format(DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH));
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime.toLocalDate().format(DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH));
        }
        return String.valueOf(value);
    }

    private static String formatTime(Object value) {
        if (value instanceof LocalTime time) {
            return time.truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString();
        }
        String raw = String.valueOf(value);
        return raw.length() == 8 ? raw.substring(0, 5) : raw;
    }
}
