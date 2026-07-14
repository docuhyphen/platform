ALTER TABLE app_user
    DROP CONSTRAINT IF EXISTS app_user_multifactor_authentication_type_check;

ALTER TABLE app_user
    ADD COLUMN authenticator_secret_encrypted VARCHAR(512),
    ADD COLUMN email_mfa_fallback_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE app_user
    ADD CONSTRAINT app_user_multifactor_authentication_type_check
        CHECK (multifactor_authentication_type IN (
            'SMS',
            'EMAIL',
            'GOOGLE_AUTHENTICATOR',
            'MICROSOFT_AUTHENTICATOR',
            'PASSKEY',
            'PASSWORD_RESET'
        ));

CREATE TABLE authenticator_enrollment (
    id UUID PRIMARY KEY,
    app_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    provider VARCHAR(64) NOT NULL,
    secret_encrypted VARCHAR(512) NOT NULL,
    created_date TIMESTAMP NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT authenticator_enrollment_provider_check
        CHECK (provider IN ('GOOGLE_AUTHENTICATOR', 'MICROSOFT_AUTHENTICATOR'))
);

CREATE INDEX authenticator_enrollment_user_idx
    ON authenticator_enrollment(app_user_id);
