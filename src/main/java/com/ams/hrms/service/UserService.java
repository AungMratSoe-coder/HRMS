package com.ams.hrms.service;

import java.util.ArrayList;
import java.util.List;

import com.ams.hrms.event.EventBus;
import com.ams.hrms.event.Events;
import com.ams.hrms.exception.BusinessException;
import com.ams.hrms.exception.ValidationException;
import com.ams.hrms.repository.UserRepository;
import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SecurityService;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.util.AvatarImages;
import com.ams.hrms.validator.Validators;

/**
 * User account administration (spec section 7 / Roles &amp; Permissions):
 * create accounts, reset passwords, activate/deactivate and assign roles.
 * Every operation requires {@link Permissions#USER_MANAGE} and is audited.
 */
public class UserService {

    public static final String DATA_SCOPE = "users";

    private final UserRepository userRepository;
    private final AuditService auditService;
    private final com.ams.hrms.repository.EmployeeRepository employeeRepository;

    public UserService(UserRepository userRepository, AuditService auditService,
                       com.ams.hrms.repository.EmployeeRepository employeeRepository) {
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.employeeRepository = employeeRepository;
    }

    /** All accounts for the administration screen. */
    public List<UserRepository.UserRow> findAll() {
        SecurityService.require(Permissions.USER_MANAGE);
        return userRepository.findAll();
    }

    /** All assignable roles for pickers. */
    public List<UserRepository.RoleRef> findRoles() {
        SecurityService.require(Permissions.USER_MANAGE);
        return userRepository.findAllRoles();
    }

    /** Role ids currently assigned to a user. */
    public List<Long> findRoleIds(long userId) {
        SecurityService.require(Permissions.USER_MANAGE);
        return userRepository.findRoleIds(userId);
    }

    /**
     * Creates an account with a first password. The user must change it at
     * next sign-in ({@code must_change_password} is set).
     */
    public long createUser(String username, String fullName, String email,
                           String plainPassword, List<Long> roleIds) {
        SecurityService.require(Permissions.USER_MANAGE);

        String normalizedUsername = username == null ? "" : username.trim();
        List<String> errors = new ArrayList<>();
        Validators.pattern(errors, normalizedUsername, "Username",
                Validators.CODE_PATTERN, "EMP-0001");
        Validators.required(errors, fullName, "Full name");
        Validators.maxLength(errors, fullName, 150, "Full name");
        Validators.email(errors, email, "Email");
        AuthService.validateNewPassword(null, plainPassword);
        if (roleIds == null || roleIds.isEmpty()) {
            errors.add("Assign at least one role.");
        }
        if (userRepository.findAccountByUsername(normalizedUsername).isPresent()) {
            errors.add("Username '" + normalizedUsername + "' is already taken.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        long userId = userRepository.insert(normalizedUsername,
                com.ams.hrms.security.PasswordHasher.hash(plainPassword),
                fullName.trim(), email == null || email.isBlank() ? null : email.trim());
        userRepository.setMustChangePassword(userId, true);
        userRepository.replaceRoles(userId, roleIds);
        userRepository.linkByEmailIfUnlinked(userId);
        auditService.record("CREATE", "SECURITY", "User", userId,
                "Created user '" + normalizedUsername + "'");
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
        return userId;
    }

    /**
     * Administrative password reset: sets a fresh password and forces the
     * user to change it at next sign-in.
     */
    public void resetPassword(long userId, String newPlain) {
        SecurityService.require(Permissions.USER_MANAGE);
        AuthService.validateNewPassword(null, newPlain);

        userRepository.updatePassword(userId, com.ams.hrms.security.PasswordHasher.hash(newPlain));
        userRepository.setMustChangePassword(userId, true);
        auditService.record(AuditService.ACTION_PASSWORD_RESET, "SECURITY", "User", userId,
                "Password reset by '" + SessionContext.currentUser().username() + "'");
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
    }

    /** Activates or deactivates an account; deactivating yourself is refused. */
    public void setActive(long userId, boolean active) {
        SecurityService.require(Permissions.USER_MANAGE);
        if (userId == SessionContext.currentUserId() && !active) {
            throw new BusinessException("Cannot deactivate your own account",
                    "Ask another administrator to deactivate this account.");
        }
        userRepository.setActive(userId, active);
        auditService.record("STATUS_CHANGE", "SECURITY", "User", userId,
                "Account set to " + (active ? "ACTIVE" : "INACTIVE"));
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
    }

    /** Replaces the role assignment of a user; at least one role is required. */
    public void updateRoles(long userId, List<Long> roleIds) {
        SecurityService.require(Permissions.USER_MANAGE);
        if (roleIds == null || roleIds.isEmpty()) {
            throw new ValidationException(List.of("Assign at least one role."));
        }
        userRepository.replaceRoles(userId, roleIds);
        auditService.record("UPDATE", "SECURITY", "User", userId,
                "Roles updated by '" + SessionContext.currentUser().username() + "'");
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
    }

    /**
     * Updates the signed-in user's own contact details (email and phone).
     * Deliberately free of {@link Permissions#USER_MANAGE}: every account may
     * maintain its own profile; identity fields stay under HR control.
     */
    public void updateOwnProfile(String email, String phone) {
        long userId = SessionContext.currentUserId();
        String normalizedEmail = email == null ? "" : email.trim();
        String normalizedPhone = phone == null ? "" : phone.trim();

        List<String> errors = new ArrayList<>();
        Validators.maxLength(errors, normalizedEmail, 150, "Email");
        Validators.email(errors, normalizedEmail, "Email");
        Validators.phone(errors, normalizedPhone, "Phone");
        if (userRepository.emailTakenByOther(normalizedEmail, userId)) {
            errors.add("Email '" + normalizedEmail + "' is already used by another account.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        userRepository.updateProfile(userId,
                normalizedEmail.isBlank() ? null : normalizedEmail,
                normalizedPhone.isBlank() ? null : normalizedPhone);
        refreshSessionContact(normalizedEmail, normalizedPhone);
        auditService.record("UPDATE", "SECURITY", "User", userId,
                "Profile updated by '" + SessionContext.currentUser().username() + "'");
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
    }

    /** Re-publishes the session snapshot with fresh contact details. */
    private void refreshSessionContact(String email, String phone) {
        var user = SessionContext.currentUser();
        java.util.Set<String> codes = SessionContext.permissions().stream()
                .map(Permissions::name).collect(java.util.stream.Collectors.toSet());
        SessionContext.login(new com.ams.hrms.security.SessionContext.AuthenticatedUser(
                user.id(), user.username(), user.fullName(),
                email.isBlank() ? null : email, phone.isBlank() ? null : phone,
                user.employeeId(), user.mustChangePassword()),
                SessionContext.roles(), codes);
    }

    // ------------------------------------------------------------------
    // Profile picture (self-service, no directory permission required)
    // ------------------------------------------------------------------

    /**
     * Returns the signed-in user's stored profile picture (a square JPEG
     * thumbnail) or null when none was uploaded.
     */
    public byte[] findOwnAvatar() {
        return userRepository.findAvatar(SessionContext.currentUserId());
    }

    /**
     * Replaces the signed-in user's profile picture. The upload is validated,
     * center-cropped to a square and stored as a small JPEG thumbnail; the
     * original file bytes are never persisted.
     */
    public void updateOwnAvatar(byte[] imageBytes) {
        long userId = SessionContext.currentUserId();
        byte[] thumbnail = AvatarImages.squareThumbnail(imageBytes);
        userRepository.updateAvatar(userId, thumbnail);
        auditService.record("UPDATE", "SECURITY", "User", userId,
                "Profile picture updated by '" + SessionContext.currentUser().username() + "'");
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
    }

    /** Removes the signed-in user's profile picture; harmless when unset. */
    public void removeOwnAvatar() {
        long userId = SessionContext.currentUserId();
        userRepository.updateAvatar(userId, null);
        auditService.record("UPDATE", "SECURITY", "User", userId,
                "Profile picture removed by '" + SessionContext.currentUser().username() + "'");
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
    }

    /** Active employee options for the account-link picker (admin only). */
    public List<com.ams.hrms.repository.EmployeeRepository.EmployeeOption> findEmployeeOptions() {
        SecurityService.require(Permissions.USER_MANAGE);
        return employeeRepository.findActiveOptions();
    }

    /**
     * Links an account to the employee record it owns (self-service profile
     * source); null clears the link. An employee record cannot be owned by
     * two accounts, and the change reaches the target user's next session.
     */
    public void setEmployeeLink(long userId, Long employeeId) {
        SecurityService.require(Permissions.USER_MANAGE);
        if (userRepository.findRowById(userId).isEmpty()) {
            throw new BusinessException("User not found",
                    "The account no longer exists.");
        }
        if (employeeId != null) {
            Long owner = userRepository.findUserIdByEmployeeId(employeeId);
            if (owner != null && owner != userId) {
                throw new BusinessException("Employee already linked",
                        "Another account is already linked to this employee record.");
            }
        }
        userRepository.setEmployeeLink(userId, employeeId);
        auditService.record("UPDATE", "SECURITY", "User", userId,
                "Employee link "
                        + (employeeId == null ? "cleared" : "set to #" + employeeId)
                        + " by '" + SessionContext.currentUser().username() + "'");
        EventBus.publish(new Events.DataChanged(DATA_SCOPE));
    }
}
