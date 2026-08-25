-- ============================================================================
--  V6: User profile picture
--  Each account may upload a profile picture ("My Profile"). The application
--  center-crops the source image to a square and stores it as a small JPEG
--  thumbnail (max 256x256, typically a few KB), so MEDIUMBLOB is generous
--  headroom. NULL means no picture - UIs fall back to an initials avatar.
-- ============================================================================

ALTER TABLE users
    ADD COLUMN avatar MEDIUMBLOB NULL COMMENT 'Profile picture as square JPEG thumbnail' AFTER employee_id;
