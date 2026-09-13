-- Adds the authority instrument fields the minimal V98 fact table deferred: grantor identity, an
-- authority instrument or evidence reference, an explicit effective/expiry window, and an explicit
-- revocation record distinct from the active flag. The table has no rows in any environment (the
-- feature has no frontend surface yet), so NOT NULL columns need no default or backfill.

ALTER TABLE information_request_delegated_authority
    ADD COLUMN grantor_principal_kind VARCHAR(32) NOT NULL,
    ADD COLUMN grantor_principal_id uuid NOT NULL,
    ADD COLUMN authority_instrument_ref VARCHAR(512),
    ADD COLUMN effective_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN revoked_at TIMESTAMPTZ,
    ADD COLUMN revoked_by_principal_kind VARCHAR(32),
    ADD COLUMN revoked_by_principal_id uuid,
    ADD COLUMN revocation_reason VARCHAR(512);

ALTER TABLE information_request_delegated_authority
    ALTER COLUMN effective_at DROP DEFAULT;

ALTER TABLE information_request_delegated_authority
    ADD CONSTRAINT ck_information_request_delegated_authority_expiry
        CHECK (expires_at IS NULL OR expires_at > effective_at);

ALTER TABLE information_request_delegated_authority
    ADD CONSTRAINT ck_information_request_delegated_authority_revocation
        CHECK (
            (active = TRUE AND revoked_at IS NULL AND revoked_by_principal_kind IS NULL
                AND revoked_by_principal_id IS NULL)
            OR
            (active = FALSE AND revoked_at IS NOT NULL AND revoked_by_principal_kind IS NOT NULL
                AND revoked_by_principal_id IS NOT NULL)
        );
