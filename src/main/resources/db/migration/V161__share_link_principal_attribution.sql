ALTER TABLE share_link
    ADD COLUMN created_by_principal_kind VARCHAR(32),
    ADD COLUMN created_by_principal_id uuid;

UPDATE share_link
SET created_by_principal_kind = 'USER',
    created_by_principal_id   = created_by_app_user_id
WHERE created_by_app_user_id IS NOT NULL;

ALTER TABLE share_link
DROP
COLUMN created_by_app_user_id,
    ADD CONSTRAINT ck_share_link_created_principal CHECK (
        (created_by_principal_kind IS NULL AND created_by_principal_id IS NULL)
        OR (created_by_principal_kind IS NOT NULL
            AND created_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
            AND created_by_principal_id IS NOT NULL)
    );
