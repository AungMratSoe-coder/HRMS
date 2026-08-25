package com.ams.hrms.component;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.UiGraphics;

/**
 * Rounded status pill used in tables and detail views (spec sections 36 and
 * 37). Status text maps automatically to a semantic color: green for positive
 * states, amber for pending, red for negative, blue for informational.
 */
public class StatusBadge extends JComponent {

    private static final int HEIGHT = 22;
    private static final Map<String, Role> ROLE_BY_STATUS = buildRoleMap();

    private String status = "";

    public StatusBadge() {
        Font base = javax.swing.UIManager.getFont("defaultFont");
        if (base == null) {
            base = new javax.swing.JLabel().getFont();
        }
        setFont(base.deriveFont(Font.BOLD, 11f));
        setOpaque(false);
    }

    public void setStatus(String newStatus) {
        this.status = newStatus == null ? "" : newStatus.trim();
        setToolTipText(this.status);
        repaint();
    }

    @Override
    public Dimension getPreferredSize() {
        Font font = getFont();
        java.awt.FontMetrics metrics = getFontMetrics(font);
        int width = metrics.stringWidth(displayText()) + 20;
        return new Dimension(Math.max(width, 52), HEIGHT);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Role role = roleFor(status);
        Color semantic = Palette.color(role);

        Graphics2D g = (Graphics2D) graphics.create();
        UiGraphics.fillRoundRect(g, 0, (getHeight() - HEIGHT) / 2, getWidth(), HEIGHT, 11,
                UiGraphics.blend(Palette.color(Role.CARD_BG), semantic, 0.14));
        g.dispose();

        g = (Graphics2D) graphics.create();
        UiGraphics.enableAntialiasing(g);
        g.setColor(semantic);
        g.setFont(getFont());
        java.awt.FontMetrics metrics = g.getFontMetrics();
        String text = displayText();
        int textX = (getWidth() - metrics.stringWidth(text)) / 2;
        int textY = (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2 - 1;
        g.drawString(text, textX, textY);
        g.dispose();
    }

    private String displayText() {
        return status.replace('_', ' ').toUpperCase();
    }

    /**
     * Returns the table cell renderer that renders status values as badges.
     * Apply with {@code HrmsTable.Builder.badgeColumn(...)}.
     */
    public static TableCellRenderer cellRenderer() {
        return new BadgeCellRenderer();
    }

    /** Semantic color role for a status value. */
    public static Role roleFor(String statusValue) {
        if (statusValue == null || statusValue.isBlank()) {
            return Role.TEXT_MUTED;
        }
        return ROLE_BY_STATUS.getOrDefault(statusValue.toUpperCase(), Role.TEXT_MUTED);
    }

    private static Map<String, Role> buildRoleMap() {
        return Map.ofEntries(
                // Positive
                Map.entry("ACTIVE", Role.SUCCESS), Map.entry("APPROVED", Role.SUCCESS),
                Map.entry("PAID", Role.SUCCESS), Map.entry("PRESENT", Role.SUCCESS),
                Map.entry("COMPLETED", Role.SUCCESS), Map.entry("PASSED", Role.SUCCESS),
                Map.entry("ACCEPTED", Role.SUCCESS), Map.entry("HIRED", Role.SUCCESS),
                Map.entry("RETURNED", Role.SUCCESS), Map.entry("OPEN", Role.SUCCESS),
                Map.entry("AVAILABLE", Role.SUCCESS), Map.entry("ASSIGNED", Role.SUCCESS),
                Map.entry("PASS", Role.SUCCESS), Map.entry("SENT", Role.INFO),
                // Pending / warn
                Map.entry("PENDING", Role.WARNING), Map.entry("SUBMITTED", Role.WARNING),
                Map.entry("PROCESSING", Role.WARNING), Map.entry("CALCULATED", Role.WARNING),
                Map.entry("REVIEWED", Role.INFO), Map.entry("IN_PROGRESS", Role.INFO),
                Map.entry("ON_HOLD", Role.WARNING), Map.entry("OVERDUE", Role.DANGER),
                // Negative
                Map.entry("REJECTED", Role.DANGER), Map.entry("TERMINATED", Role.DANGER),
                Map.entry("FAILED", Role.DANGER), Map.entry("CANCELLED", Role.DANGER),
                Map.entry("EXPIRED", Role.DANGER), Map.entry("LOST", Role.DANGER),
                Map.entry("ABSENT", Role.DANGER), Map.entry("DECLINED", Role.DANGER),
                // Informational
                Map.entry("DRAFT", Role.INFO), Map.entry("NEW", Role.INFO),
                Map.entry("ENROLLED", Role.INFO), Map.entry("PLANNED", Role.INFO),
                Map.entry("SCHEDULED", Role.INFO), Map.entry("LATE", Role.WARNING),
                Map.entry("LEAVE", Role.INFO), Map.entry("HOLIDAY", Role.INFO),
                Map.entry("WEEKEND", Role.TEXT_MUTED), Map.entry("HALF_DAY", Role.WARNING),
                Map.entry("EARLY_LEAVE", Role.WARNING), Map.entry("MISSION", Role.INFO),
                Map.entry("RESIGNED", Role.WARNING), Map.entry("RETIRED", Role.INFO),
                Map.entry("UNDER_REPAIR", Role.WARNING), Map.entry("ATTENDED", Role.INFO),
                // Notification types (spec section 41)
                Map.entry("INFO", Role.INFO), Map.entry("WARNING", Role.WARNING),
                Map.entry("SUCCESS", Role.SUCCESS), Map.entry("ERROR", Role.DANGER),
                Map.entry("PAYROLL", Role.INFO), Map.entry("DOCUMENT", Role.WARNING),
                Map.entry("TRAINING", Role.INFO), Map.entry("SYSTEM", Role.TEXT_MUTED),
                // Audit trail actions (spec section 28)
                Map.entry("CREATE", Role.SUCCESS), Map.entry("ASSIGN", Role.INFO),
                Map.entry("UPDATE", Role.INFO), Map.entry("STATUS_CHANGE", Role.WARNING),
                Map.entry("DELETE", Role.DANGER), Map.entry("LOGIN", Role.SUCCESS),
                Map.entry("LOGIN_FAILED", Role.DANGER), Map.entry("LOGOUT", Role.TEXT_MUTED),
                Map.entry("APPROVE", Role.SUCCESS), Map.entry("REJECT", Role.DANGER),
                Map.entry("REQUEST", Role.INFO), Map.entry("CANCEL", Role.DANGER),
                Map.entry("CHECK_IN", Role.SUCCESS), Map.entry("CHECK_OUT", Role.INFO),
                Map.entry("CORRECTION", Role.WARNING), Map.entry("CALCULATE", Role.INFO),
                Map.entry("RETURN", Role.INFO),
                Map.entry("ARCHIVE", Role.WARNING));
    }

    /** TableCellRenderer implementation backed by StatusBadge painting. */
    private static final class BadgeCellRenderer extends StatusBadge implements TableCellRenderer {

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            setStatus(value == null ? "" : String.valueOf(value));
            return this;
        }
    }
}
