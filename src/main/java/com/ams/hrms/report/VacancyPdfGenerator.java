package com.ams.hrms.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Renders a job vacancy as a portrait A4 requisition PDF using PDFBox.
 * Layout: company header, vacancy title, info block, salary-range highlight,
 * wrapped job description and requirements sections and a footer. Long text
 * flows onto additional pages.
 */
public final class VacancyPdfGenerator {

    private static final PDRectangle PAGE = PDRectangle.A4;
    private static final float MARGIN = 50;
    private static final float CONTENT_WIDTH = PAGE.getWidth() - 2 * MARGIN;

    // Fonts
    private static final PDFont FONT_BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final PDFont FONT_REGULAR =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    // Colors
    private static final Color ACCENT = new Color(37, 99, 235);
    private static final Color DARK = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color LIGHT_LINE = new Color(226, 232, 240);

    // Layout constants
    private static final float ROW_HEIGHT = 18;
    private static final float LINE_HEIGHT = 14;
    private static final float FONT_SIZE_BODY = 10;
    private static final float FONT_SIZE_SMALL = 8;

    private VacancyPdfGenerator() {
    }

    /** Data holder for one vacancy document. */
    public record VacancyData(
            String companyName, String companyAddress,
            String vacancyCode, String title, String department, String position,
            String employmentType, String status,
            int headcount, long filledSeats,
            BigDecimal salaryMin, BigDecimal salaryMax, String currency,
            LocalDate openingDate, LocalDate closingDate,
            String jobDescription, String requirements,
            String generatedBy, String generatedAt) {
    }

    /**
     * Generates the vacancy PDF as bytes; used for printing and as the
     * core of the file-based overload.
     */
    public static byte[] generate(VacancyData data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            try (Canvas canvas = new Canvas(document)) {
                drawHeader(canvas, data);
                drawInfoBlock(canvas, data);
                drawSalaryRange(canvas, data);
                drawTextSection(canvas, "Job Description", data.jobDescription());
                drawTextSection(canvas, "Requirements", data.requirements());
                drawFooter(canvas, data);
            }
            document.save(output);
        }
        return output.toByteArray();
    }

    /**
     * Generates the vacancy PDF and writes it to {@code outputPath}.
     *
     * @return the output path (same as parameter, for chaining)
     */
    public static Path generate(VacancyData data, Path outputPath) throws IOException {
        byte[] pdf = generate(data);
        Path parent = outputPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(outputPath, pdf);
        return outputPath;
    }

    // ------------------------------------------------------------------
    // Page canvas with automatic pagination
    // ------------------------------------------------------------------

    /** Tracks the vertical cursor and flips to a fresh page when full. */
    private static final class Canvas implements AutoCloseable {

        private final PDDocument document;
        private PDPageContentStream cs;
        private float y;

        Canvas(PDDocument document) throws IOException {
            this.document = document;
            nextPage();
        }

        void nextPage() throws IOException {
            PDPage page = new PDPage(PAGE);
            document.addPage(page);
            if (cs != null) {
                cs.close();
            }
            cs = new PDPageContentStream(document, page);
            y = PAGE.getHeight() - MARGIN;
        }

        /** Starts a new page when the requested height no longer fits. */
        void ensure(float neededHeight) throws IOException {
            if (y - neededHeight < MARGIN) {
                nextPage();
            }
        }

        @Override
        public void close() throws IOException {
            cs.close();
        }
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    private static void drawHeader(Canvas canvas, VacancyData data) throws IOException {
        drawText(canvas, data.companyName(), MARGIN, canvas.y, FONT_BOLD, 16, ACCENT);
        canvas.y -= 12;
        drawText(canvas, data.companyAddress(), MARGIN, canvas.y, FONT_REGULAR,
                FONT_SIZE_SMALL, MUTED);
        canvas.y -= 16;
        drawText(canvas, "Job Vacancy  ·  " + data.vacancyCode(), MARGIN, canvas.y,
                FONT_BOLD, 12, DARK);
        canvas.y -= 10;
        drawLine(canvas, MARGIN, canvas.y, MARGIN + CONTENT_WIDTH, canvas.y, ACCENT, 1.5f);
        canvas.y -= 24;

        for (String line : wrap(data.title(), FONT_BOLD, 15, CONTENT_WIDTH)) {
            drawText(canvas, line, MARGIN, canvas.y, FONT_BOLD, 15, DARK);
            canvas.y -= 20;
        }
        canvas.y -= 4;
    }

    private static void drawInfoBlock(Canvas canvas, VacancyData data) throws IOException {
        infoRow(canvas, "Department", pretty(data.department()),
                "Position", pretty(data.position()));
        infoRow(canvas, "Employment Type", pretty(data.employmentType()),
                "Status", pretty(data.status()));
        infoRow(canvas, "Headcount", String.valueOf(data.headcount()),
                "Seats Filled", String.valueOf(data.filledSeats()));
        infoRow(canvas, "Opening Date", date(data.openingDate()),
                "Closing Date", date(data.closingDate()));
        canvas.y -= 10;
    }

    private static void infoRow(Canvas canvas, String label1, String value1,
                                String label2, String value2) throws IOException {
        canvas.ensure(ROW_HEIGHT);
        float col2X = MARGIN + CONTENT_WIDTH / 2;
        drawText(canvas, label1, MARGIN, canvas.y, FONT_REGULAR, FONT_SIZE_SMALL, MUTED);
        drawText(canvas, fit(value1, FONT_REGULAR, FONT_SIZE_BODY, 140),
                MARGIN + 100, canvas.y, FONT_REGULAR, FONT_SIZE_BODY, DARK);
        drawText(canvas, label2, col2X, canvas.y, FONT_REGULAR, FONT_SIZE_SMALL, MUTED);
        drawText(canvas, fit(value2, FONT_REGULAR, FONT_SIZE_BODY, 140),
                col2X + 100, canvas.y, FONT_REGULAR, FONT_SIZE_BODY, DARK);
        canvas.y -= ROW_HEIGHT;
    }

    private static void drawSalaryRange(Canvas canvas, VacancyData data) throws IOException {
        float boxHeight = 32;
        canvas.ensure(boxHeight + 10);
        canvas.y -= 6;
        float boxY = canvas.y - boxHeight;

        canvas.cs.setNonStrokingColor(ACCENT);
        canvas.cs.addRect(MARGIN, boxY, CONTENT_WIDTH, boxHeight);
        canvas.cs.fill();

        canvas.cs.beginText();
        canvas.cs.setFont(FONT_BOLD, 12);
        canvas.cs.setNonStrokingColor(Color.WHITE);
        canvas.cs.newLineAtOffset(MARGIN + 14, boxY + 11);
        canvas.cs.showText("SALARY RANGE");
        canvas.cs.endText();

        String range = salaryRange(data);
        canvas.cs.beginText();
        canvas.cs.setFont(FONT_BOLD, 12);
        canvas.cs.setNonStrokingColor(Color.WHITE);
        float textWidth = width(range, FONT_BOLD, 12);
        canvas.cs.newLineAtOffset(MARGIN + CONTENT_WIDTH - 14 - textWidth, boxY + 11);
        canvas.cs.showText(range);
        canvas.cs.endText();

        canvas.y = boxY - 16;
    }

    private static void drawTextSection(Canvas canvas, String heading, String body)
            throws IOException {
        if (body == null || body.isBlank()) {
            return;
        }
        canvas.ensure(ROW_HEIGHT * 2);
        drawSectionHeader(canvas, heading);
        for (String line : wrap(body, FONT_REGULAR, FONT_SIZE_BODY, CONTENT_WIDTH - 10)) {
            canvas.ensure(LINE_HEIGHT);
            drawText(canvas, line, MARGIN + 10, canvas.y, FONT_REGULAR, FONT_SIZE_BODY, DARK);
            canvas.y -= LINE_HEIGHT;
        }
        canvas.y -= 10;
    }

    private static void drawSectionHeader(Canvas canvas, String title) throws IOException {
        drawText(canvas, title.toUpperCase(), MARGIN, canvas.y, FONT_BOLD, 11, ACCENT);
        canvas.y -= 4;
        drawLine(canvas, MARGIN, canvas.y, MARGIN + CONTENT_WIDTH, canvas.y, LIGHT_LINE, 0.8f);
        canvas.y -= ROW_HEIGHT - 4;
    }

    private static void drawFooter(Canvas canvas, VacancyData data) throws IOException {
        canvas.ensure(ROW_HEIGHT + 14);
        canvas.y -= 6;
        drawLine(canvas, MARGIN, canvas.y, MARGIN + CONTENT_WIDTH, canvas.y, LIGHT_LINE, 0.8f);
        canvas.y -= 12;
        drawText(canvas, "Generated by " + data.generatedBy() + " on " + data.generatedAt()
                + "  ·  System-generated document.", MARGIN, canvas.y,
                FONT_REGULAR, FONT_SIZE_SMALL, MUTED);
    }

    // ------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------

    private static void drawText(Canvas canvas, String text, float x, float y,
                                 PDFont font, float size, Color color) throws IOException {
        canvas.cs.beginText();
        canvas.cs.setFont(font, size);
        canvas.cs.setNonStrokingColor(color);
        canvas.cs.newLineAtOffset(x, y);
        canvas.cs.showText(text);
        canvas.cs.endText();
    }

    private static void drawLine(Canvas canvas, float x1, float y1, float x2, float y2,
                                 Color color, float lineWidth) throws IOException {
        canvas.cs.setStrokingColor(color);
        canvas.cs.setLineWidth(lineWidth);
        canvas.cs.moveTo(x1, y1);
        canvas.cs.lineTo(x2, y2);
        canvas.cs.stroke();
    }

    // ------------------------------------------------------------------
    // Text helpers
    // ------------------------------------------------------------------

    private static String salaryRange(VacancyData data) {
        String currency = data.currency() == null || data.currency().isBlank()
                ? "" : " " + data.currency();
        if (data.salaryMin() == null && data.salaryMax() == null) {
            return "Negotiable";
        }
        if (data.salaryMin() != null && data.salaryMax() != null) {
            return money(data.salaryMin()) + " - " + money(data.salaryMax()) + currency;
        }
        return money(data.salaryMin() != null ? data.salaryMin() : data.salaryMax())
                + currency;
    }

    /** Greedy word wrap honouring explicit line breaks; drops empty lines. */
    private static List<String> wrap(String text, PDFont font, float size, float maxWidth)
            throws IOException {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return lines;
        }
        for (String paragraph : text.split("\\r?\\n")) {
            String trimmed = paragraph.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : trimmed.split("\\s+")) {
                String candidate = line.length() == 0 ? word : line + " " + word;
                if (width(candidate, font, size) <= maxWidth) {
                    line.setLength(0);
                    line.append(candidate);
                } else if (line.length() > 0) {
                    lines.add(line.toString());
                    line.setLength(0);
                    line.append(word);
                } else {
                    lines.add(fit(word, font, size, maxWidth));
                }
            }
            if (line.length() > 0) {
                lines.add(line.toString());
            }
        }
        return lines;
    }

    /** Truncates with an ellipsis when the text is wider than {@code maxWidth}. */
    private static String fit(String text, PDFont font, float size, float maxWidth)
            throws IOException {
        if (text == null || width(text, font, size) <= maxWidth) {
            return text == null ? "-" : text;
        }
        String truncated = text;
        while (truncated.length() > 1 && width(truncated + "...", font, size) > maxWidth) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated + "...";
    }

    private static float width(String text, PDFont font, float size) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    /** FULL_TIME -&gt; "Full Time"; blank/null -&gt; "-". */
    private static String pretty(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String[] parts = value.trim().split("_");
        StringBuilder text = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (text.length() > 0) {
                text.append(' ');
            }
            text.append(Character.toUpperCase(part.charAt(0)))
                    .append(part.substring(1).toLowerCase());
        }
        return text.length() == 0 ? "-" : text.toString();
    }

    private static String date(LocalDate value) {
        return value == null ? "-" : value.toString();
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00" : String.format("%,.2f", value);
    }
}
