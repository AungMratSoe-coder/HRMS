-- ============================================================================
--  V4: Separation permission (Phase 21)
--  Resignation / termination handling gets its own RBAC code so the exit
--  checklist (status change, shift close, asset return, payroll void) is
--  not reachable through generic employee-update rights.
-- ============================================================================

INSERT INTO permissions (perm_code, perm_name, module, description)
VALUES ('SEPARATION_MANAGE', 'Manage separations', 'SEPARATION',
        'Record resignations/terminations and run the exit checklist');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p
WHERE p.perm_code = 'SEPARATION_MANAGE'
  AND r.role_code IN ('SUPER_ADMIN', 'HR_MANAGER');
