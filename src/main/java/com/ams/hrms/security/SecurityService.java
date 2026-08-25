package com.ams.hrms.security;

import java.util.Arrays;

import com.ams.hrms.exception.AuthenticationException;
import com.ams.hrms.exception.AuthorizationException;

/**
 * Service-layer authorization gate (spec section 8). UI hiding is never
 * trusted: every mutating or sensitive service method calls
 * {@link #require(Permissions)} (or the any/all variants) before doing work.
 */
public final class SecurityService {

    private SecurityService() {
    }

    /** True when the current session holds the given permission. */
    public static boolean can(Permissions permission) {
        return SessionContext.has(permission);
    }

    /**
     * Fails unless the current session holds {@code permission}.
     *
     * @throws AuthenticationException when nobody is signed in
     * @throws AuthorizationException when the permission is missing
     */
    public static void require(Permissions permission) {
        requireAuthenticated();
        if (!SessionContext.has(permission)) {
            throw new AuthorizationException(
                    "Missing required permission: " + permission.name(),
                    "You do not have permission to perform this action.");
        }
    }

    /** Fails unless at least one of the permissions is held. */
    public static void requireAny(Permissions... permissions) {
        requireAuthenticated();
        for (Permissions permission : permissions) {
            if (SessionContext.has(permission)) {
                return;
            }
        }
        throw new AuthorizationException(
                "Missing required permission (any of): " + names(permissions),
                "You do not have permission to perform this action.");
    }

    /** Fails unless all of the permissions are held. */
    public static void requireAll(Permissions... permissions) {
        requireAuthenticated();
        for (Permissions permission : permissions) {
            require(permission);
        }
    }

    private static void requireAuthenticated() {
        if (!SessionContext.isAuthenticated()) {
            throw new AuthenticationException(
                    "Authorization attempted without an active session",
                    AuthenticationException.DEFAULT_USER_MESSAGE);
        }
    }

    private static String names(Permissions[] permissions) {
        return String.join(", ",
                Arrays.stream(permissions).map(Enum::name).toArray(String[]::new));
    }
}
