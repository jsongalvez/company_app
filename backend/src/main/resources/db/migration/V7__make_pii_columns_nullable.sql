-- CR-019: Make PII columns nullable for proper GDPR anonymization
-- Anonymized clients should have NULL (not empty string) for PII fields.
ALTER TABLE client ALTER COLUMN first_name DROP NOT NULL;
ALTER TABLE client ALTER COLUMN last_name DROP NOT NULL;
ALTER TABLE client ALTER COLUMN address DROP NOT NULL;
