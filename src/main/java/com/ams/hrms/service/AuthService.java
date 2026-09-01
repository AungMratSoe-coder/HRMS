package com.ams.hrms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.exception.AuthenticationException;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.model.UserAccount;
import com.ams.hrms.repository.UserRepository;
import com.ams.hrms.security.LoginAttemptGuard;
import com.ams.hrms.security.PasswordHasher;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.security.SessionContext.AuthenticatedUser;
import com.ams.hrms.security.SessionContext.RoleRef;

/**
 * Authentication lifecycle (spec section 7): credential verification with
 * BCrypt, account-state checks, brute-force lockout, session publication and
 * audit trail entries. Verification is constant-time; unknown emails burn
 * equivalent time so they are not distinguishable from wrong passwords.
 */
public class AuthService {

    private static final Logger LOG = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final LoginAttemptGuard attemptGuard;

    public AuthService(UserRepository userRepository, AuditService auditService,
                       LoginAttemptGuard attemptGuard) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.attemptGuard = attemptGuard;
    }

    /**
     * Verifies credentials and starts a session.
     *
     * @param email the account's email address (the sign-in credential)
     * @param password plaintext password; the reference is released as early as possible
     * @return the authenticated user identity now held in {@link SessionContext}
     */
    public AuthenticatedUser login(String email, String password) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(java.util.Locale.ROOT);
        attemptGuard.ensureAllowed(normalizedEmail);

        if (normalizedEmail.isEmpty() || password == null || password.isEmpty()) {
            throw new AuthenticationException();
        }

        UserAccount account = userRepository.findAccountByEmail(normalizedEmail).orElse(null);
        boolean verified = false;
        if (account != null) {
            verified = PasswordHasher.verify(password, account.passwordHash());
        } else {
            // Burn comparable time so unknown emails are not distinguishable.
            PasswordHasher.verify(password, "$2a$12$C6UzMDM.H6dfI/f/IKcEeO7ZBpQ0MzW8LzEB3vq1vHnDDQ2zVQKmu");
        }
        password = null; // drop the plaintext reference as early as possible

        if (!verified) {
            attemptGuard.recordFailure(normalizedEmail);
            auditService.record(AuditService.ACTION_LOGIN_FAILED, "SECURITY", "User", null,
                    "Failed sign-in for '" + normalizedEmail + "'");
            LOG.info("Sign-in failed for '{}'", normalizedEmail);
            throw new AuthenticationException();
        }

        if (!account.active()) {
            auditService.record(AuditService.ACTION_LOGIN_FAILED, "SECURITY", "User", account.id(),
                    "Sign-in refused: inactive account '" + normalizedEmail + "'");
            throw new AuthenticationException(
                    "Inactive account: " + normalizedEmail,
                    "This account has been deactivated. Please contact your administrator.");
        }

        List<RoleRef> roles = userRepository.findRoles(account.id()).stream()
                .map(role -> new RoleRef(role.code(), role.name()))
                .collect(Collectors.toList());
        Set<String> permissionCodes = Set.copyOf(userRepository.findPermissionCodes(account.id()));

        AuthenticatedUser user = new AuthenticatedUser(
                account.id(), account.username(), account.fullName(), account.email(),
                account.phone(), account.employeeId(), account.mustChangePassword());
        SessionContext.login(user, Set.copyOf(roles), permissionCodes);
        userRepository.touchLastLogin(account.id());

        attemptGuard.reset(normalizedEmail);
        auditService.record(AuditService.ACTION_LOGIN, "SECURITY", "User", account.id(),
                "User '" + normalizedEmail + "' signed in");
        return user;
    }

    /**
     * Changes the signed-in user's own password: verifies the current one,
     * enforces the password policy, stores the new hash and clears the
     * forced-change flag.
     */
    public void changePassword(String currentPlain, String newPlain) {
        long userId = SessionContext.currentUserId();
        UserAccount account = userRepository.findAccountById(userId)
                .orElseThrow(() -> new IllegalStateException("No session account"));

        if (currentPlain == null || !PasswordHasher.verify(currentPlain, account.passwordHash())) {
            throw new ValidationException(List.of("Current password is incorrect."));
        }
        validateNewPassword(currentPlain, newPlain);

        userRepository.updatePassword(userId, PasswordHasher.hash(newPlain));
        userRepository.setMustChangePassword(userId, false);
        auditService.record(AuditService.ACTION_PASSWORD_CHANGE, "SECURITY", "User", userId,
                "Password changed for '" + account.username() + "'");
    }

    /**
     * Sets a new password without the current one; only allowed right after
     * sign-in when the account carries the forced-change flag (e.g. after an
     * administrator reset).
     */
    public void completeForcedPasswordChange(String newPlain) {
        long userId = SessionContext.currentUserId();
        AuthenticatedUser user = SessionContext.currentUser();
        if (!user.mustChangePassword()) {
            throw new BusinessException("Password change is not required",
                    "This account was not flagged for a password change.");
        }
        validateNewPassword(null, newPlain);

        userRepository.updatePassword(userId, PasswordHasher.hash(newPlain));
        userRepository.setMustChangePassword(userId, false);
        auditService.record(AuditService.ACTION_PASSWORD_CHANGE, "SECURITY", "User", userId,
                "Forced password change completed for '" + user.username() + "'");

        // Refresh the session snapshot so the flag does not linger.
        Set<String> codes = SessionContext.permissions().stream()
                .map(Permissions::name).collect(Collectors.toSet());
        SessionContext.login(new AuthenticatedUser(user.id(), user.username(),
                user.fullName(), user.email(), user.phone(), user.employeeId(), false),
                SessionContext.roles(), codes);
    }

    /** Password policy shared by self-change, forced change and admin resets. */
    static void validateNewPassword(String currentPlain, String newPlain) {
        List<String> errors = new ArrayList<>();
        if (newPlain == null || newPlain.length() < 8) {
            errors.add("New password must be at least 8 characters long.");
        }
        if (newPlain != null && !newPlain.matches(".*[A-Z].*")) {
            errors.add("New password must contain an uppercase letter.");
        }
        if (newPlain != null && !newPlain.matches(".*[a-z].*")) {
            errors.add("New password must contain a lowercase letter.");
        }
        if (newPlain != null && !newPlain.matches(".*[0-9].*")) {
            errors.add("New password must contain a digit.");
        }
        if (currentPlain != null && newPlain != null && newPlain.equals(currentPlain)) {
            errors.add("New password must be different from the current password.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    /** True when the current session may use the given permission. */
    public boolean hasPermission(Permissions permission) {
        return SessionContext.has(permission);
    }

    /**
     * Re-checks the signed-in user's password without side effects (no
     * lockout counting, no audit entries, session untouched). Used by the
     * idle-lock screen to confirm the person at the keyboard is the account
     * owner.
     */
    public boolean verifyPassword(String rawPassword) {
        Long userId = SessionContext.currentUserId();
        if (userId == null) {
            return false;
        }
        return userRepository.findAccountById(userId)
                .map(account -> PasswordHasher.verify(
                        rawPassword == null ? "" : rawPassword, account.passwordHash()))
                .orElse(false);
    }

    /** Ends the current session and writes the logout audit entry. */
    public void logout() {
        if (SessionContext.isAuthenticated()) {
            String username = SessionContext.currentUser().username();
            auditService.record(AuditService.ACTION_LOGOUT, "SECURITY", "User",
                    SessionContext.currentUserId(), "User '" + username + "' signed out");
        }
        SessionContext.clear();
    }
}
