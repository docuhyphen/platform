-- A stored document version names its content through one typed locator only.
--
-- The local path column and the local filesystem locator kind let a version be found through a
-- second shape. The platform has no production data, so a version that can only be located that way
-- is removed rather than kept readable through a fallback, and the path column is dropped.

DELETE
FROM document_version
WHERE storage_locator IS NULL
   OR storage_provider <> 'OBJECT_STORE'
   OR storage_locator_kind <> 'OBJECT_KEY';

ALTER TABLE document_version
    DROP CONSTRAINT ck_document_version_storage_locator_stated,
    DROP CONSTRAINT ck_document_version_storage_locator_provider_kind,
    DROP COLUMN storage_path,
    ALTER COLUMN storage_provider SET NOT NULL,
    ALTER COLUMN storage_locator_kind SET NOT NULL,
    ALTER COLUMN storage_locator SET NOT NULL,
    ADD CONSTRAINT ck_document_version_storage_locator_provider_kind CHECK (
        storage_provider = 'OBJECT_STORE' AND storage_locator_kind = 'OBJECT_KEY'
        );
