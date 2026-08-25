package com.ams.hrms.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Phase 22 tests: report model formatting plus PDF/Excel writers, all fully
 * offline (no database).
 */
class ReportWritersTest {

    private static final ReportColumn[] COLUMNS = {
            ReportColumn.text("Code"),
            ReportColumn.text("Full Name"),
            ReportColumn.number("Days"),
            ReportColumn.money("Net Pay"),
            ReportColumn.date("Join Date")
    };

    private ReportResult sampleResult() {
        List<Object[]> rows = List.of(
                new Object[]{"EMP-0001", "Aung Kyaw", new BigDecimal("3"),
                        new BigDecimal("1500.50"), LocalDate.of(2024, 3, 1)},
                new Object[]{"EMP-0002", "Su Su", null,
                        new BigDecimal("9800.00"), LocalDate.of(2025, 11, 15)});
        Object[] totals = {"TOTAL", "", new BigDecimal("3"), new BigDecimal("11300.50"), ""};
        return new ReportResult("Employee List", "1 Jan 2026 - 31 Jan 2026",
                List.of(COLUMNS), rows, totals, "admin",
                LocalDateTime.of(2026, 1, 31, 17, 45), "Test Company");
    }

    // ------------------------------------------------------------------
    // ReportColumn
    // ------------------------------------------------------------------

    @Test
    @DisplayName("display(): money formats with thousands separator")
    void moneyFormatting() {
        assertThat(COLUMNS[3].display(new BigDecimal("1234567.89"))).isEqualTo("1,234,567.89");
        assertThat(COLUMNS[3].display(null)).isEmpty();
    }

    @Test
    @DisplayName("display(): numbers drop trailing zeros; times trim seconds")
    void numberAndTimeFormatting() {
        assertThat(COLUMNS[2].display(new BigDecimal("12.500"))).isEqualTo("12.5");
        ReportColumn checkIn = ReportColumn.time("Check In");
        assertThat(checkIn.display(java.time.LocalTime.of(8, 5, 0))).isEqualTo("08:05");
        assertThat(checkIn.display("08:05:00")).isEqualTo("08:05");
    }

    @Test
    @DisplayName("filter describe(): renders range, department and status echo")
    void filterDescription() {
        ReportFilter filter = new ReportFilter(LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31), 7L, "IT", null, "APPROVED");
        String description = filter.describe();
        assertThat(description).contains("1 Aug 2026 - 31 Aug 2026")
                .contains("Department: IT").contains("Status: APPROVED");
        assertThat(filter.keywordLike()).isNull();
    }

    @Test
    @DisplayName("ReportResult rejects null column lists defensively via List.copyOf")
    void resultCopiesLists() {
        ReportResult result = sampleResult();
        assertThat(result.dataRowCount()).isEqualTo(2);
        assertThat(result.hasData()).isTrue();
        assertThat(result.columns()).hasSize(5);
    }

    // ------------------------------------------------------------------
    // PDF writer
    // ------------------------------------------------------------------

    @Test
    @DisplayName("PdfReportWriter produces a valid multi-row PDF document")
    void pdfWriterProducesDocument() throws Exception {
        byte[] pdf = PdfReportWriter.write(sampleResult());
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
        }
    }

    @Test
    @DisplayName("PdfReportWriter handles empty results without totals row")
    void pdfWriterHandlesEmptyResult() throws Exception {
        ReportResult empty = new ReportResult("Late Report", "All records",
                List.of(COLUMNS), List.of(), null, "admin",
                LocalDateTime.now(), "Test Company");
        byte[] pdf = PdfReportWriter.write(empty);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("PdfReportWriter keeps right-aligned values inside the printable area")
    void pdfWriterRightAlignsNumericColumns() throws Exception {
        ReportColumn[] columns = {
                ReportColumn.text("Code"),
                ReportColumn.text("Department"),
                ReportColumn.text("Manager"),
                ReportColumn.number("Positions"),
                ReportColumn.number("Employees"),
                ReportColumn.number("Active"),
                ReportColumn.money("Total Basic Salary")
        };
        List<Object[]> rows = List.<Object[]>of(new Object[]{"FIN", "Finance", "Thiri Aung",
                new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("1"),
                new BigDecimal("2200.00")});
        Object[] totals = {"TOTAL", "", "", new BigDecimal("2"), new BigDecimal("1"),
                new BigDecimal("1"), new BigDecimal("2200.00")};
        ReportResult result = new ReportResult("Department Report", "All records",
                List.of(columns), rows, totals, "admin",
                LocalDateTime.of(2026, 8, 24, 12, 50), "Test Company");

        byte[] pdf = PdfReportWriter.write(result);
        try (PDDocument document = Loader.loadPDF(pdf)) {
            java.util.List<org.apache.pdfbox.text.TextPosition> positions =
                    new java.util.ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void writeString(String text,
                        java.util.List<TextPosition> textPositions) {
                    positions.addAll(textPositions);
                }
            };
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            stripper.getText(document);
            float pageWidth = document.getPage(0).getMediaBox().getWidth();
            assertThat(positions).isNotEmpty();
            for (TextPosition position : positions) {
                assertThat(position.getXDirAdj() + position.getWidthDirAdj())
                        .isLessThanOrEqualTo(pageWidth - 36 + 1);
            }
        }
    }

    // ------------------------------------------------------------------
    // Excel writer
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ExcelReportWriter writes header, typed values and totals")
    void excelWriterWritesWorkbook() throws Exception {
        byte[] workbookBytes = ExcelReportWriter.write(sampleResult());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            Sheet sheet = workbook.getSheetAt(0);

            Row headerRow = sheet.getRow(4);
            assertThat(headerRow.getCell(0).getStringCellValue()).isEqualTo("Code");
            assertThat(headerRow.getCell(3).getStringCellValue()).isEqualTo("Net Pay");

            Row firstDataRow = sheet.getRow(5);
            assertThat(firstDataRow.getCell(0).getStringCellValue()).isEqualTo("EMP-0001");
            assertThat(firstDataRow.getCell(3).getNumericCellValue()).isEqualTo(1500.50);

            Row secondDataRow = sheet.getRow(6);
            assertThat(secondDataRow.getCell(1).getStringCellValue()).isEqualTo("Su Su");

            Row totalsRow = sheet.getRow(7);
            assertThat(totalsRow.getCell(0).getStringCellValue()).isEqualTo("TOTAL");
            assertThat(totalsRow.getCell(3).getNumericCellValue()).isEqualTo(11300.50);

            // Dates are real date cells carrying the native dd mmm yyyy format
            assertThat(firstDataRow.getCell(4).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2024, 3, 1));
            String dateFormat = workbook.createDataFormat()
                    .getFormat(firstDataRow.getCell(4).getCellStyle().getDataFormat());
            assertThat(dateFormat).isEqualToIgnoringCase("dd mmm yyyy");

            // Money cells carry the thousands-aware native format
            String moneyFormat = workbook.createDataFormat()
                    .getFormat(firstDataRow.getCell(3).getCellStyle().getDataFormat());
            assertThat(moneyFormat).isEqualTo("#,##0.00");
        }
    }

    @Test
    @DisplayName("Excel title block is merged across the table so data drives column widths")
    void excelTitleBlockMergedAndColumnsSizedToData() throws Exception {
        byte[] workbookBytes = ExcelReportWriter.write(sampleResult());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            Sheet sheet = workbook.getSheetAt(0);

            assertThat(sheet.getNumMergedRegions()).isGreaterThanOrEqualTo(3);
            org.apache.poi.ss.util.CellRangeAddress firstBanner = sheet.getMergedRegion(0);
            assertThat(firstBanner.getFirstRow()).isZero();
            assertThat(firstBanner.getLastColumn()).isEqualTo(sampleResult().columns().size() - 1);

            // Column A holds short codes; the long banner text must not inflate it
            int codeColumnWidth = sheet.getColumnWidth(0);
            assertThat(codeColumnWidth).isLessThan(20 * 256);
            assertThat(codeColumnWidth).isGreaterThanOrEqualTo(8 * 256);
        }
    }

    @Test
    @DisplayName("Excel export writes typed time cells and print-friendly setup")
    void excelTypedTimesAndPrintSetup() throws Exception {
        ReportColumn[] columns = {
                ReportColumn.text("Employee"),
                ReportColumn.date("Work Date"),
                ReportColumn.time("Check In"),
                ReportColumn.number("Hours")
        };
        List<Object[]> rows = List.<Object[]>of(new Object[]{"Aung Kyaw",
                java.time.LocalDateTime.of(2026, 8, 1, 0, 0),
                java.time.LocalTime.of(8, 5, 12), new BigDecimal("8.5")});
        ReportResult result = new ReportResult("Attendance", "Aug 2026",
                List.of(columns), rows, null, "admin",
                LocalDateTime.of(2026, 8, 24, 9, 0), "Test Company");

        byte[] bytes = ExcelReportWriter.write(result);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row dataRow = sheet.getRow(5);

            // LocalDateTime feeds DATE columns as their calendar date part
            assertThat(dataRow.getCell(1).getLocalDateTimeCellValue().toLocalDate())
                    .isEqualTo(LocalDate.of(2026, 8, 1));

            // Time becomes a real fraction-of-day cell rounded to minutes (08:05)
            double dayFraction = dataRow.getCell(2).getNumericCellValue();
            int minutes = (int) Math.round(dayFraction * 24 * 60);
            assertThat(minutes).isEqualTo(8 * 60 + 5);

            String timeFormat = workbook.createDataFormat()
                    .getFormat(dataRow.getCell(2).getCellStyle().getDataFormat());
            assertThat(timeFormat).isEqualTo("hh:mm");

            // Printing: landscape, fit to one page wide, header repeats per page
            assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
            assertThat(sheet.getFitToPage()).isTrue();
            assertThat(sheet.getPrintSetup().getFitWidth()).isEqualTo((short) 1);
            assertThat(sheet.getRepeatingRows().formatAsString()).isEqualTo("5:5");
        }
    }

    @Test
    @DisplayName("ExcelReportWriter notes empty results instead of a bare table")
    void excelWriterHandlesEmptyResult() throws Exception {
        ReportResult empty = new ReportResult("Asset Report", "All records",
                List.of(COLUMNS), List.of(), null, "admin",
                LocalDateTime.now(), "Test Company");
        byte[] workbookBytes = ExcelReportWriter.write(empty);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(sheet.getLastRowNum()).getCell(0).getStringCellValue())
                    .contains("No records found");
        }
    }

    @Test
    @DisplayName("Excel fonts are human-sized (regression: setFontHeight unit mix-up produced 180pt text)")
    void excelFontSizesAreSane() throws Exception {
        byte[] workbookBytes = ExcelReportWriter.write(sampleResult());
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(fontPoints(sheet, 0, 0)).as("company banner font").isEqualTo(13);
            assertThat(fontPoints(sheet, 1, 0)).as("title font").isEqualTo(11);
            assertThat(fontPoints(sheet, 2, 0)).as("meta line font").isEqualTo(9);
            assertThat(fontPoints(sheet, 4, 0)).as("header font").isEqualTo(9);
            assertThat(fontPoints(sheet, 5, 0)).as("data font").isEqualTo(9);
        }
    }

    private static int fontPoints(Sheet sheet, int row, int column) {
        return ((org.apache.poi.xssf.usermodel.XSSFCellStyle)
                sheet.getRow(row).getCell(column).getCellStyle())
                .getFont().getFontHeightInPoints();
    }

    @Test
    @DisplayName("Sheet names are sanitized to Excel's 31 character limit")
    void sheetNameSanitized() throws Exception {
        ReportResult longTitle = new ReportResult(
                "An Extremely Long Report Title That Definitely Exceeds Limits",
                "-", List.of(COLUMNS), List.of(), null, "admin",
                LocalDateTime.now(), "Company");
        byte[] bytes = ExcelReportWriter.write(longTitle);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertThat(workbook.getSheetName(0).length()).isLessThanOrEqualTo(31);
        }
    }
}
