ALTER TABLE document_version
    ADD COLUMN storage_provider     VARCHAR(32),
    ADD COLUMN storage_locator_kind VARCHAR(32),
    ADD COLUMN storage_locator      VARCHAR(1024),
    ADD CONSTRAINT ck_document_version_storage_locator_stated CHECK (
        (storage_provider IS NULL AND storage_locator_kind IS NULL AND storage_locator IS NULL) OR
        (storage_provider IS NOT NULL AND storage_locator_kind IS NOT NULL AND storage_locator IS NOT NULL)
        ),
    ADD CONSTRAINT ck_document_version_storage_locator_provider_kind CHECK (
        storage_provider IS NULL OR storage_locator_kind IS NULL OR
        (storage_provider = 'LOCAL_FILESYSTEM' AND storage_locator_kind = 'LEGACY_LOCAL_PATH') OR
        (storage_provider = 'OBJECT_STORE' AND storage_locator_kind = 'OBJECT_KEY')
        ),
    ADD CONSTRAINT ck_document_version_storage_locator_value CHECK (
        storage_locator IS NULL OR BTRIM(storage_locator) <> ''
        );
