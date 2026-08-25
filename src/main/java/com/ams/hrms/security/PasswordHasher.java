package com.ams.hrms.security;

import at.favre.lib.crypto.bcrypt.BCrypt;
import at.favre.lib.crypto.bcrypt.BCrypt.Result;

/**
 * Password hashing built on BCrypt (cost 12). Plaintext passwords and hashes
 * are never logged anywhere in the application.
 */
public final class PasswordHasher {

    /** BCrypt cost factor - a deliberate trade-off (~250ms) against brute force. */
    private static final int COST = 12;

    private PasswordHasher() {
    }

    /**
     * Hashes a plaintext password with a fresh random salt.
     *
     * @param plainPassword the password as typed by the user (not stored)
     * @return a modular BCrypt hash string suitable for database storage
     */
    public static String hash(String plainPassword) {
        return BCrypt.withDefaults().hashToString(COST, plainPassword.toCharArray());
    }

    /**
     * Verifies a plaintext password against a stored hash in constant time.
     *
     * @return true when the password matches the hash
     */
    public static boolean verify(String plainPassword, String storedHash) {
        if (plainPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        Result result = BCrypt.verifyer().verify(plainPassword.toCharArray(), storedHash);
        return result.verified;
    }
}
