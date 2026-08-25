package com.ams.hrms.report;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.util.Matrix;

/**
 * Renders a job offer as a portrait A4 offer-letter PDF using PDFBox.
 * Layout: company header, letter heading, salutation and body paragraphs,
 * a terms table, dual signature blocks and a footer. Offers still in DRAFT
 * carry a diagonal watermark on every page. Long content flows onto
 * additional pages.
 */
public final class OfferPdfGenerator {

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
    private static final Color WATERMARK = new Color(226, 232, 240);

    // Layout constants
    private static final float ROW_HEIGHT = 18;
    private static final float LINE_HEIGHT = 14;
    private static final float FONT_SIZE_BODY = 10;
    private static final float FONT_SIZE_SMALL = 8;

    private OfferPdfGenerator() {
    }

    /** Data holder for one offer letter. */
    public record OfferData(
            String companyName, String companyAddress,
            String offerCode, String applicationCode,
            String candidateName, String position,
            BigDecimal offeredSalary, String currency,
            LocalDate offerDate, LocalDate expiryDate, LocalDate joiningDate,
            String status, boolean draft,
            String generatedBy, String generatedAt) {
    }

    /**
     * Generates the offer letter PDF as bytes; used for printing and as the
     * core of the file-based overload.
     */
    public static byte[] generate(OfferData data) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PDDocument document = new PDDocument()) {
            try (Canvas canvas = new Canvas(document, data.draft() ? "DRAFT" : null)) {
                drawHeader(canvas, data);
                drawBody(canvas, data);
                drawTerms(canvas, data);
                drawSignatures(canvas, data);
                drawFooter(canvas, data);
            }
            document.save(output);
        }
        return output.toByteArray();
    }

    /**
     * Generates the offer letter PDF and writes it to {@code outputPath}.
     *
     * @return the output path (same as parameter, for chaining)
     */
    public static Path generate(OfferData data, Path outputPath) throws IOException {
        byte[] pdf = generate(data);
        Path parent = outputPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(outputPath, pdf);
        return outputPath;
    }

    // ------------------------------------------------------------------
    // Page canvas with automatic pagination and watermark
    // ------------------------------------------------------------------

    /** Tracks the vertical cursor; every page carries the DRAFT watermark. */
    private static final class Canvas implements AutoCloseable {

        private final PDDocument document;
        private final String watermark;
        private PDPageContentStream cs;
        private float y;

        Canvas(PDDocument document, String watermark) throws IOException {
            this.document = document;
            this.watermark = watermark;
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
            if (watermark != null) {
                drawWatermark();
            }
        }

        private void drawWatermark() throws IOException {
            float size = 64;
            float textWidth = width(watermark, FONT_BOLD, size);
            cs.beginText();
            cs.setFont(FONT_BOLD, size);
            cs.setNonStrokingColor(WATERMARK);
            cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(45),
                    PAGE.getWidth() / 2 - textWidth / 2, PAGE.getHeight() / 3));
            cs.showText(watermark);
            cs.endText();
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

    private static void drawHeader(Canvas canvas, OfferData data) throws IOException {
        drawText(canvas, data.companyName(), MARGIN, canvas.y, FONT_BOLD, 16, ACCENT);
        canvas.y -= 12;
        drawText(canvas, data.companyAddress(), MARGIN, canvas.y, FONT_REGULAR,
                FONT_SIZE_SMALL, MUTED);
        canvas.y -= 16;
        drawText(canvas, "OFFER OF EMPLOYMENT", MARGIN, canvas.y, FONT_BOLD, 12, DARK);
        canvas.y -= 10;
        drawLine(canvas, MARGIN, canvas.y, MARGIN + CONTENT_WIDTH, canvas.y, ACCENT, 1.5f);
        canvas.y -= 14;

        drawText(canvas, "Offer " + data.offerCode() + "  ·  " + pretty(data.status())
                + "  ·  Application " + data.applicationCode(), MARGIN, canvas.y,
                FONT_REGULAR, FONT_SIZE_SMALL, MUTED);
        canvas.y -= 24;
    }

    private static void drawBody(Canvas canvas, OfferData data) throws IOException {
        paragraph(canvas, "Dear " + data.candidateName() + ",");
        canvas.y -= 6;
        paragraph(canvas, "We are pleased to offer you the position of "
                + pretty(data.position()) + " at " + data.companyName()
                + ". We were impressed with your interviews and believe your skills "
                + "will be a strong addition to our team.");
        canvas.y -= 6;

        String joining = data.joiningDate() == null
                ? "to be confirmed with you"
                : date(data.joiningDate());
        paragraph(canvas, "Your starting salary will be " + money(data.offeredSalary())
                + " " + data.currency() + ", with your joining date expected on "
                + joining + ".");
        canvas.y -= 6;

        if (data.expiryDate() != null) {
            paragraph(canvas, "This offer is valid until " + date(data.expiryDate())
                    + ". Please confirm your acceptance on or before that date.");
            canvas.y -= 6;
        }
        if (data.draft()) {
            paragraph(canvas, "This offer is a draft and has not yet been sent to the "
                    + "candidate.");
            canvas.y -= 6;
        }
        canvas.y -= 4;
    }

    private static void paragraph(Canvas canvas, String text) throws IOException {
        for (String line : wrap(text, FONT_REGULAR, FONT_SIZE_BODY, CONTENT_WIDTH)) {
            canvas.ensure(LINE_HEIGHT);
            drawText(canvas, line, MARGIN, canvas.y, FONT_REGULAR, FONT_SIZE_BODY, DARK);
            canvas.y -= LINE_HEIGHT;
        }
    }

    private static void drawTerms(Canvas canvas, OfferData data) throws IOException {
        canvas.ensure(ROW_HEIGHT * 3);
        drawText(canvas, "TERMS", MARGIN, canvas.y, FONT_BOLD, 11, ACCENT);
        canvas.y -= 4;
        drawLine(canvas, MARGIN, canvas.y, MARGIN + CONTENT_WIDTH, canvas.y, LIGHT_LINE, 0.8f);
        canvas.y -= ROW_HEIGHT - 4;

        termRow(canvas, "Position", pretty(data.position()));
        termRow(canvas, "Offered Salary",
                money(data.offeredSalary()) + " " + data.currency(), true);
        termRow(canvas, "Offer Date", date(data.offerDate()));
        termRow(canvas, "Valid Until", date(data.expiryDate()));
        termRow(canvas, "Joining Date", date(data.joiningDate()));
        termRow(canvas, "Status", pretty(data.status()));
        canvas.y -= 10;
    }

    private static void termRow(Canvas canvas, String label, String value)
            throws IOException {
        termRow(canvas, label, value, false);
    }

    private static void termRow(Canvas canvas, String label, String value, boolean bold)
            throws IOException {
        canvas.ensure(ROW_HEIGHT);
        var font = bold ? FONT_BOLD : FONT_REGULAR;
        drawText(canvas, label, MARGIN + 10, canvas.y, font, FONT_SIZE_BODY, DARK);
        float valueWidth = width(value, font, FONT_SIZE_BODY);
        drawText(canvas, value, MARGIN + CONTENT_WIDTH - 10 - valueWidth, canvas.y,
                font, FONT_SIZE_BODY, DARK);
        drawLine(canvas, MARGIN, canvas.y - 4, MARGIN + CONTENT_WIDTH, canvas.y - 4,
                LIGHT_LINE, 0.5f);
        canvas.y -= ROW_HEIGHT;
    }

    private static void drawSignatures(Canvas canvas, OfferData data) throws IOException {
        canvas.ensure(110);
        canvas.y -= 30;
        float col2X = MARGIN + CONTENT_WIDTH / 2 + 20;
        float lineWidth = 180;

        drawText(canvas, "For " + data.companyName(), MARGIN, canvas.y,
                FONT_REGULAR, FONT_SIZE_BODY, DARK);
        drawText(canvas, "Accepted by candidate", col2X, canvas.y,
                FONT_REGULAR, FONT_SIZE_BODY, DARK);
        canvas.y -= 44;
        drawLine(canvas, MARGIN, canvas.y, MARGIN + lineWidth, canvas.y, DARK, 0.8f);
        drawLine(canvas, col2X, canvas.y, col2X + lineWidth, canvas.y, DARK, 0.8f);
        canvas.y -= 14;
        drawText(canvas, "Authorised Signatory", MARGIN, canvas.y,
                FONT_REGULAR, FONT_SIZE_SMALL, MUTED);
        drawText(canvas, "Name & Date", col2X, canvas.y,
                FONT_REGULAR, FONT_SIZE_SMALL, MUTED);
    }

    private static void drawFooter(Canvas canvas, OfferData data) throws IOException {
        canvas.ensure(ROW_HEIGHT + 14);
        canvas.y -= 10;
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

    /** Greedy word wrap honouring explicit line breaks; drops empty lines. */
    private static java.util.List<String> wrap(String text, PDFont font, float size,
                                               float maxWidth) throws IOException {
        java.util.List<String> lines = new java.util.ArrayList<>();
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
