ALTER TABLE share_link
    ADD COLUMN contact_otp_challenge_count INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_share_link_contact_otp_challenge_count CHECK (contact_otp_challenge_count >= 0);
