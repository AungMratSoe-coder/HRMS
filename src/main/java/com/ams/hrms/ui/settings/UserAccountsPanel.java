package com.ams.hrms.ui.settings;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;

import com.ams.hrms.component.HrmsTable;
import com.ams.hrms.component.ModernButton;
import com.ams.hrms.component.SecureButton;
import com.ams.hrms.component.Toast;
import com.ams.hrms.config.ServiceRegistry;
import com.ams.hrms.controller.UserController;
import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.ErrorHandler;
import com.ams.hrms.repository.UserRepository;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.service.UserService;
import com.ams.hrms.util.Dialogs;
import com.ams.hrms.util.UiThread;

import net.miginfocom.swing.MigLayout;

/**
 * User account administration tab (Settings): list accounts, create users,
 * reset passwords, activate/deactivate and assign roles. Requires
 * {@link Permissions#USER_MANAGE}.
 */
public class UserAccountsPanel extends JPanel {

    private final UserController controller =
            new UserController(ServiceRegistry.userService());

    private final SecureButton newUserButton =
            new SecureButton("New User", "plus", ModernButton.Variant.PRIMARY,
                    Permissions.USER_MANAGE);
    private final SecureButton resetPasswordButton =
            new SecureButton("Reset Password", "refresh", ModernButton.Variant.OUTLINE,
                    Permissions.USER_MANAGE);
    private final SecureButton toggleActiveButton =
            new SecureButton("Deactivate", "close", ModernButton.Variant.OUTLINE,
                    Permissions.USER_MANAGE);
    private final SecureButton editRolesButton =
            new SecureButton("Edit Roles", "settings", ModernButton.Variant.OUTLINE,
                    Permissions.USER_MANAGE);
    private final SecureButton linkEmployeeButton =
            new SecureButton("Link Employee", "employees", ModernButton.Variant.OUTLINE,
                    Permissions.USER_MANAGE);

    private final HrmsTable table = HrmsTable.builder(
                    "ID", "Username", "Full Name", "Email", "Roles",
                    "Status", "Must Change", "Last Login")
            .hiddenColumn(0)
            .fixedColumn(1, 120)
            .fixedColumn(5, 100)
            .fixedColumn(6, 100)
            .fixedColumn(7, 110)
            .badgeColumn(5)
            .contextMenu(this::buildContextMenu)
            .build();

    private List<UserRepository.UserRow> loaded = List.of();

    public UserAccountsPanel() {
        super(new BorderLayout());
        setOpaque(false);

        JPanel toolbar = new JPanel(new MigLayout("insets 14 18, gap 8"));
        toolbar.setOpaque(false);
        toolbar.add(newUserButton);
        toolbar.add(resetPasswordButton, "gap 8");
        toolbar.add(toggleActiveButton);
        toolbar.add(editRolesButton);
        toolbar.add(linkEmployeeButton);

        add(toolbar, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);

        wireEvents();
        refresh();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        EventBus.subscribe(Events.DataChanged.class, event -> {
            if (UserService.DATA_SCOPE.equals(event.scope())) {
                UiThread.runLater(this::refresh);
            }
        });
    }

    private void wireEvents() {
        newUserButton.addActionListener(event -> showCreateDialog());
        resetPasswordButton.addActionListener(event -> resetPassword());
        toggleActiveButton.addActionListener(event -> toggleActive());
        editRolesButton.addActionListener(event -> editRoles());
        linkEmployeeButton.addActionListener(event -> linkEmployee());
    }

    private void refresh() {
        controller.loadUsers(rows -> {
            loaded = rows;
            List<Object[]> tableRows = new ArrayList<>();
            for (UserRepository.UserRow user : rows) {
                tableRows.add(new Object[]{
                        user.id(),
                        user.username(),
                        user.fullName(),
                        user.email() == null ? "-" : user.email(),
                        user.roles(),
                        user.active() ? "ACTIVE" : "INACTIVE",
                        user.mustChangePassword() ? "Yes" : "-",
                        user.lastLoginAt() == null ? "-"
                                : user.lastLoginAt().toLocalDate().toString()});
            }
            table.setRows(tableRows);
            updateToggleButton();
        });
    }

    private UserRepository.UserRow selectedUser() {
        Object id = table.selectedValue(0);
        if (id == null) {
            return null;
        }
        long target = ((Number) id).longValue();
        for (UserRepository.UserRow user : loaded) {
            if (user.id() == target) {
                return user;
            }
        }
        return null;
    }

    private void updateToggleButton() {
        UserRepository.UserRow user = selectedUser();
        boolean deactivate = user != null && user.active();
        toggleActiveButton.setText(deactivate ? "Deactivate" : "Activate");
    }

    private void showCreateDialog() {
        controller.loadRoles(roles -> UiThread.runLater(() ->
                new CreateUserDialog(swingWindow(), roles).showDialog(this::refresh)));
    }

    private void resetPassword() {
        UserRepository.UserRow user = selectedUser();
        if (user == null) {
            return;
        }
        String newPassword = Dialogs.prompt(this, "Reset Password",
                "New password for '" + user.username() + "':");
        if (newPassword == null || newPassword.isBlank()) {
            return;
        }
        controller.resetPassword(user.id(), newPassword, () -> {
            Toast.show(swingWindow(), Toast.Type.SUCCESS,
                    "Password reset; the user must change it at next sign-in.");
            refresh();
        }, this::handleError);
    }

    private void toggleActive() {
        UserRepository.UserRow user = selectedUser();
        if (user == null) {
            return;
        }
        boolean activating = !user.active();
        boolean confirmed = Dialogs.confirm(this, activating ? "Activate" : "Deactivate",
                (activating ? "Activate" : "Deactivate") + " account '" + user.username() + "'?");
        if (!confirmed) {
            return;
        }
        controller.setActive(user.id(), activating, this::refresh, this::handleError);
    }

    private void editRoles() {
        UserRepository.UserRow user = selectedUser();
        if (user == null) {
            return;
        }
        controller.loadRoles(roles -> controller.loadRoleIds(user.id(), currentIds ->
                UiThread.runLater(() -> {
                    List<Long> chosen = RolePickerDialog.show(swingWindow(), roles, currentIds,
                            user.username());
                    if (chosen != null) {
                        controller.updateRoles(user.id(), chosen, this::refresh, this::handleError);
                    }
                })));
    }

    /** Picks the employee record this account owns; drives self-service profiles. */
    private void linkEmployee() {
        UserRepository.UserRow user = selectedUser();
        if (user == null) {
            return;
        }
        controller.loadEmployeeOptions(options -> UiThread.runLater(() -> {
            // null = cancelled; present-but-empty = clear the link.
            java.util.Optional<Long> chosen =
                    EmployeeLinkPickerDialog.show(swingWindow(), options, user.username());
            if (chosen != null) {
                controller.setEmployeeLink(user.id(), chosen.orElse(null),
                        () -> {
                            Toast.show(swingWindow(), Toast.Type.SUCCESS,
                                    "Employee link updated.");
                            refresh();
                        }, this::handleError);
            }
        }));
    }

    private JPopupMenu buildContextMenu() {
        JPopupMenu menu = new JPopupMenu();
        UserRepository.UserRow user = selectedUser();

        JMenuItem reset = new JMenuItem("Reset Password...");
        reset.setEnabled(user != null);
        reset.addActionListener(event -> resetPassword());

        JMenuItem roles = new JMenuItem("Edit Roles...");
        roles.setEnabled(user != null);
        roles.addActionListener(event -> editRoles());

        JMenuItem link = new JMenuItem("Link Employee...");
        link.setEnabled(user != null);
        link.addActionListener(event -> linkEmployee());

        JMenuItem toggle = new JMenuItem(user != null && user.active()
                ? "Deactivate" : "Activate");
        toggle.setEnabled(user != null);
        toggle.addActionListener(event -> toggleActive());

        menu.add(reset);
        menu.add(roles);
        menu.add(link);
        menu.addSeparator();
        menu.add(toggle);
        return menu;
    }

    private void handleError(Exception error) {
        ErrorHandler.handle(this, error);
    }

    private java.awt.Window swingWindow() {
        return javax.swing.SwingUtilities.getWindowAncestor(this);
    }

    // ------------------------------------------------------------------
    // Dialogs
    // ------------------------------------------------------------------

    /** Modal create-user form: identity, first password and role checklist. */
    private static final class CreateUserDialog {

        private final javax.swing.JDialog dialog;
        private final javax.swing.JTextField usernameField = new javax.swing.JTextField(16);
        private final javax.swing.JTextField fullNameField = new javax.swing.JTextField(16);
        private final javax.swing.JTextField emailField = new javax.swing.JTextField(16);
        private final javax.swing.JPasswordField passwordField = new javax.swing.JPasswordField(16);
        private final List<javax.swing.JCheckBox> roleChecks = new ArrayList<>();
        private boolean saved;

        CreateUserDialog(java.awt.Window owner, List<UserRepository.RoleRef> roles) {
            dialog = new javax.swing.JDialog(owner, "New User",
                    java.awt.Dialog.ModalityType.APPLICATION_MODAL);
            JPanel form = new JPanel(new MigLayout("wrap 2, gap 8",
                    "[right][grow,fill]"));
            form.setOpaque(false);
            form.add(new JLabel("Username:"));
            form.add(usernameField);
            form.add(new JLabel("Full name:"));
            form.add(fullNameField);
            form.add(new JLabel("Email:"));
            form.add(emailField);
            form.add(new JLabel("Initial password:"));
            form.add(passwordField);
            form.add(new JLabel("Roles:"));
            JPanel rolePanel = new JPanel(new MigLayout("wrap 1, insets 0, gap 2"));
            rolePanel.setOpaque(false);
            for (UserRepository.RoleRef role : roles) {
                javax.swing.JCheckBox check = new javax.swing.JCheckBox(
                        role.code() + " - " + role.name());
                roleChecks.add(check);
                rolePanel.add(check);
            }
            form.add(rolePanel);

            JLabel errorLabel = new JLabel();
            errorLabel.setForeground(com.ams.hrms.ui.theme.Palette.color(
                    com.ams.hrms.ui.theme.Palette.Role.DANGER));
            errorLabel.setVisible(false);

            ModernButton save = new ModernButton("Create User", ModernButton.Variant.PRIMARY);
            ModernButton cancel = new ModernButton("Cancel", ModernButton.Variant.OUTLINE);
            JPanel buttons = new JPanel(new MigLayout("insets 0, gap 8, right"));
            buttons.setOpaque(false);
            buttons.add(cancel);
            buttons.add(save);

            JPanel content = new JPanel(new MigLayout("wrap 1, insets 18 22, gap 10"));
            content.setOpaque(false);
            content.add(form);
            content.add(errorLabel);
            content.add(buttons, "growx");
            dialog.setContentPane(content);
            dialog.getRootPane().setDefaultButton(save);
            dialog.pack();
            dialog.setLocationRelativeTo(owner);

            cancel.addActionListener(event -> dialog.dispose());
            save.addActionListener(event -> {
                List<Long> selectedRoleIds = new ArrayList<>();
                for (int i = 0; i < roleChecks.size(); i++) {
                    if (roleChecks.get(i).isSelected()) {
                        selectedRoleIds.add(roles.get(i).id());
                    }
                }
                UiThread.executeAsync("Create user",
                        () -> ServiceRegistry.userService().createUser(
                                usernameField.getText(), fullNameField.getText(),
                                emailField.getText(),
                                new String(passwordField.getPassword()), selectedRoleIds),
                        id -> {
                            saved = true;
                            dialog.dispose();
                        },
                        error -> {
                            Exception exception = error instanceof Exception e ? e
                                    : new IllegalStateException(error);
                            if (exception instanceof com.ams.hrms.exception.ValidationException validation) {
                                errorLabel.setText(wrappedError(
                                        String.join(" ", validation.getErrors())));
                                errorLabel.setVisible(true);
                                // The error row grows the content; re-pack and
                                // re-center so the button row stays visible.
                                dialog.pack();
                                dialog.setLocationRelativeTo(owner);
                            } else {
                                ErrorHandler.handle(dialog, exception);
                            }
                        });
            });
        }

        /** Shows the dialog and runs {@code onSaved} after a successful create. */
        void showDialog(Runnable onSaved) {
            dialog.addComponentListener(new java.awt.event.ComponentAdapter() {
                @Override
                public void componentHidden(java.awt.event.ComponentEvent event) {
                    if (saved) {
                        onSaved.run();
                    }
                }
            });
            dialog.setVisible(true);
        }

        /**
         * Renders an error message as line-wrapping HTML capped to the form's
         * natural width, so long validation messages grow the dialog in
         * height only instead of stretching it towards screen width.
         */
        private static String wrappedError(String message) {
            String escaped = message.replace("&", "&amp;")
                    .replace("<", "&lt;").replace(">", "&gt;");
            return "<html><div style='width:340px'>" + escaped + "</div></html>";
        }
    }

    /**
     * Modal role checklist; returns the chosen ids or null when cancelled.
     */
    private static final class RolePickerDialog {

        private RolePickerDialog() {
        }

        static List<Long> show(java.awt.Window owner, List<UserRepository.RoleRef> roles,
                               List<Long> currentIds, String username) {
            List<javax.swing.JCheckBox> checks = new ArrayList<>();
            JPanel panel = new JPanel(new MigLayout("wrap 1, insets 8 12, gap 4"));
            panel.setOpaque(false);
            for (UserRepository.RoleRef role : roles) {
                javax.swing.JCheckBox check = new javax.swing.JCheckBox(
                        role.code() + " - " + role.name(), currentIds.contains(role.id()));
                checks.add(check);
                panel.add(check);
            }
            int choice = javax.swing.JOptionPane.showConfirmDialog(owner, panel,
                    "Roles for '" + username + "'",
                    javax.swing.JOptionPane.OK_CANCEL_OPTION,
                    javax.swing.JOptionPane.PLAIN_MESSAGE);
            if (choice != javax.swing.JOptionPane.OK_OPTION) {
                return null;
            }
            List<Long> selected = new ArrayList<>();
            for (int i = 0; i < checks.size(); i++) {
                if (checks.get(i).isSelected()) {
                    selected.add(roles.get(i).id());
                }
            }
            if (selected.isEmpty()) {
                Dialogs.info(owner, "Edit Roles", "Assign at least one role.");
                return null;
            }
            return selected;
        }
    }

    /**
     * Modal employee picker; returns {@code Optional.empty()} when the user
     * confirms with no employee selected (clears the link) and null when
     * cancelled. Records already owned by another account are marked and
     * refused - one employee record belongs to one account.
     */
    private static final class EmployeeLinkPickerDialog {

        private EmployeeLinkPickerDialog() {
        }

        static java.util.Optional<Long> show(java.awt.Window owner,
                List<com.ams.hrms.repository.EmployeeRepository.EmployeeOption> options,
                String username) {
            javax.swing.JComboBox<String> combo = new javax.swing.JComboBox<>();
            combo.addItem("- No link -");
            for (var option : options) {
                String label = option.display()
                        + (option.linkedUsername() == null ? ""
                                : "  [linked: " + option.linkedUsername() + "]");
                combo.addItem(label);
            }
            JPanel panel = new JPanel(new net.miginfocom.swing.MigLayout(
                    "wrap 1, insets 8 12, gap 6"));
            panel.setOpaque(false);
            panel.add(new javax.swing.JLabel(
                    "<html>Employee record owned by '<b>" + username
                            + "</b>'. It drives the self-service profile.</html>"));
            panel.add(combo, "growx");

            while (true) {
                int choice = javax.swing.JOptionPane.showConfirmDialog(owner, panel,
                        "Link Employee for '" + username + "'",
                        javax.swing.JOptionPane.OK_CANCEL_OPTION,
                        javax.swing.JOptionPane.PLAIN_MESSAGE);
                if (choice != javax.swing.JOptionPane.OK_OPTION) {
                    return null;
                }
                int index = combo.getSelectedIndex();
                if (index <= 0) {
                    return java.util.Optional.empty();
                }
                var selected = options.get(index - 1);
                if (selected.linkedUsername() != null
                        && !selected.linkedUsername().equals(username)) {
                    javax.swing.JOptionPane.showMessageDialog(owner,
                            "'" + selected.display() + "' is already linked to account '"
                                    + selected.linkedUsername() + "'. "
                                    + "Unlink it there before linking it here.",
                            "Employee already linked",
                            javax.swing.JOptionPane.WARNING_MESSAGE);
                    continue;
                }
                return java.util.Optional.of(selected.id());
            }
        }
    }
}
