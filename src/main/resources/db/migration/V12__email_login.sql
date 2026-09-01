-- ============================================================================
--  V12__email_login.sql
--  Email becomes the sign-in credential. Accounts without an email receive
--  one derived from their unique username (guaranteed unique by
--  uq_users_username), then the column is made mandatory.
-- ============================================================================

USE hrms;

UPDATE users
SET email = CONCAT(username, '@ams.local')
WHERE email IS NULL OR TRIM(email) = '';

ALTER TABLE users MODIFY email VARCHAR(150) NOT NULL;
