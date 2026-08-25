package com.ams.hrms.ui.recruitment;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.ams.hrms.component.ModernButton;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;

import net.miginfocom.swing.MigLayout;

/**
 * Small modal prompt asking for a rejection reason; null means the user
 * cancelled, a blank string is refused.
 */
public final class ReasonDialog extends JDialog {

    private final JTextArea reasonArea = new JTextArea(4, 30);
    private String reason;
    private boolean submitted;

    private ReasonDialog(java.awt.Window owner, String title, String prompt) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setLayout(new BorderLayout());

        JPanel content = new JPanel(new MigLayout(
                "wrap 1, insets 24 28 12 28, gap 8", "[grow,fill]"));
        JLabel promptLabel = new JLabel(prompt);
        promptLabel.setForeground(Palette.color(Role.TEXT));
        JScrollPane scroll = new JScrollPane(reasonArea);
        scroll.setPreferredSize(new java.awt.Dimension(360, 100));

        JButton okButton = new ModernButton("Confirm", "check");
        okButton.addActionListener(event -> confirm());
        JButton cancelButton = new ModernButton("Cancel", ModernButton.Variant.OUTLINE);
        cancelButton.addActionListener(event -> dispose());

        JPanel buttons = new JPanel(new MigLayout(
                "insets 14 28, gap 10, alignx right", "push[][]"));
        buttons.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                Palette.color(Role.CARD_BORDER)));
        buttons.add(cancelButton);
        buttons.add(okButton);

        content.add(promptLabel);
        content.add(scroll);
        add(content, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(okButton);
        setSize(440, 260);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    }

    private void confirm() {
        String trimmed = reasonArea.getText().trim();
        if (trimmed.isEmpty()) {
            return;
        }
        reason = trimmed;
        submitted = true;
        dispose();
    }

    /** Shows the dialog; returns the reason or null when cancelled. */
    public static String show(java.awt.Window owner, String title, String prompt) {
        ReasonDialog dialog = new ReasonDialog(owner, title, prompt);
        dialog.setVisible(true);
        return dialog.submitted ? dialog.reason : null;
    }
}
