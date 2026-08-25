package com.ams.hrms.security;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ams.hrms.exception.AuthorizationException;
import com.ams.hrms.security.SessionContext.AuthenticatedUser;
import com.ams.hrms.security.SessionContext.RoleRef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure-logic tests for the service-layer authorization gate (no DB, no Swing).
 */
class SecurityServiceTest {

    private static final AuthenticatedUser TEST_USER =
            new AuthenticatedUser(1L, "tester", "Test User", "tester@local", null, null, false);

    @AfterEach
    void tearDown() {
        SessionContext.clear();
    }

    private void loginWith(String... permissionCodes) {
        SessionContext.login(TEST_USER,
                Set.of(new RoleRef("TEST_ROLE", "Test Role")),
                Set.of(permissionCodes));
    }

    @Test
    @DisplayName("require fails when nobody is signed in")
    void requiresAuthentication() {
        assertThatThrownBy(() -> SecurityService.require(Permissions.EMPLOYEE_VIEW))
                .isInstanceOf(com.ams.hrms.exception.AuthenticationException.class);
    }

    @Test
    @DisplayName("require passes with the permission and fails without it")
    void requireChecksPermission() {
        loginWith("EMPLOYEE_VIEW", "REPORT_VIEW");

        assertThat(SecurityService.can(Permissions.EMPLOYEE_VIEW)).isTrue();
        assertThatCode(() -> SecurityService.require(Permissions.EMPLOYEE_VIEW))
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> SecurityService.require(Permissions.PAYROLL_APPROVE))
                .isInstanceOf(AuthorizationException.class)
                .hasMessageContaining("PAYROLL_APPROVE");
    }

    @Test
    @DisplayName("requireAny passes when at least one matches; requireAll needs all")
    void anyAndAllVariants() {
        loginWith("LEAVE_REQUEST", "OVERTIME_REQUEST");

        assertThatCode(() -> SecurityService.requireAny(
                Permissions.LEAVE_APPROVE, Permissions.LEAVE_REQUEST))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> SecurityService.requireAll(
                Permissions.LEAVE_REQUEST, Permissions.LEAVE_CANCEL))
                .isInstanceOf(AuthorizationException.class);
    }
}
