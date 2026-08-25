package com.ams.hrms.model;

/**
 * Full user account row including the password hash. Used only by
 * {@code AuthService}; sanitized {@link com.ams.hrms.security.SessionContext.AuthenticatedUser}
 * is what enters the session. The hash never leaves the service layer and is
 * never logged.
 *
 * @param employeeId employee record owned by this login (self-service
 *                   profile); null when the account is not linked
 */
public record UserAccount(long id,
                          String username,
                          String passwordHash,
                          String fullName,
                          String email,
                          String phone,
                          boolean active,
                          boolean mustChangePassword,
                          Long employeeId) {
}
