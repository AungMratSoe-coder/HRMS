package com.ams.hrms.component;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Window;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;

import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.exception.ErrorHandler;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.UiThread;

import net.miginfocom.swing.MigLayout;

/**
 * Modal change-password dialog. Standard mode verifies the current password;
 * forced mode (after an administrator reset) skips it and cannot be
 * dismissed until the password is changed.
 */
public class ChangePasswordDialog extends JDialog {

    private final JPasswordField currentField = new JPasswordField(18);
    private final JPasswordField newField = new JPasswordField(18);
    private final JPasswordField confirmField = new JPasswordField(18);
    private final JLabel errorLabel = new JLabel();
    private final boolean forcedMode;
    private boolean succeeded;

    private ChangePasswordDialog(Window owner, boolean forcedMode) {
        super(owner, forcedMode ? "Set a New Password" : "Change Password",
                ModalityType.APPLICATION_MODAL);
        this.forcedMode = forcedMode;

        JPanel form = new JPanel(new MigLayout("wrap 2, gap 8", "[right][grow,fill]"));
        form.setOpaque(false);

        if (!forcedMode) {
            form.add(new JLabel("Current password:"));
            form.add(currentField);
        }
        form.add(new JLabel("New password:"));
        form.add(newField);
        form.add(new JLabel("Confirm new password:"));
        form.add(confirmField);

        JLabel policy = new JLabel("<html><div style='width:260px'>At least 8 characters "
                + "with an uppercase letter, a lowercase letter and a digit.</div></html>");
        policy.setFont(policy.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
        policy.setForeground(Palette.color(Role.TEXT_MUTED));
        form.add(new JLabel());
        form.add(policy);

        errorLabel.setForeground(Palette.color(Role.DANGER));
        errorLabel.setVisible(false);

        JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8, right"));
        buttons.setOpaque(false);
        ModernButton okButton = new ModernButton(
                forcedMode ? "Set Password" : "Change Password", ModernButton.Variant.PRIMARY);
        ModernButton cancelButton = new ModernButton("Cancel", ModernButton.Variant.OUTLINE);
        buttons.add(cancelButton);
        buttons.add(okButton);

        JPanel content = new JPanel(new MigLayout("wrap 1, insets 18 22, gap 10"));
        content.setOpaque(false);
        content.add(form);
        content.add(errorLabel, "gapleft 130");
        content.add(buttons, "growx");
        add(content, BorderLayout.CENTER);

        if (forcedMode) {
            JLabel notice = new JLabel("Your password was reset by an administrator. "
                    + "Choose a new password to continue.");
            notice.setForeground(Palette.color(Role.WARNING));
            add(notice, BorderLayout.NORTH);
        }

        okButton.addActionListener(event -> submit());
        cancelButton.addActionListener(event -> cancel());
        getRootPane().setDefaultButton(okButton);
        pack();
        setLocationRelativeTo(owner);
    }

    /** Opens the dialog for the signed-in user; true when the change succeeded. */
    public static boolean show(Window owner, boolean forcedMode) {
        ChangePasswordDialog dialog = new ChangePasswordDialog(owner, forcedMode);
        dialog.setVisible(true);
        return dialog.succeeded;
    }

    private void submit() {
        errorLabel.setVisible(false);
        String current = new String(currentField.getPassword());
        String newPlain = new String(newField.getPassword());
        String confirm = new String(confirmField.getPassword());

        if (!newPlain.equals(confirm)) {
            showError("New password and confirmation do not match.");
            return;
        }

        okBusy(true);
        UiThread.executeAsync("Change password",
                () -> {
                    if (forcedMode) {
                        ServiceRegistry.authService().completeForcedPasswordChange(newPlain);
                    } else {
                        ServiceRegistry.authService().changePassword(current, newPlain);
                    }
                    return null;
                },
                result -> {
                    succeeded = true;
                    okBusy(false);
                    Toast.show(this, Toast.Type.SUCCESS,
                            forcedMode ? "Password set. Welcome!" : "Password changed.");
                    dispose();
                },
                error -> {
                    okBusy(false);
                    Exception exception = error instanceof Exception e ? e
                            : new IllegalStateException(error);
                    if (exception instanceof ValidationException validation) {
                        showError(String.join(" ", validation.getErrors()));
                    } else {
                        ErrorHandler.handle(this, exception);
                    }
                });
    }

    private void okBusy(boolean busy) {
        for (Component component : getContentPane().getComponents()) {
            component.setEnabled(!busy);
        }
    }

    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
        pack();
        setLocationRelativeTo(getOwner());
    }

    private void cancel() {
        if (forcedMode) {
            // Cannot skip a forced change; nudge instead of closing.
            Toast.show(this, Toast.Type.WARNING,
                    "You must set a new password before continuing.");
            return;
        }
        dispose();
    }
}
