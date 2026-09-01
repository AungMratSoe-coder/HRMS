package com.ams.hrms.ui.main;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.AWTEventListener;
import java.time.Duration;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.ams.hrms.component.ModernButton;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;

import net.miginfocom.swing.MigLayout;

/**
 * Session idle auto-lock (security hardening): watches global AWT input
 * activity and, after {@link #IDLE_TIMEOUT} without any mouse/keyboard
 * event, shows a modal lock screen that requires re-entering the account
 * password (or signing out). The session itself is kept alive - unlocking
 * simply proves the same person is still at the keyboard.
 */
public final class IdleLockManager {

    /** Idle duration before the lock screen appears. */
    public static final Duration IDLE_TIMEOUT = Duration.ofMinutes(15);

    private static final int CHECK_INTERVAL_MS = 30_000;

    private final Window owner;
    private final Runnable onSignOut;
    private volatile long lastActivity = System.currentTimeMillis();
    private volatile boolean locked;

    private final Timer checkTimer = new Timer(CHECK_INTERVAL_MS, event -> checkIdle());
    private final AWTEventListener activityListener = event ->
            lastActivity = System.currentTimeMillis();

    public IdleLockManager(Window owner, Runnable onSignOut) {
        this.owner = owner;
        this.onSignOut = onSignOut;
    }

    /** Starts watching; call once the frame is visible. */
    public void install() {
        Toolkit.getDefaultToolkit().addAWTEventListener(activityListener,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK
                        | AWTEvent.KEY_EVENT_MASK);
        checkTimer.start();
    }

    /** Stops watching and removes the global listener. */
    public void uninstall() {
        checkTimer.stop();
        Toolkit.getDefaultToolkit().removeAWTEventListener(activityListener);
    }

    /** Manual lock (e.g. a future "Lock now" shortcut) resets nothing. */
    public void lockNow() {
        lastActivity = 0;
        checkIdle();
    }

    private void checkIdle() {
        if (locked || !EventQueue.isDispatchThread()) {
            return;
        }
        boolean timedOut =
                System.currentTimeMillis() - lastActivity >= IDLE_TIMEOUT.toMillis();
        if (!timedOut) {
            return;
        }
        locked = true;
        try {
            new LockDialog(owner, onSignOut).setVisible(true);
        } finally {
            lastActivity = System.currentTimeMillis();
            locked = false;
        }
    }

    /** Modal password prompt; cannot be dismissed without unlocking or signing out. */
    private static final class LockDialog extends JDialog {

        private final JPasswordField passwordField = new JPasswordField(18);
        private final JLabel errorLabel = new JLabel(" ");

        LockDialog(Window owner, Runnable onSignOut) {
            super(owner, "Locked", ModalityType.APPLICATION_MODAL);
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            setResizable(false);

            JPanel content = new JPanel(new MigLayout(
                    "wrap 1, insets 24 28 20 28, gapy 8",
                    "[grow,fill]", ""));
            content.setBackground(Palette.color(Role.CARD_BG));
            content.setBorder(BorderFactory.createLineBorder(
                    Palette.color(Role.CARD_BORDER), 1));

            JLabel title = new JLabel(sessionTitle());
            title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 15f));
            title.setForeground(Palette.color(Role.TEXT));
            errorLabel.setForeground(Palette.color(Role.DANGER));

            JButton unlockButton = new ModernButton("Unlock", ModernButton.Variant.PRIMARY);
            unlockButton.addActionListener(this::attemptUnlock);
            getRootPane().setDefaultButton(unlockButton);

            JButton signOutButton = new ModernButton("Sign Out",
                    ModernButton.Variant.OUTLINE);
            signOutButton.addActionListener(event -> {
                onSignOut.run();
                dispose();
            });

            content.add(title);
            content.add(new JLabel("Session locked after inactivity."
                    + " Enter your password to continue."));
            content.add(passwordField, "height 30!");
            content.add(errorLabel);
            content.add(unlockButton, "split 2, width 120!, height 34!");
            content.add(signOutButton, "width 120!, height 34!");
            setContentPane(content);
            pack();
            setLocationRelativeTo(owner);
        }

        private static String sessionTitle() {
            var user = com.ams.hrms.security.SessionContext.currentUser();
            return "Locked - " + user.fullName();
        }

        private void attemptUnlock(ActionEvent event) {
            String password = new String(passwordField.getPassword());
            passwordField.setText("");
            boolean ok = ServiceRegistry.authService().verifyPassword(password);
            if (ok) {
                dispose();
                return;
            }
            errorLabel.setText("Incorrect password. Try again.");
            pack();
        }
    }
}
