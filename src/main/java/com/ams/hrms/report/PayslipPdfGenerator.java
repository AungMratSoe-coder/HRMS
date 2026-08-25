package com.ams.hrms.report;

import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Renders a professional A4 payslip PDF (spec section 21) using PDFBox.
 * Layout: company header, employee info block, earnings table, deductions
 * table, net-pay highlight and a footer.
 */
public final class PayslipPdfGenerator {

    private static final PDRectangle PAGE = PDRectangle.A4;
    private static final float MARGIN = 50;
    private static final float CONTENT_WIDTH = PAGE.getWidth() - 2 * MARGIN;

    // Fonts
    private static final org.apache.pdfbox.pdmodel.font.PDFont FONT_BOLD =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private static final org.apache.pdfbox.pdmodel.font.PDFont FONT_REGULAR =
            new PDType1Font(Standard14Fonts.FontName.HELVETICA);

    // Colors
    private static final Color ACCENT = new Color(37, 99, 235);
    private static final Color DARK = new Color(15, 23, 42);
    private static final Color MUTED = new Color(100, 116, 139);
    private static final Color LIGHT_LINE = new Color(226, 232, 240);
    private static final Color NET_BG = new Color(22, 163, 74);

    // Layout constants
    private static final float ROW_HEIGHT = 18;
    private static final float FONT_SIZE_BODY = 10;
    private static final float FONT_SIZE_SMALL = 8;

    private PayslipPdfGenerator() {
    }

    /** Data holder for one payslip. */
    public record PayslipData(
            String companyName, String companyAddress,
            String employeeCode, String employeeName, String department,
            String position, String periodName,
            BigDecimal basicSalary, BigDecimal allowances, BigDecimal bonuses,
            BigDecimal overtime, BigDecimal grossSalary,
            BigDecimal tax, BigDecimal socialSecurity, BigDecimal otherDeductions,
            BigDecimal totalDeductions, BigDecimal netSalary, String currency) {
    }

    /**
     * Generates the payslip PDF and writes it to {@code outputPath}.
     *
     * @return the output path (same as parameter, for chaining)
     */
    public static Path generate(PayslipData data, Path outputPath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PAGE);
            document.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = PAGE.getHeight() - MARGIN;

                // ---- Company header ------------------------------------------
                y = drawText(cs, data.companyName(), MARGIN, y, FONT_BOLD, 16, ACCENT);
                y = drawText(cs, data.companyAddress(), MARGIN, y - 4, FONT_REGULAR,
                        FONT_SIZE_SMALL, MUTED);
                y = drawText(cs, "Payslip  ·  " + data.periodName(), MARGIN, y - 8,
                        FONT_BOLD, 12, DARK);

                y -= 10;
                drawLine(cs, MARGIN, y, MARGIN + CONTENT_WIDTH, y, ACCENT, 1.5f);
                y -= 20;

                // ---- Employee info (two columns) -----------------------------
                y = infoRow(cs, y, "Employee Code", data.employeeCode(),
                        "Employee Name", data.employeeName());
                y = infoRow(cs, y, "Department", data.department(),
                        "Position", data.position());
                y -= 10;

                // ---- Earnings table -------------------------------------------
                y = drawSectionHeader(cs, y, "Earnings");
                y = tableHeader(cs, y, "Description", "Amount (" + data.currency() + ")");
                y = tableRow(cs, y, "Basic Salary", money(data.basicSalary()));
                y = tableRow(cs, y, "Allowances", money(data.allowances()));
                y = tableRow(cs, y, "Bonuses", money(data.bonuses()));
                y = tableRow(cs, y, "Overtime", money(data.overtime()));
                y = tableRow(cs, y, "Gross Salary", money(data.grossSalary()), true);
                y -= 14;

                // ---- Deductions table -----------------------------------------
                y = drawSectionHeader(cs, y, "Deductions");
                y = tableHeader(cs, y, "Description", "Amount (" + data.currency() + ")");
                y = tableRow(cs, y, "Income Tax", money(data.tax()));
                y = tableRow(cs, y, "Social Security", money(data.socialSecurity()));
                y = tableRow(cs, y, "Other Deductions", money(data.otherDeductions()));
                y = tableRow(cs, y, "Total Deductions", money(data.totalDeductions()), true);
                y -= 14;

                // ---- Net pay highlight ----------------------------------------
                y = drawNetPay(cs, y, data);
                y -= 20;

                // ---- Footer -----------------------------------------------------
                drawText(cs, "This is a system-generated payslip. No signature required.",
                        MARGIN, y, FONT_REGULAR, FONT_SIZE_SMALL, MUTED);
            }

            Path parent = outputPath.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            document.save(outputPath.toFile());
        }
        return outputPath;
    }

    // ------------------------------------------------------------------
    // Drawing helpers
    // ------------------------------------------------------------------

    private static float drawText(PDPageContentStream cs, String text, float x, float y,
                                  org.apache.pdfbox.pdmodel.font.PDFont font,
                                  float size, Color color) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.setNonStrokingColor(color);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        return y;
    }

    private static float infoRow(PDPageContentStream cs, float y,
                                 String label1, String value1,
                                 String label2, String value2) throws IOException {
        float col2X = MARGIN + CONTENT_WIDTH / 2;
        drawText(cs, label1, MARGIN, y, FONT_REGULAR, FONT_SIZE_SMALL, MUTED);
        drawText(cs, value1, MARGIN + 100, y, FONT_REGULAR, FONT_SIZE_BODY, DARK);
        drawText(cs, label2, col2X, y, FONT_REGULAR, FONT_SIZE_SMALL, MUTED);
        drawText(cs, value2, col2X + 100, y, FONT_REGULAR, FONT_SIZE_BODY, DARK);
        return y - ROW_HEIGHT;
    }

    private static float drawSectionHeader(PDPageContentStream cs, float y, String title)
            throws IOException {
        drawText(cs, title.toUpperCase(), MARGIN, y, FONT_BOLD, 11, ACCENT);
        y -= 4;
        drawLine(cs, MARGIN, y, MARGIN + CONTENT_WIDTH, y, LIGHT_LINE, 0.8f);
        return y - ROW_HEIGHT + 4;
    }

    private static float tableHeader(PDPageContentStream cs, float y,
                                     String left, String right) throws IOException {
        float rightX = MARGIN + CONTENT_WIDTH - 100;
        drawText(cs, left, MARGIN, y, FONT_BOLD, FONT_SIZE_SMALL, MUTED);
        drawText(cs, right, rightX, y, FONT_BOLD, FONT_SIZE_SMALL, MUTED);
        y -= 4;
        drawLine(cs, MARGIN, y, MARGIN + CONTENT_WIDTH, y, LIGHT_LINE, 0.8f);
        return y - ROW_HEIGHT;
    }

    private static float tableRow(PDPageContentStream cs, float y, String label,
                                  String value) throws IOException {
        return tableRow(cs, y, label, value, false);
    }

    private static float tableRow(PDPageContentStream cs, float y, String label,
                                  String value, boolean bold) throws IOException {
        var font = bold ? FONT_BOLD : FONT_REGULAR;
        drawText(cs, label, MARGIN + 10, y, font, FONT_SIZE_BODY, DARK);
        float rightX = MARGIN + CONTENT_WIDTH - 100;
        drawText(cs, value, rightX, y, font, FONT_SIZE_BODY, DARK);
        drawLine(cs, MARGIN, y - 4, MARGIN + CONTENT_WIDTH, y - 4, LIGHT_LINE, 0.5f);
        return y - ROW_HEIGHT;
    }

    private static float drawNetPay(PDPageContentStream cs, float y,
                                    PayslipData data) throws IOException {
        float boxHeight = 32;
        float boxY = y - boxHeight;

        // Green background
        cs.setNonStrokingColor(NET_BG);
        cs.addRect(MARGIN, boxY, CONTENT_WIDTH, boxHeight);
        cs.fill();

        // Text
        cs.beginText();
        cs.setFont(FONT_BOLD, 13);
        cs.setNonStrokingColor(Color.WHITE);
        cs.newLineAtOffset(MARGIN + 14, boxY + 10);
        cs.showText("NET PAY");
        cs.endText();

        String amount = money(data.netSalary()) + " " + data.currency();
        cs.beginText();
        cs.setFont(FONT_BOLD, 13);
        cs.setNonStrokingColor(Color.WHITE);
        cs.newLineAtOffset(MARGIN + CONTENT_WIDTH - 160, boxY + 10);
        cs.showText(amount);
        cs.endText();

        return boxY;
    }

    private static void drawLine(PDPageContentStream cs, float x1, float y1,
                                 float x2, float y2, Color color, float width)
            throws IOException {
        cs.setStrokingColor(color);
        cs.setLineWidth(width);
        cs.moveTo(x1, y1);
        cs.lineTo(x2, y2);
        cs.stroke();
    }

    private static String money(BigDecimal value) {
        return value == null ? "0.00"
                : String.format("%,.2f", value);
    }
}
