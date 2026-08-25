package com.ams.hrms.security;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ams.hrms.exception.BusinessException;

/**
 * Brute-force protection for the login form: after {@link #MAX_ATTEMPTS}
 * consecutive failures for a username, further attempts are rejected for
 {@link #LOCKOUT_MS} milliseconds. Counters reset on successful sign-in.
 */
public class LoginAttemptGuard {

    static final int MAX_ATTEMPTS = 5;
    static final long LOCKOUT_MS = 30_000;

    private static final Logger LOG = LoggerFactory.getLogger(LoginAttemptGuard.class);

    private final Map<String, Deque<Long>> failureTimesByUsername = new ConcurrentHashMap<>();

    /**
     * Rejects the attempt when the username is currently locked out.
     *
     * @throws BusinessException when locked; message contains remaining seconds
     */
    public void ensureAllowed(String username) {
        Deque<Long> failures = failureTimesByUsername.get(username);
        if (failures == null) {
            return;
        }
        prune(failures);
        if (failures.size() >= MAX_ATTEMPTS) {
            long oldest = failures.peekFirst();
            long remainingMs = LOCKOUT_MS - (System.currentTimeMillis() - oldest);
            long remainingSeconds = Math.max(1, remainingMs / 1000);
            LOG.warn("Login locked for '{}' ({}s remaining)", username, remainingSeconds);
            throw new BusinessException(
                    "Too many failed login attempts for '" + username + "'",
                    "Too many failed attempts. Please try again in " + remainingSeconds + " seconds.");
        }
    }

    /** Records a failed attempt for the username. */
    public void recordFailure(String username) {
        Deque<Long> failures = failureTimesByUsername.computeIfAbsent(username,
                key -> new ConcurrentLinkedDeque<>());
        failures.addLast(System.currentTimeMillis());
    }

    /** Clears the failure history (successful sign-in). */
    public void reset(String username) {
        failureTimesByUsername.remove(username);
    }

    private void prune(Deque<Long> failures) {
        long cutoff = System.currentTimeMillis() - LOCKOUT_MS;
        while (!failures.isEmpty() && failures.peekFirst() < cutoff) {
            failures.pollFirst();
        }
    }
}
