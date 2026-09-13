ALTER TABLE share
    ADD COLUMN granted_by_principal_kind VARCHAR(32),
    ADD COLUMN granted_by_principal_id uuid,
    ADD COLUMN revoked_by_principal_kind VARCHAR(32),
    ADD COLUMN revoked_by_principal_id uuid;

UPDATE share
SET granted_by_principal_kind = 'USER',
    granted_by_principal_id = granted_by_app_user_id
WHERE granted_by_app_user_id IS NOT NULL;

UPDATE share
SET revoked_by_principal_kind = 'USER',
    revoked_by_principal_id = revoked_by_app_user_id
WHERE revoked_by_app_user_id IS NOT NULL;

ALTER TABLE share
    ADD CONSTRAINT ck_share_grantor_principal_kind CHECK (
        granted_by_principal_kind IS NULL
        OR granted_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                         'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
    ),
    ADD CONSTRAINT ck_share_revoker_principal_kind CHECK (
        revoked_by_principal_kind IS NULL
        OR revoked_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                         'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
    ),
    ADD CONSTRAINT ck_share_grantor_principal_pair CHECK (
        (granted_by_principal_kind IS NULL) = (granted_by_principal_id IS NULL)
    ),
    ADD CONSTRAINT ck_share_revoker_principal_pair CHECK (
        (revoked_by_principal_kind IS NULL) = (revoked_by_principal_id IS NULL)
    ),
    ADD CONSTRAINT ck_share_grantor_principal_legacy CHECK (
        granted_by_app_user_id IS NULL
        OR (
            granted_by_principal_kind = 'USER'
            AND granted_by_principal_id = granted_by_app_user_id
        )
    ),
    ADD CONSTRAINT ck_share_revoker_principal_legacy CHECK (
        revoked_by_app_user_id IS NULL
        OR (
            revoked_by_principal_kind = 'USER'
            AND revoked_by_principal_id = revoked_by_app_user_id
        )
    );

CREATE INDEX ix_share_granted_by_principal
    ON share (granted_by_principal_kind, granted_by_principal_id);

CREATE INDEX ix_share_revoked_by_principal
    ON share (revoked_by_principal_kind, revoked_by_principal_id);
