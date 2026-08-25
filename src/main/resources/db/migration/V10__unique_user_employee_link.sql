-- ============================================================================
--  V10: One owner per employee record
--  users.employee_id is the self-service identity link ("this login owns
--  this employee record"). Two accounts pointing at the same employee would
--  both pass the isOwnRecord check and see that person's payslips and
--  documents as their own. The service layer already refuses double links;
--  this constraint makes it impossible, including under races.
--  Replaces the plain lookup index from V5 (the unique index serves the FK).
-- ============================================================================

ALTER TABLE users
    DROP KEY idx_users_employee,
    ADD UNIQUE KEY uq_users_employee (employee_id);
