ALTER TABLE share_link
    ADD COLUMN contact_otp_failed_attempts INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN contact_otp_locked_until TIMESTAMP,
    ADD CONSTRAINT ck_share_link_contact_otp_failed_attempts CHECK (contact_otp_failed_attempts >= 0);
