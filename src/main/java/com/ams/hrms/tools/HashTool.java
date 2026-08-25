package com.ams.hrms.tools;

import com.ams.hrms.security.PasswordHasher;

/**
 * Development utility: generates a BCrypt hash for seeding or resetting a user
 * password, or verifies a candidate password against an existing hash.
 *
 * Usage:
 *   HashTool &lt;plainTextPassword&gt;             prints a fresh hash
 *   HashTool &lt;plainTextPassword&gt; &lt;hash&gt;      prints MATCH or MISMATCH
 *
 * This tool prints only hashes - never log or commit a plaintext password.
 */
public final class HashTool {

    public static void main(String[] args) {
        if (args.length == 0 || args[0].isBlank()) {
            System.err.println("Usage: HashTool <plainTextPassword> [hashToVerify]");
            System.exit(2);
            return;
        }
        String plainPassword = args[0];

        if (args.length == 1) {
            System.out.println(PasswordHasher.hash(plainPassword));
            return;
        }

        boolean matches = PasswordHasher.verify(plainPassword, args[1]);
        System.out.println(matches ? "MATCH" : "MISMATCH");
        System.exit(matches ? 0 : 1);
    }

    private HashTool() {
    }
}
