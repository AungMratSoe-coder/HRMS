package com.ams.hrms.ui.login;

import java.awt.Color;
import java.awt.Font;
import java.awt.event.ActionEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.component.ModernButton;
import com.ams.hrms.config.AppConfig;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.exception.ErrorHandler;
import com.ams.hrms.exception.HrmsException;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.service.AuthService;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.IconLoader;
import com.ams.hrms.util.UiThread;

import com.formdev.flatlaf.FlatClientProperties;

import net.miginfocom.swing.MigLayout;

/**
 * Credential form (spec section 7): email, password with visibility
 * toggle, remember-email persistence, inline error banner, and a busy
 * state on the submit button. Authentication runs off the EDT via
 * {@link UiThread}; errors land in the inline banner instead of dialogs.
 */
public class LoginPanel extends JPanel {

    private static final Logger LOG = LoggerFactory.getLogger(LoginPanel.class);

    private static final String PREF_REMEMBER = "login.rememberEmail";
    private static final String PREF_EMAIL = "login.email";

    private final transient AuthService authService = ServiceRegistry.authService();
    private final transient Runnable loginSuccessHandler;

    private final JTextField emailField = new JTextField(18);
    private final JPasswordField passwordField = new JPasswordField(18);
    private final JCheckBox rememberBox = new JCheckBox("Remember email");
    private final ModernButton loginButton = new ModernButton("Sign In");
    private final JLabel errorBanner = new JLabel();

    public LoginPanel(Runnable loginSuccessHandler) {
        this.loginSuccessHandler = loginSuccessHandler;

        setLayout(new MigLayout(
                "wrap 1, insets 56 64, gap 14",
                "[380!,fill]",
                "[][][][][][][][]push[]"));

        JLabel titleLabel = new JLabel("Welcome back");
        titleLabel.setName("welcomeTitle");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 24f));
        titleLabel.setForeground(Palette.color(Role.TEXT));

        JLabel subtitleLabel = new JLabel("Sign in to your HR workspace.");
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 13f));
        subtitleLabel.setForeground(Palette.color(Role.TEXT_MUTED));

        emailField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Email");

        passwordField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Password");

        errorBanner.setIcon(IconLoader.small("warning"));
        errorBanner.setForeground(Palette.color(Role.DANGER));
        errorBanner.setVerticalTextPosition(SwingConstants.TOP);
        errorBanner.setVisible(false);

        loginButton.addActionListener(this::submitLogin);
        stylePrimaryButton(loginButton);

        restoreRememberedEmail();
        clearErrorOnTyping();

        add(titleLabel);
        add(subtitleLabel, "gapbottom 14");
        add(emailField, "height 40!");
        add(passwordField, "height 40!");
        add(rememberBox, "gap 2");
        add(errorBanner);
        add(loginButton, "height 42!, gaptop 6");
        add(buildHintLabel(), "gaptop 18");
    }

    /** The button bound as the frame's default (Enter) button. */
    public JButton defaultButton() {
        return loginButton;
    }

    /** Re-resolves palette colors after a theme switch. */
    @Override
    public void updateUI() {
        super.updateUI();
        // Fields may not exist yet when the LAF installs during construction.
        if (errorBanner != null) {
            errorBanner.setForeground(Palette.color(Role.DANGER));
        }
        for (java.awt.Component component : getComponents()) {
            if (component instanceof JLabel label && "welcomeTitle".equals(label.getName())) {
                label.setForeground(Palette.color(Role.TEXT));
            }
        }
    }

    // ------------------------------------------------------------------
    // Login flow
    // ------------------------------------------------------------------

    private void submitLogin(ActionEvent event) {
        clearError();
        String email = emailField.getText().trim();
        char[] passwordChars = passwordField.getPassword();
        String password = new String(passwordChars);
        java.util.Arrays.fill(passwordChars, '\0');

        if (email.isEmpty()) {
            showError("Please enter your email.");
            return;
        }
        if (password.isEmpty()) {
            showError("Please enter your password.");
            return;
        }
        persistRememberedEmail(email);

        loginButton.setEnabled(false);
        loginButton.setText("Signing In...");

        UiThread.executeAsync(
                "Sign in",
                () -> authService.login(email, password),
                user -> {
                    resetButtonState();
                    LOG.info("User '{}' signed in with {} permission(s)",
                            user.email(),
                            SessionContext.permissions().size());
                    loginSuccessHandler.run();
                },
                error -> {
                    resetButtonState();
                    if (error instanceof HrmsException hrmsError) {
                        showError(hrmsError.getUserMessage());
                    } else {
                        ErrorHandler.handle(error);
                    }
                });
    }

    private void resetButtonState() {
        loginButton.setEnabled(true);
        loginButton.setText("Sign In");
    }

    // ------------------------------------------------------------------
    // UI wiring
    // ------------------------------------------------------------------

    private void stylePrimaryButton(JButton button) {
        button.setBackground(Palette.color(Role.ACCENT));
        button.setForeground(Palette.readableForeground(button.getBackground()));
        button.setFocusPainted(false);
    }

    private JLabel buildHintLabel() {
        String hint = AppConfig.get().get("app.login.hint", "");
        JLabel hintLabel = new JLabel(hint.isBlank() ? " " : hint, SwingConstants.CENTER);
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 11f));
        hintLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        return hintLabel;
    }

    // ------------------------------------------------------------------
    // Remember email (java.util.prefs)
    // ------------------------------------------------------------------

    private void restoreRememberedEmail() {
        java.util.prefs.Preferences prefs =
                java.util.prefs.Preferences.userNodeForPackage(LoginPanel.class);
        boolean remembered = prefs.getBoolean(PREF_REMEMBER, false);
        String savedEmail = prefs.get(PREF_EMAIL, "");
        if (remembered && !savedEmail.isBlank()) {
            emailField.setText(savedEmail);
            rememberBox.setSelected(true);
            passwordField.requestFocusInWindow();
        } else {
            emailField.requestFocusInWindow();
        }
    }

    private void persistRememberedEmail(String email) {
        java.util.prefs.Preferences prefs =
                java.util.prefs.Preferences.userNodeForPackage(LoginPanel.class);
        prefs.putBoolean(PREF_REMEMBER, rememberBox.isSelected());
        if (rememberBox.isSelected()) {
            prefs.put(PREF_EMAIL, email);
        } else {
            prefs.remove(PREF_EMAIL);
        }
    }

    // ------------------------------------------------------------------
    // Error banner
    // ------------------------------------------------------------------

    private void showError(String message) {
        errorBanner.setText(message);
        errorBanner.setVisible(true);
        revalidate();
        repaint();
    }

    private void clearError() {
        errorBanner.setVisible(false);
    }

    private void clearErrorOnTyping() {
        javax.swing.event.DocumentListener clearer = new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                clearError();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                clearError();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                clearError();
            }
        };
        emailField.getDocument().addDocumentListener(clearer);
        passwordField.getDocument().addDocumentListener(clearer);
    }
}
