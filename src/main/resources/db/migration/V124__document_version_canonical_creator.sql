-- A stored document version names its creator through one canonical principal only.
--
-- The registered-user key and the free-text email let a creator be stated a second way. A version
-- that still names its creator only through the key is carried to the same registered user as a
-- canonical principal. The platform has no production data, so a version whose creator could only
-- be stated by an email, or not at all, is removed rather than kept with an invented principal.

UPDATE document_version
SET created_by_principal_kind = 'USER',
    created_by_principal_id   = created_by
WHERE created_by_principal_kind IS NULL
  AND created_by IS NOT NULL;

DELETE
FROM document_version
WHERE created_by_principal_kind IS NULL;

ALTER TABLE document_version
    DROP CONSTRAINT ck_document_version_creator_principal_legacy,
    DROP CONSTRAINT ck_document_version_creator_principal_pair,
    DROP CONSTRAINT fk_document_version_creator,
    DROP COLUMN created_by,
    DROP COLUMN createdbyemail,
    ALTER COLUMN created_by_principal_kind SET NOT NULL,
    ALTER COLUMN created_by_principal_id SET NOT NULL;
