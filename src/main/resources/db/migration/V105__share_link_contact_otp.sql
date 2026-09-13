ALTER TABLE share_link
    ADD COLUMN contact_otp_hash VARCHAR(255),
    ADD COLUMN contact_otp_expires_at TIMESTAMP;
