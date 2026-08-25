-- ============================================================================
--  V3: Recruitment candidate hire details (Phase 16)
--  Hiring converts a candidate into an employee; the employee record needs
--  gender and date of birth, so candidates carry them from registration.
-- ============================================================================

ALTER TABLE candidates
    ADD COLUMN gender VARCHAR(10) NULL AFTER last_name,
    ADD COLUMN date_of_birth DATE NULL AFTER gender,
    ADD CONSTRAINT chk_candidates_gender
        CHECK (gender IS NULL OR gender IN ('MALE', 'FEMALE', 'OTHER'));
