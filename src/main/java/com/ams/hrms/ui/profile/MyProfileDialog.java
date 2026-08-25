package com.ams.hrms.ui.profile;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Image;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.Toast;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.exception.ErrorHandler;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.Employee;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.security.SessionContext.RoleRef;
import com.ams.hrms.ui.employee.EmployeeProfileDialog;
import com.ams.hrms.ui.theme.Palette;
import com.ams.hrms.ui.theme.Palette.Role;
import com.ams.hrms.util.UiThread;

import net.miginfocom.swing.MigLayout;

/**
 * Self-service account profile ("My Profile"): every signed-in user can
 * review their identity and roles, upload or remove their profile picture,
 * maintain their own contact details and change their password without any
 * directory permission. When the account is linked to an employee record,
 * the full employee profile opens from here as well.
 */
public class MyProfileDialog extends JDialog {

    private static final DateTimeFormatter SIGNED_IN_AT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(java.time.ZoneId.systemDefault());

    private static final int AVATAR_SIZE = 96;

    /** Unsaved picture edit staged by the user; applied when Save is pressed. */
    private enum AvatarAction { NONE, SET, REMOVE }

    private final JTextField usernameField = new JTextField(18);
    private final JTextField fullNameField = new JTextField(18);
    private final JTextField emailField = new JTextField(18);
    private final JTextField phoneField = new JTextField(18);
    private final JLabel errorLabel = new JLabel();
    private final JLabel linkHintLabel = new JLabel();
    private final ModernButton viewEmployeeButton =
            new ModernButton("View Employee Record", ModernButton.Variant.OUTLINE);
    private final ModernButton saveButton = new ModernButton("Save", ModernButton.Variant.PRIMARY);
    private final ModernButton changePictureButton =
            new ModernButton("Change Picture...", ModernButton.Variant.OUTLINE);
    private final ModernButton removePictureButton =
            new ModernButton("Remove Picture", ModernButton.Variant.OUTLINE);

    private final AvatarPreview avatarPreview = new AvatarPreview();

    /** Persisted picture state as last loaded from the database. */
    private byte[] savedAvatar;
    private AvatarAction pendingAvatarAction = AvatarAction.NONE;
    private byte[] pendingAvatarBytes;
    private Image pendingThumbnailImage;

    private Employee ownEmployee;

    private MyProfileDialog(java.awt.Window owner) {
        super(owner, "My Profile", ModalityType.APPLICATION_MODAL);

        JPanel content = new JPanel(new MigLayout("wrap 2, insets 20 24, gap 8",
                "[right][grow,fill]"));
        content.setOpaque(false);

        content.add(buildIdentityCard(), "span 2, growx, wrap");

        usernameField.setText(SessionContext.currentUser().username());
        usernameField.setEditable(false);
        fullNameField.setText(SessionContext.currentUser().fullName());
        fullNameField.setEditable(false);
        emailField.setText(orEmpty(SessionContext.currentUser().email()));
        phoneField.setText(orEmpty(SessionContext.currentUser().phone()));

        content.add(new JLabel("Username:"));
        content.add(usernameField);
        content.add(new JLabel("Full name:"));
        content.add(fullNameField);
        content.add(nameHint(), "skip");
        content.add(new JLabel("Email:"));
        content.add(emailField);
        content.add(new JLabel("Phone:"));
        content.add(phoneField);

        errorLabel.setForeground(Palette.color(Role.DANGER));
        errorLabel.setVisible(false);

        ModernButton changePasswordButton =
                new ModernButton("Change Password...", ModernButton.Variant.OUTLINE);
        ModernButton cancelButton = new ModernButton("Cancel", ModernButton.Variant.OUTLINE);

        JPanel buttons = new JPanel(new MigLayout("insets 6 0 0 0, gap 8"));
        buttons.setOpaque(false);
        buttons.add(viewEmployeeButton);
        buttons.add(changePasswordButton, "gapleft push");
        buttons.add(cancelButton);
        buttons.add(saveButton);

        linkHintLabel.setFont(linkHintLabel.getFont().deriveFont(java.awt.Font.PLAIN, 11f));
        linkHintLabel.setForeground(Palette.color(Role.TEXT_MUTED));
        linkHintLabel.setVisible(false);

        content.add(errorLabel, "span 2");
        content.add(linkHintLabel, "span 2");
        content.add(buttons, "span 2, growx");
        add(content, BorderLayout.CENTER);

        saveButton.addActionListener(event -> save());
        cancelButton.addActionListener(event -> dispose());
        changePasswordButton.addActionListener(event ->
                com.ams.hrms.component.ChangePasswordDialog.show(this, false));
        viewEmployeeButton.setEnabled(false);
        viewEmployeeButton.addActionListener(event -> openEmployeeRecord());
        changePictureButton.setToolTipText(
                "JPG, PNG, GIF or BMP up to "
                        + (com.ams.hrms.util.AvatarImages.MAX_SOURCE_BYTES / (1024 * 1024))
                        + " MB - stored as a small square thumbnail.");
        changePictureButton.addActionListener(event -> choosePicture());
        removePictureButton.addActionListener(event -> stageRemoval());
        refreshPictureButtons();

        loadOwnEmployee();
        loadSavedAvatar();

        getRootPane().setDefaultButton(saveButton);
        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    }

    /** Opens the dialog for the signed-in user. */
    public static void show(java.awt.Window owner) {
        new MyProfileDialog(owner).setVisible(true);
    }

    // ------------------------------------------------------------------
    // Sections
    // ------------------------------------------------------------------

    /** Picture preview + display name, roles, sign-in time and picture actions. */
    private JPanel buildIdentityCard() {
        JPanel card = new JPanel(new MigLayout("insets 14 16, gap 4 2, aligny center",
                "[]12[grow]"));
        card.setBackground(Palette.color(Role.CARD_BG));
        card.setBorder(BorderFactory.createLineBorder(Palette.color(Role.CARD_BORDER)));

        String roles = SessionContext.roles().stream()
                .map(RoleRef::name)
                .reduce((a, b) -> a + ", " + b)
                .orElse("No role");

        JLabel nameLabel = new JLabel(SessionContext.currentUser().fullName());
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 15f));
        nameLabel.setForeground(Palette.color(Role.TEXT));

        JLabel detailLabel = new JLabel(roles
                + "  |  signed in " + SIGNED_IN_AT.format(SessionContext.loggedInAt()));
        detailLabel.setFont(detailLabel.getFont().deriveFont(Font.PLAIN, 11f));
        detailLabel.setForeground(Palette.color(Role.TEXT_MUTED));

        JPanel pictureButtons = new JPanel(new MigLayout("insets 2 0 0 0, gap 8"));
        pictureButtons.setOpaque(false);
        pictureButtons.add(changePictureButton);
        pictureButtons.add(removePictureButton);

        card.add(avatarPreview, "spany 3, aligny center");
        card.add(nameLabel, "wrap");
        card.add(detailLabel, "wrap");
        card.add(pictureButtons);
        return card;
    }

    private JLabel nameHint() {
        JLabel hint = new JLabel("Managed by HR - contact your administrator.");
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
        hint.setForeground(Palette.color(Role.TEXT_MUTED));
        return hint;
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    /** Resolves the linked employee record off the EDT, if any. */
    private void loadOwnEmployee() {
        java.util.function.Supplier<Optional<Employee>> work =
                () -> ServiceRegistry.employeeService().findOwnEmployee();
        java.util.function.Consumer<Optional<Employee>> onSuccess = this::applyOwnEmployee;
        java.util.function.Consumer<Exception> onError = error -> ErrorHandler.handle(error);
        UiThread.executeAsync("Load own employee record", work, onSuccess, onError);
    }

    private void applyOwnEmployee(Optional<Employee> employee) {
        ownEmployee = employee.orElse(null);
        if (ownEmployee != null) {
            viewEmployeeButton.setToolTipText("Open your employee profile ("
                    + ownEmployee.getCode() + ")");
            viewEmployeeButton.setEnabled(true);
            linkHintLabel.setVisible(false);
        } else {
            viewEmployeeButton.setToolTipText(
                    "Your account is not linked to an employee record yet.");
            linkHintLabel.setText("<html>Your account is not linked to an employee record "
                    + "yet - an administrator can link it under "
                    + "Settings &gt; User Accounts &gt; Link Employee.</html>");
            linkHintLabel.setVisible(true);
            pack();
            setLocationRelativeTo(getOwner());
        }
    }

    private void openEmployeeRecord() {
        if (ownEmployee == null) {
            return;
        }
        new EmployeeProfileDialog(this, ownEmployee).setVisible(true);
    }

    // ------------------------------------------------------------------
    // Profile picture
    // ------------------------------------------------------------------

    /** Loads the persisted picture off the EDT and shows it as baseline. */
    private void loadSavedAvatar() {
        UiThread.executeAsync("Load my profile picture",
                () -> ServiceRegistry.userService().findOwnAvatar(),
                bytes -> {
                    savedAvatar = bytes;
                    avatarPreview.setSaved(bytes);
                    refreshPictureButtons();
                });
    }

    /** File chooser + local preview; nothing is stored until Save is pressed. */
    private void choosePicture() {
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                "Pictures (JPG, PNG, GIF, BMP)", "jpg", "jpeg", "png", "gif", "bmp"));
        if (chooser.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        java.nio.file.Path file = chooser.getSelectedFile().toPath();

        errorLabel.setVisible(false);
        okBusy(true);
        UiThread.executeAsync("Prepare profile picture",
                () -> {
                    try {
                        byte[] raw = java.nio.file.Files.readAllBytes(file);
                        byte[] thumbnail = com.ams.hrms.util.AvatarImages.squareThumbnail(raw);
                        Image image = javax.imageio.ImageIO
                                .read(new java.io.ByteArrayInputStream(thumbnail));
                        return new Object[] { raw, image };
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                },
                result -> {
                    okBusy(false);
                    pendingAvatarBytes = (byte[]) result[0];
                    pendingThumbnailImage = (Image) result[1];
                    pendingAvatarAction = AvatarAction.SET;
                    avatarPreview.setPending(pendingThumbnailImage);
                    refreshPictureButtons();
                },
                error -> {
                    okBusy(false);
                    Exception exception = error instanceof Exception e ? e
                            : new IllegalStateException(error);
                    if (exception instanceof ValidationException validation) {
                        showError(String.join(" ", validation.getErrors()));
                    } else if (exception instanceof java.io.UncheckedIOException io) {
                        showError("The picture could not be read: " + io.getMessage());
                    } else {
                        ErrorHandler.handle(this, exception);
                    }
                });
    }

    /** Stages removal of the picture; still reversible via Cancel/another pick. */
    private void stageRemoval() {
        pendingAvatarBytes = null;
        pendingThumbnailImage = null;
        pendingAvatarAction = AvatarAction.REMOVE;
        avatarPreview.setPending(null);
        refreshPictureButtons();
    }

    /** Persists the staged picture edit (called on the worker thread). */
    private void applyPendingAvatar() {
        switch (pendingAvatarAction) {
            case SET -> ServiceRegistry.userService().updateOwnAvatar(pendingAvatarBytes);
            case REMOVE -> ServiceRegistry.userService().removeOwnAvatar();
            case NONE -> { }
        }
        pendingAvatarAction = AvatarAction.NONE;
        pendingAvatarBytes = null;
        pendingThumbnailImage = null;
    }

    /** Remove is only meaningful when a picture exists or one is staged. */
    private void refreshPictureButtons() {
        boolean hasPicture = savedAvatar != null && savedAvatar.length > 0
                || pendingAvatarAction == AvatarAction.SET;
        removePictureButton.setEnabled(hasPicture);
    }

    private void save() {
        errorLabel.setVisible(false);
        okBusy(true);
        UiThread.executeAsync("Update my profile",
                () -> {
                    applyPendingAvatar();
                    ServiceRegistry.userService().updateOwnProfile(
                            emailField.getText(), phoneField.getText());
                    return null;
                },
                result -> {
                    okBusy(false);
                    Toast.show(this, Toast.Type.SUCCESS, "Profile updated.");
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

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private void okBusy(boolean busy) {
        for (Component component : getContentPane().getComponents()) {
            component.setEnabled(!busy);
        }
    }

    private void showError(String message) {
        String escaped = message.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;");
        errorLabel.setText("<html><div style='width:340px'>" + escaped + "</div></html>");
        errorLabel.setVisible(true);
        pack();
        setLocationRelativeTo(getOwner());
    }

    /** Circular picture preview; initials fallback mirrors the header chip. */
    private final class AvatarPreview extends javax.swing.JComponent {

        private Image savedImage;
        private Image pendingImage;

        AvatarPreview() {
            setPreferredSize(new java.awt.Dimension(AVATAR_SIZE, AVATAR_SIZE));
            setSaved(null);
        }

        void setSaved(byte[] jpegBytes) {
            this.savedImage = decode(jpegBytes);
            repaint();
        }

        /** Shows an unsaved choice; null falls back to saved/initials state. */
        void setPending(Image thumbnail) {
            this.pendingImage = thumbnail;
            repaint();
        }

        private Image decode(byte[] jpegBytes) {
            if (jpegBytes == null || jpegBytes.length == 0) {
                return null;
            }
            try {
                return javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(jpegBytes));
            } catch (java.io.IOException e) {
                return null;
            }
        }

        @Override
        protected void paintComponent(java.awt.Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            com.ams.hrms.util.UiGraphics.enableAntialiasing(g);
            int size = Math.min(getWidth(), getHeight()) - 2;
            int x = (getWidth() - size) / 2;
            int y = (getHeight() - size) / 2;

            Image picture = pendingImage != null ? pendingImage : savedImage;
            if (picture != null && picture.getWidth(this) > 0) {
                java.awt.Shape circle = new java.awt.geom.Ellipse2D.Float(x, y, size, size);
                g.setClip(circle);
                g.drawImage(picture, x, y, size, size, null);
                g.setClip(null);
            } else {
                g.setColor(Palette.color(Role.ACCENT));
                g.fillOval(x, y, size, size);
                String initials = initialsOf(SessionContext.currentUser().fullName());
                g.setFont(getFont().deriveFont(Font.BOLD, 28f));
                java.awt.FontMetrics metrics = g.getFontMetrics();
                g.setColor(Palette.readableForeground(Palette.color(Role.ACCENT)));
                g.drawString(initials,
                        (getWidth() - metrics.stringWidth(initials)) / 2,
                        (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
            }

            g.setColor(Palette.color(Role.CARD_BORDER));
            g.drawOval(x, y, size, size);
            g.dispose();
        }

        private String initialsOf(String fullName) {
            if (fullName == null || fullName.isBlank()) {
                return "?";
            }
            String[] parts = fullName.trim().split("\\s+");
            StringBuilder initials = new StringBuilder();
            initials.append(Character.toUpperCase(parts[0].charAt(0)));
            if (parts.length > 1) {
                initials.append(Character.toUpperCase(parts[parts.length - 1].charAt(0)));
            }
            return initials.toString();
        }
    }
}
