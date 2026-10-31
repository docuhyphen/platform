ALTER TABLE share_link
DROP
COLUMN password_hash,
    DROP
COLUMN domain_allowlist,
    ADD COLUMN verification_strength VARCHAR(32),
    ADD COLUMN last_used_at TIMESTAMPTZ,
    ADD COLUMN revoked_at TIMESTAMPTZ;

UPDATE share_link
SET verification_strength = 'EMAIL_OTP'
WHERE link_mode = 'VERIFICATION_BOOTSTRAP';

ALTER TABLE share_link
    ADD CONSTRAINT ck_share_link_verification_strength CHECK (
        verification_strength IS NULL OR verification_strength IN ('EMAIL_OTP')
        ),
    ADD CONSTRAINT ck_share_link_bootstrap_verification CHECK (
        link_mode <> 'VERIFICATION_BOOTSTRAP' OR verification_strength IS NOT NULL
    ),
    ADD CONSTRAINT ck_share_link_bootstrap_mfa CHECK (
        link_mode <> 'VERIFICATION_BOOTSTRAP' OR require_mfa = FALSE
    );
