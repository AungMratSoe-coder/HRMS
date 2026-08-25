package com.ams.hrms.security;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Holds the authenticated user's session for the running application
 * (spec section 7). An immutable snapshot is published on login and cleared
 * on logout, so readers never see partially-populated state.
 *
 * <p>The permission set is loaded once at login from the database; the
 * service layer checks it via {@link #has(Permissions)} (Phase 4 wires this
 * into SecurityService).</p>
 */
public final class SessionContext {

    private static final Logger LOG = LoggerFactory.getLogger(SessionContext.class);

    /** Priority used only for display purposes (e.g. header role chip). */
    private static final List<String> ROLE_DISPLAY_ORDER = List.of(
            "SUPER_ADMIN", "HR_MANAGER", "HR_OFFICER", "MANAGER", "FINANCE", "EMPLOYEE");

    /**
     * Sanitized identity stored in the session. Never carries password
     * material. {@code mustChangePassword} is set by an administrator reset
     * and drives the forced password change after sign-in.
     */
    public record AuthenticatedUser(long id, String username, String fullName, String email,
                                    String phone, Long employeeId, boolean mustChangePassword) {
    }

    public record RoleRef(String code, String name) {
    }

    private record Session(AuthenticatedUser user, Set<RoleRef> roles,
                           Set<Permissions> permissions, Instant loggedInAt) {
    }

    private static volatile Session active;

    private SessionContext() {
    }

    /**
     * Publishes a new authenticated session.
     *
     * @param roles          roles of the user
     * @param permissionCodes raw codes from the database; unknown codes are skipped with a warning
     */
    public static void login(AuthenticatedUser user, Set<RoleRef> roles, Set<String> permissionCodes) {
        Set<Permissions> permissions = permissionCodes == null ? Set.of() : permissionCodes.stream()
                .map(Permissions::fromCode)
                .flatMap(java.util.Optional::stream)
                .collect(Collectors.toUnmodifiableSet());

        long skipped = permissionCodes == null ? 0 : permissionCodes.size() - permissions.size();
        if (skipped > 0) {
            LOG.warn("{} permission code(s) unknown to this build were ignored for user '{}'",
                    skipped, user.username());
        }
        active = new Session(user, Set.copyOf(roles), Set.copyOf(permissions), Instant.now());
        LOG.info("Session started for '{}' with {} role(s) and {} permission(s)",
                user.username(), roles.size(), permissions.size());
    }

    public static boolean isAuthenticated() {
        return active != null;
    }

    public static AuthenticatedUser currentUser() {
        Session session = active;
        if (session == null) {
            throw new IllegalStateException("No authenticated user");
        }
        return session.user();
    }

    public static Long currentUserId() {
        Session session = active;
        return session == null ? null : session.user().id();
    }

    /**
     * Employee record linked to the signed-in account (self-service profile),
     * or null when the account is unlinked or nobody is signed in.
     */
    public static Long currentEmployeeId() {
        Session session = active;
        return session == null ? null : session.user().employeeId();
    }

    public static Set<RoleRef> roles() {
        Session session = active;
        return session == null ? Set.of() : session.roles();
    }

    public static Set<Permissions> permissions() {
        Session session = active;
        return session == null ? Set.of() : session.permissions();
    }

    /** True when the current session holds the given permission. */
    public static boolean has(Permissions permission) {
        Session session = active;
        return session != null && session.permissions().contains(permission);
    }

    /**
     * True when the session holds at least one role and all of them equal
     * {@code roleCode} (e.g. a plain EMPLOYEE self-service account).
     */
    public static boolean hasOnlyRole(String roleCode) {
        Session session = active;
        if (session == null || session.roles().isEmpty()) {
            return false;
        }
        return session.roles().stream().allMatch(role -> role.code().equals(roleCode));
    }

    /** Most significant role name for display; empty string when unknown. */
    public static String primaryRoleName() {
        Session session = active;
        if (session == null) {
            return "";
        }
        for (String code : ROLE_DISPLAY_ORDER) {
            for (RoleRef role : session.roles()) {
                if (role.code().equals(code)) {
                    return role.name();
                }
            }
        }
        return session.roles().isEmpty() ? "" : session.roles().iterator().next().name();
    }

    public static Instant loggedInAt() {
        Session session = active;
        return session == null ? null : session.loggedInAt();
    }

    /** Ends the session. Safe to call when not logged in. */
    public static void clear() {
        Session session = active;
        if (session != null) {
            LOG.info("Session ended for '{}'", session.user().username());
        }
        active = null;
    }
}
