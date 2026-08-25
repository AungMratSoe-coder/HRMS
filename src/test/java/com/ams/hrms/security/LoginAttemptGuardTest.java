package com.ams.hrms.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ams.hrms.exception.BusinessException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the login brute-force guard (business logic, no DB/Swing).
 */
class LoginAttemptGuardTest {

    private final LoginAttemptGuard guard = new LoginAttemptGuard();

    @Test
    @DisplayName("allows login before any failures")
    void allowsFirstAttempt() {
        assertThatCode(() -> guard.ensureAllowed("alice")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("locks the username after the maximum consecutive failures")
    void locksAfterMaxFailures() {
        for (int i = 0; i < LoginAttemptGuard.MAX_ATTEMPTS; i++) {
            guard.recordFailure("bob");
        }
        assertThatThrownBy(() -> guard.ensureAllowed("bob"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions
                        .assertThat(((BusinessException) exception).getUserMessage())
                        .contains("Too many failed attempts"));
    }

    @Test
    @DisplayName("reset clears the failure history")
    void resetClearsHistory() {
        for (int i = 0; i < LoginAttemptGuard.MAX_ATTEMPTS; i++) {
            guard.recordFailure("carol");
        }
        guard.reset("carol");
        assertThatCode(() -> guard.ensureAllowed("carol")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("lockouts are tracked per username independently")
    void usernamesAreIndependent() {
        for (int i = 0; i < LoginAttemptGuard.MAX_ATTEMPTS; i++) {
            guard.recordFailure("dave");
        }
        assertThatThrownBy(() -> guard.ensureAllowed("dave")).isInstanceOf(BusinessException.class);
        assertThatCode(() -> guard.ensureAllowed("erin")).doesNotThrowAnyException();
    }
}
