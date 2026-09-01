package com.ams.hrms.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;

import com.ams.hrms.security.Permissions;
import com.ams.hrms.security.SessionContext;
import com.ams.hrms.security.SessionContext.AuthenticatedUser;
import com.ams.hrms.security.SessionContext.RoleRef;

/**
 * Shared support for service-layer unit tests (no DB, no Swing): a signed-in
 * session helper and an audit fake that records entries in memory instead of
 * touching {@code audit_logs}.
 */
abstract class ServiceTestSupport {

    /** Captures every audited action for assertions. */
    static final class RecordingAudit extends AuditService {

        record Entry(String action, String module, String entity, Long entityId,
                     String description) {
        }

        final List<Entry> entries = new ArrayList<>();

        RecordingAudit() {
            super(null);
        }

        @Override
        public void record(String action, String module, String entity,
                           Long entityId, String description) {
            entries.add(new Entry(action, module, entity, entityId, description));
        }

        Entry last() {
            return entries.isEmpty() ? null : entries.get(entries.size() - 1);
        }
    }

    private static final AuthenticatedUser TEST_USER =
            new AuthenticatedUser(7L, "tester", "Test User", "tester@local", null, null, false);

    @AfterEach
    void tearDownSession() {
        SessionContext.clear();
    }

    /** Signs in a test user holding exactly the given permissions. */
    static void loginAs(String... permissionCodes) {
        SessionContext.login(TEST_USER,
                Set.of(new RoleRef("TEST_ROLE", "Test Role")),
                Set.of(permissionCodes));
    }

    static boolean hasPermission(Permissions permission) {
        return SessionContext.has(permission);
    }
}
