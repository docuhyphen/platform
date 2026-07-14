ALTER TABLE mfa_record
    DROP CONSTRAINT IF EXISTS mfa_record_mfa_type_check;

ALTER TABLE mfa_record
    ADD CONSTRAINT mfa_record_mfa_type_check
        CHECK (mfa_type IN (
            'SMS',
            'EMAIL',
            'GOOGLE_AUTHENTICATOR',
            'MICROSOFT_AUTHENTICATOR',
            'PASSKEY',
            'PASSWORD_RESET'
        ));
