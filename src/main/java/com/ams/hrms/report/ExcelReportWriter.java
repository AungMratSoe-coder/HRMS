package com.ams.hrms.report;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Renders a {@link ReportResult} as a styled Excel workbook (spec section 27):
 * merged company/title block, frozen styled header, typed cells (money,
 * numbers, dates and times carry real Excel types with native formats),
 * striped rows and a bold totals row. Print setup fits the width landscape
 * and repeats the header on every page. Purely functional - no database or
 * UI access.
 */
public final class ExcelReportWriter {

    private static final byte[] WHITE = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
    private static final byte[] DARK = {(byte) 0x0F, (byte) 0x17, (byte) 0x2A};
    private static final byte[] MUTED = {(byte) 0x64, (byte) 0x74, (byte) 0x8B};
    private static final byte[] ACCENT = {(byte) 0x25, (byte) 0x63, (byte) 0xEB};
    private static final byte[] ZEBRA = {(byte) 0xF1, (byte) 0xF5, (byte) 0xF9};
    private static final byte[] TOTAL_BG = {(byte) 0xEA, (byte) 0xF1, (byte) 0xFE};
    private static final byte[] LINE_GREY = {(byte) 0xD8, (byte) 0xDE, (byte) 0xE6};

    private static final int MAX_COLUMN_WIDTH = 40 * 256;

    private ExcelReportWriter() {
    }

    /** Renders the report and returns the workbook bytes. */
    public static byte[] write(ReportResult result) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName(result.title()));

            Styles styles = new Styles(workbook);
            int columnCount = Math.max(result.columns().size(), 1);

            int rowIndex = 0;
            rowIndex = textRow(sheet, rowIndex, result.companyName(), styles.company());
            rowIndex = textRow(sheet, rowIndex, result.title(), styles.titleStyle());
            rowIndex = textRow(sheet, rowIndex, metaLine(result), styles.subtitle());
            rowIndex++; // spacer before the table

            // Merge the title block across the table so auto-sizing measures
            // data instead of the long banner strings in column A.
            if (columnCount > 1) {
                for (int bannerRow = 0; bannerRow < rowIndex - 1; bannerRow++) {
                    sheet.addMergedRegion(new CellRangeAddress(
                            bannerRow, bannerRow, 0, columnCount - 1));
                }
            }

            Row headerRow = sheet.createRow(rowIndex++);
            headerRow.setHeightInPoints(18f);
            int[] columnWidths = new int[columnCount];
            for (int i = 0; i < result.columns().size(); i++) {
                Cell cell = headerRow.createCell(i);
                String header = result.columns().get(i).header();
                cell.setCellValue(header);
                cell.setCellStyle(styles.header());
                columnWidths[i] = header.length();
            }
            sheet.createFreezePane(0, rowIndex);

            int headerRowIndex = rowIndex - 1;
            boolean zebra = false;
            for (Object[] values : result.rows()) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < result.columns().size(); i++) {
                    Cell cell = row.createCell(i);
                    Object value = i < values.length ? values[i] : null;
                    String display = result.columns().get(i).display(value);
                    cell.setCellValue(display);
                    columnWidths[i] = Math.max(columnWidths[i], display.length());
                    applyKind(cell, result.columns().get(i), value, styles,
                            zebra ? Styles.Scope.ZEBRA : Styles.Scope.PLAIN);
                }
                zebra = !zebra;
            }
            if (!result.hasData()) {
                Row emptyRow = sheet.createRow(rowIndex++);
                Cell cell = emptyRow.createCell(0);
                cell.setCellValue("No records found for the selected filters.");
                cell.setCellStyle(styles.subtitle());
            }

            if (result.totalsRow() != null && result.hasData()) {
                Row totalsRow = sheet.createRow(rowIndex);
                for (int i = 0; i < result.columns().size(); i++) {
                    Cell cell = totalsRow.createCell(i);
                    Object value = i < result.totalsRow().length ? result.totalsRow()[i] : null;
                    String display = result.columns().get(i).display(value);
                    cell.setCellValue(display);
                    columnWidths[i] = Math.max(columnWidths[i], display.length());
                    applyKind(cell, result.columns().get(i), value, styles, Styles.Scope.TOTALS);
                }
            }

            autoSizeColumns(sheet, columnWidths);
            configurePrinting(sheet, headerRowIndex);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Filter echo plus generation provenance on a single banner line. */
    private static String metaLine(ReportResult result) {
        String subtitle = result.subtitle();
        if (subtitle == null || subtitle.isBlank()) {
            return result.footerText();
        }
        return subtitle + "   |   " + result.footerText();
    }

    /**
     * Numeric kinds receive typed values (overriding the pre-set display
     * string) so Excel can sort and sum them natively; dates and times are
     * written as real date/time cells so chronological sorting works.
     */
    private static void applyKind(Cell cell, ReportColumn column, Object value,
                                  Styles styles, Styles.Scope scope) {
        switch (column.kind()) {
            case MONEY -> {
                if (value instanceof BigDecimal decimal) {
                    cell.setCellValue(decimal.doubleValue());
                }
            }
            case NUMBER -> {
                if (value instanceof BigDecimal decimal) {
                    cell.setCellValue(decimal.doubleValue());
                } else if (value instanceof Number number) {
                    cell.setCellValue(number.doubleValue());
                }
            }
            case DATE -> {
                if (value instanceof LocalDate date) {
                    cell.setCellValue(date);
                } else if (value instanceof LocalDateTime dateTime) {
                    cell.setCellValue(dateTime.toLocalDate());
                }
            }
            case TIME -> {
                if (value instanceof LocalTime time) {
                    int seconds = time.truncatedTo(ChronoUnit.MINUTES).toSecondOfDay();
                    cell.setCellValue(seconds / 86400d);
                }
            }
            default -> {
            }
        }
        cell.setCellStyle(styles.tableStyle(scope, column.kind()));
    }

    private static int textRow(Sheet sheet, int rowIndex, String value, CellStyle style) {
        Row row = sheet.createRow(rowIndex);
        Cell cell = row.createCell(0);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
        return rowIndex + 1;
    }

    /**
     * Sizes columns from the longest rendered cell text (header included)
     * plus padding. Deliberately avoids POI's font-metric autosizing, whose
     * AWT measurements vary wildly across machines; the banner rows above
     * the table are merged and excluded so they never inflate column A.
     */
    private static void autoSizeColumns(Sheet sheet, int[] columnWidths) {
        for (int i = 0; i < columnWidths.length && i < 30; i++) {
            int chars = Math.min(Math.max(columnWidths[i] + 3, 8),
                    MAX_COLUMN_WIDTH / 256);
            sheet.setColumnWidth(i, chars * 256);
        }
    }

    private static void configurePrinting(Sheet sheet, int headerRowIndex) {
        sheet.getPrintSetup().setLandscape(true);
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
        int oneBasedHeader = headerRowIndex + 1;
        sheet.setRepeatingRows(CellRangeAddress.valueOf(
                oneBasedHeader + ":" + oneBasedHeader));
    }

    private static String sheetName(String title) {
        String sanitized = title.replaceAll("[\\\\/*?:\\[\\]]", "").trim();
        return sanitized.length() > 31 ? sanitized.substring(0, 31) : sanitized;
    }

    /** Prebuilt cell styles grouped by table zone. */
    private static final class Styles {

        enum Scope { PLAIN, ZEBRA, TOTALS }

        private final XSSFWorkbook workbook;
        private final Map<String, CellStyle> tableStyles = new HashMap<>();
        private CellStyle company;
        private CellStyle titleStyle;
        private CellStyle subtitle;
        private CellStyle header;

        Styles(XSSFWorkbook workbook) {
            this.workbook = workbook;
        }

        CellStyle company() {
            if (company == null) {
                company = build(13, true, false, ACCENT, null, HorizontalAlignment.LEFT);
            }
            return company;
        }

        CellStyle titleStyle() {
            if (titleStyle == null) {
                titleStyle = build(11, true, false, DARK, null, HorizontalAlignment.LEFT);
            }
            return titleStyle;
        }

        CellStyle subtitle() {
            if (subtitle == null) {
                subtitle = build(9, false, true, MUTED, null, HorizontalAlignment.LEFT);
            }
            return subtitle;
        }

        CellStyle header() {
            if (header == null) {
                CellStyle style = build(9, true, false, WHITE, DARK, HorizontalAlignment.LEFT);
                style.setVerticalAlignment(VerticalAlignment.CENTER);
                header = style;
            }
            return header;
        }

        /** Bordered body style for one scope/kind pair, built lazily once. */
        CellStyle tableStyle(Scope scope, ReportColumn.Kind kind) {
            return tableStyles.computeIfAbsent(scope.name() + ':' + kind.name(),
                    key -> bordered(build(9, scope == Scope.TOTALS, false, DARK,
                            background(scope), alignment(kind)), format(kind)));
        }

        private byte[] background(Scope scope) {
            return switch (scope) {
                case TOTALS -> TOTAL_BG;
                case ZEBRA -> ZEBRA;
                case PLAIN -> null;
            };
        }

        private HorizontalAlignment alignment(ReportColumn.Kind kind) {
            return kind == ReportColumn.Kind.NUMBER || kind == ReportColumn.Kind.MONEY
                    ? HorizontalAlignment.RIGHT
                    : HorizontalAlignment.LEFT;
        }

        private String format(ReportColumn.Kind kind) {
            return switch (kind) {
                case MONEY -> "#,##0.00";
                case NUMBER -> "#,##0.##";
                case DATE -> "dd mmm yyyy";
                case TIME -> "hh:mm";
                case TEXT -> null;
            };
        }

        private CellStyle build(float fontHeight, boolean bold, boolean italic,
                                byte[] fontColor, byte[] background,
                                HorizontalAlignment alignment) {
            CellStyle style = workbook.createCellStyle();
            XSSFFont font = workbook.createFont();
            font.setFontHeightInPoints((short) fontHeight);
            font.setBold(bold);
            font.setItalic(italic);
            font.setColor(new XSSFColor(fontColor, null));
            style.setFont(font);
            style.setAlignment(alignment);
            if (background != null) {
                style.setFillForegroundColor(new XSSFColor(background, null));
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return style;
        }

        private CellStyle bordered(CellStyle style, String dataFormat) {
            XSSFCellStyle xssf = (XSSFCellStyle) style;
            xssf.setBorderTop(BorderStyle.THIN);
            xssf.setBorderBottom(BorderStyle.THIN);
            xssf.setBorderLeft(BorderStyle.THIN);
            xssf.setBorderRight(BorderStyle.THIN);
            xssf.setTopBorderColor(new XSSFColor(LINE_GREY, null));
            xssf.setBottomBorderColor(new XSSFColor(LINE_GREY, null));
            xssf.setLeftBorderColor(new XSSFColor(LINE_GREY, null));
            xssf.setRightBorderColor(new XSSFColor(LINE_GREY, null));
            if (dataFormat != null) {
                xssf.setDataFormat(workbook.createDataFormat().getFormat(dataFormat));
            }
            return xssf;
        }
    }
}
