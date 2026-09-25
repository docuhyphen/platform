-- Who granted and who revoked a Share is named through the canonical principal only. The
-- registered-user columns kept beside it could only ever repeat the registered user the canonical
-- pair already names, which their consistency rules enforced, so dropping them loses nothing.

ALTER TABLE share
    DROP CONSTRAINT ck_share_grantor_principal_legacy,
    DROP CONSTRAINT ck_share_revoker_principal_legacy,
    DROP COLUMN granted_by_app_user_id,
    DROP COLUMN revoked_by_app_user_id;
