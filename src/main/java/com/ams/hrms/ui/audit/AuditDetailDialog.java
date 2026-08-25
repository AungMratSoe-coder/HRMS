package com.ams.hrms.ui.audit;

import java.awt.BorderLayout;
import java.awt.Font;
import java.time.format.DateTimeFormatter;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import com.ams.hrms.repository.AuditRepository.AuditRow;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;

import net.miginfocom.swing.MigLayout;

/**
 * Read-only detail of one audit trail entry (spec section 28): every recorded
 * field including the device information omitted from the table columns.
 * The trail is append-only; there is nothing to edit here by design.
 */
public class AuditDetailDialog extends JDialog {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm:ss");

    public AuditDetailDialog(java.awt.Window owner, AuditRow entry) {
        super(owner, "Audit Entry #" + entry.id(), ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());
        setSize(560, 440);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel content = new JPanel(new MigLayout(
                "wrap 2, insets 20 24, gap 10",
                "[130!][grow,fill]"));
        content.setOpaque(false);

        addRow(content, "When", entry.createdAt() == null
                ? "-" : TIMESTAMP_FORMAT.format(entry.createdAt()));
        addRow(content, "User", entry.username());
        addRow(content, "Action", entry.action());
        addRow(content, "Module", entry.module());
        addRow(content, "Entity", entry.entity() == null ? "-" : entry.entity());
        addRow(content, "Entity ID", entry.entityId() == null ? "-" : String.valueOf(entry.entityId()));
        addRow(content, "IP address", entry.ipAddress() == null ? "-" : entry.ipAddress());
        addRow(content, "Device", entry.deviceInfo() == null ? "-" : entry.deviceInfo());

        JLabel descriptionTitle = sectionLabel("Description");
        content.add(descriptionTitle, "span 2, gaptop 6");

        JLabel description = new JLabel(
                "<html><body style='width:440px'>"
                        + escape(entry.description() == null ? "-" : entry.description())
                        + "</body></html>");
        description.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        content.add(description, "span 2, growx");

        add(content, BorderLayout.CENTER);
    }

    private void addRow(JPanel content, String label, String value) {
        JLabel labelLabel = new JLabel(label);
        labelLabel.setFont(labelLabel.getFont().deriveFont(Font.PLAIN, 12f));
        labelLabel.setForeground(Palette.color(Role.TEXT_MUTED));

        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.PLAIN, 13f));
        valueLabel.setForeground(Palette.color(Role.TEXT));

        content.add(labelLabel);
        content.add(valueLabel);
    }

    private JLabel sectionLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        return label;
    }

    private static String escape(String raw) {
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
