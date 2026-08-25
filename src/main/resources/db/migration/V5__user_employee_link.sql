-- ============================================================================
--  V5: User <-> employee self-service link
--  Each login account may point at the employee record it belongs to so the
--  signed-in user can open their own profile ("My Profile") without broad
--  employee-directory rights. Existing accounts are backfilled by email
--  match; accounts without a match simply have no employee shortcut.
-- ============================================================================

ALTER TABLE users
    ADD COLUMN employee_id BIGINT UNSIGNED NULL COMMENT 'Employee record owned by this login' AFTER phone,
    ADD KEY idx_users_employee (employee_id),
    ADD CONSTRAINT fk_users_employee FOREIGN KEY (employee_id) REFERENCES employees (id)
        ON DELETE SET NULL;

-- Best-effort backfill: link each account to the oldest employee sharing its
-- email (MIN keeps the result deterministic when emails are duplicated).
UPDATE users u
SET u.employee_id = (
    SELECT MIN(e.id)
    FROM employees e
    WHERE e.email IS NOT NULL AND LOWER(e.email) = LOWER(u.email)
)
WHERE u.email IS NOT NULL;
