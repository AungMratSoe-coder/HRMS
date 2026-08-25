-- Fix: Mark Paid failed with "Unknown column 'paid_by'" because the
-- payrolls table never had a paid_by column while the repository
-- transition SQL writes paid_at / paid_by for the PAID state.

ALTER TABLE payrolls
    ADD COLUMN paid_by BIGINT UNSIGNED NULL AFTER paid_at;

ALTER TABLE payrolls
    ADD CONSTRAINT fk_payrolls_paid_by
    FOREIGN KEY (paid_by) REFERENCES users (id) ON DELETE SET NULL;
