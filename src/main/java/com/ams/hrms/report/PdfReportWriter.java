package com.ams.hrms.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Renders a {@link ReportResult} as a professional landscape-A4 PDF
 * (spec section 27): company/title block, parameter echo, striped data table
 * with repeating headers on every page, a bold totals row and page footers.
 * Purely functional - no database or UI access.
 */
public final class PdfReportWriter {

    private static final PDRectangle PAGE =
            new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());
    private static final float MARGIN = 36;
    private static final float CONTENT_WIDTH = PAGE.getWidth() - 2 * MARGIN;
    private static final float TOP_Y = PAGE.getHeight() - MARGIN;
    private static final float BOTTOM_Y = 30;
    private static final float HEADER_BAND_HEIGHT = 20;
    private static final float ROW_HEIGHT = 15.5f;
    private static final float CELL_PADDING = 4;

    private static final PDFont FONT_BOLD = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont FONT_REGULAR = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    private static final Color ACCENT = new Color(37, 99, 235);
    private static final Color DARK = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color ZEBRA = new Color(241, 245, 249);
    private static final Color TOTAL_BG = new Color(234, 241, 254);
    private static final Color LINE = new Color(226, 232, 240);

    private static final float FONT_TITLE = 14;
    private static final float FONT_BODY = 8;
    private static final float FONT_SMALL = 7.5f;

    private PdfReportWriter() {
    }

    /** Renders the report and returns the PDF document bytes. */
    public static byte[] write(ReportResult result) throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            drawContent(document, result);
            drawFooters(document);
            document.save(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------
    // Content
    // ------------------------------------------------------------------

    private static void drawContent(PDDocument document, ReportResult result) throws IOException {
        float[] columnX = columnPositions(result.columns());
        List<Object[]> rows = result.rows();

        PDPage page = newPage(document);
        PDPageContentStream cs = newStream(document, page);
        float y = drawReportHeader(cs, result);

        int currentRow = 0;
        while (true) {
            boolean needHeader = currentRow == 0 || y - ROW_HEIGHT < BOTTOM_Y;
            if (y - ROW_HEIGHT < BOTTOM_Y) {
                cs.close();
                page = newPage(document);
                cs = newStream(document, page);
                y = drawContinuedHeader(cs, result);
                if (currentRow < rows.size()) {
                    y = drawColumnHeader(cs, result.columns(), columnX, y);
                }
            } else if (needHeader) {
                y = drawColumnHeader(cs, result.columns(), columnX, y);
            }

            if (currentRow >= rows.size()) {
                break;
            }
            Object[] row = rows.get(currentRow);
            boolean zebra = currentRow % 2 == 1;
            y = drawDataRow(cs, result.columns(), columnX, row, y, zebra,
                    FONT_REGULAR, Color.WHITE, DARK);
            currentRow++;
        }

        if (result.totalsRow() != null && result.hasData()) {
            y = drawDataRow(cs, result.columns(), columnX, result.totalsRow(), y - 2,
                    false, FONT_BOLD, TOTAL_BG, DARK);
        }
        cs.close();
    }

    private static float drawReportHeader(PDPageContentStream cs, ReportResult result)
            throws IOException {
        text(cs, result.companyName(), MARGIN, TOP_Y, FONT_BOLD, FONT_TITLE, ACCENT);
        text(cs, result.title().toUpperCase(), MARGIN, TOP_Y - 17, FONT_BOLD, 11.5f, DARK);
        String footer = result.footerText();
        textRight(cs, footer, MARGIN + CONTENT_WIDTH, TOP_Y, FONT_REGULAR, FONT_SMALL, MUTED);

        String subtitle = result.subtitle() + "   |   " + result.dataRowCount() + " record"
                + (result.dataRowCount() == 1 ? "" : "s");
        text(cs, subtitle, MARGIN, TOP_Y - 29, FONT_REGULAR, FONT_SMALL, MUTED);

        float lineY = TOP_Y - 38;
        rule(cs, lineY, LINE, 0.8f);
        return lineY - 8;
    }

    private static float drawContinuedHeader(PDPageContentStream cs, ReportResult result)
            throws IOException {
        text(cs, result.title() + "  (continued)", MARGIN, TOP_Y, FONT_BOLD, 10, MUTED);
        float lineY = TOP_Y - 12;
        rule(cs, lineY, LINE, 0.8f);
        return lineY - 6;
    }

    private static float drawColumnHeader(PDPageContentStream cs, java.util.List<ReportColumn> columns,
                                          float[] columnX, float y) throws IOException {
        cs.setNonStrokingColor(DARK);
        cs.addRect(MARGIN, y - HEADER_BAND_HEIGHT, CONTENT_WIDTH, HEADER_BAND_HEIGHT);
        cs.fill();

        for (int i = 0; i < columns.size(); i++) {
            ReportColumn column = columns.get(i);
            float columnWidth = columnX[i + 1] - columnX[i] - 2 * CELL_PADDING;
            String header = fit(column.header(), FONT_BOLD, FONT_BODY, columnWidth);
            float x = column.isRightAligned()
                    ? columnX[i + 1] - CELL_PADDING - width(header, FONT_BOLD, FONT_BODY)
                    : columnX[i] + CELL_PADDING;
            text(cs, header, x, y - 13.5f, FONT_BOLD, FONT_BODY, Color.WHITE);
        }
        return y - HEADER_BAND_HEIGHT;
    }

    private static float drawDataRow(PDPageContentStream cs, java.util.List<ReportColumn> columns,
                                     float[] columnX, Object[] values, float y,
                                     boolean zebra, PDFont font, Color background,
                                     Color foreground) throws IOException {
        if (zebra) {
            cs.setNonStrokingColor(ZEBRA);
            cs.addRect(MARGIN, y - ROW_HEIGHT, CONTENT_WIDTH, ROW_HEIGHT);
            cs.fill();
        }
        for (int i = 0; i < columns.size(); i++) {
            String cell = i < values.length ? columns.get(i).display(values[i]) : "";
            float columnWidth = columnX[i + 1] - columnX[i] - 2 * CELL_PADDING;
            String fitted = fit(cell, font, FONT_BODY, columnWidth);
            float x = columns.get(i).isRightAligned()
                    ? columnX[i + 1] - CELL_PADDING - width(fitted, font, FONT_BODY)
                    : columnX[i] + CELL_PADDING;
            text(cs, fitted, x, y - 11.5f, font, FONT_BODY, foreground);
        }
        cs.setStrokingColor(LINE);
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, y - ROW_HEIGHT);
        cs.lineTo(MARGIN + CONTENT_WIDTH, y - ROW_HEIGHT);
        cs.stroke();
        return y - ROW_HEIGHT;
    }

    // ------------------------------------------------------------------
    // Footers (second pass so the total page count is known)
    // ------------------------------------------------------------------

    private static void drawFooters(PDDocument document) throws IOException {
        int totalPages = document.getNumberOfPages();
        for (int index = 0; index < totalPages; index++) {
            try (PDPageContentStream cs = new PDPageContentStream(document,
                    document.getPage(index), PDPageContentStream.AppendMode.APPEND,
                    true, true)) {
                String label = "Page " + (index + 1) + " of " + totalPages;
                text(cs, label, PAGE.getWidth() - MARGIN - 60, BOTTOM_Y - 12,
                        FONT_REGULAR, FONT_SMALL, MUTED);
            }
        }
    }

    // ------------------------------------------------------------------
    // Layout helpers
    // ------------------------------------------------------------------

    /**
     * X coordinates of every column edge: width hints are normalized to the
     * printable width with a minimum floor for narrow numeric columns.
     */
    private static float[] columnPositions(java.util.List<ReportColumn> columns) {
        double hintTotal = 0;
        for (ReportColumn column : columns) {
            hintTotal += Math.max(column.widthHint(), 5);
        }
        float[] edges = new float[columns.size() + 1];
        edges[0] = MARGIN;
        float scale = CONTENT_WIDTH / (float) hintTotal;
        for (int i = 0; i < columns.size(); i++) {
            float width = Math.max((float) Math.max(columns.get(i).widthHint(), 5) * scale, 42);
            edges[i + 1] = Math.min(edges[i] + width, MARGIN + CONTENT_WIDTH);
        }
        edges[columns.size()] = MARGIN + CONTENT_WIDTH;
        return edges;
    }

    /** Truncates with an ellipsis when the string exceeds the available width. */
    private static String fit(String value, PDFont font, float size, float maxWidth)
            throws IOException {
        if (width(value, font, size) <= maxWidth) {
            return value;
        }
        String ellipsis = "...";
        String clipped = value;
        while (clipped.length() > 1
                && width(clipped + ellipsis, font, size) > maxWidth) {
            clipped = clipped.substring(0, clipped.length() - 1);
        }
        return clipped + ellipsis;
    }

    private static float width(String value, PDFont font, float size) throws IOException {
        try {
            return font.getStringWidth(value) / 1000 * size;
        } catch (IllegalArgumentException e) {
            // Characters outside WinAnsi (e.g. CJK): fall back to a per-char estimate.
            return value.length() * size * 0.9f;
        }
    }

    private static void text(PDPageContentStream cs, String value, float x, float y,
                             PDFont font, float size, Color color) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(value));
        cs.endText();
    }

    private static void textRight(PDPageContentStream cs, String value, float rightX,
                                  float y, PDFont font, float size, Color color)
            throws IOException {
        float width = width(value, font, size);
        text(cs, value, rightX - width, y, font, size, color);
    }

    private static void rule(PDPageContentStream cs, float y, Color color, float lineWidth)
            throws IOException {
        cs.setStrokingColor(color);
        cs.setLineWidth(lineWidth);
        cs.moveTo(MARGIN, y);
        cs.lineTo(MARGIN + CONTENT_WIDTH, y);
        cs.stroke();
    }

    /** Replaces characters the standard fonts cannot encode. */
    private static String sanitize(String value) {
        StringBuilder safe = new StringBuilder(value.length());
        for (char character : value.toCharArray()) {
            safe.append(character > 126 || character < 32 ? '?' : character);
        }
        return safe.toString();
    }

    private static PDPage newPage(PDDocument document) {
        PDPage page = new PDPage(PAGE);
        document.addPage(page);
        return page;
    }

    private static PDPageContentStream newStream(PDDocument document, PDPage page)
            throws IOException {
        return new PDPageContentStream(document, page);
    }
}

