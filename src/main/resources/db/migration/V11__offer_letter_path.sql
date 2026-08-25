-- Archived offer letters: when an offer is sent, the rendered PDF letter
-- is stored under the document store (offers/<offerId>/) and its relative
-- path recorded here so the exact letter that was sent can be reopened.

ALTER TABLE job_offers
    ADD COLUMN letter_path VARCHAR(255) NULL AFTER employee_id;
