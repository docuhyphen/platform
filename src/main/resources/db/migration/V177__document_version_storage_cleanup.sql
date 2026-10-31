CREATE TABLE document_version_storage_cleanup
(
    id              UUID PRIMARY KEY,
    locator_kind    VARCHAR(32)   NOT NULL,
    locator         VARCHAR(1024) NOT NULL,
    created_at      TIMESTAMPTZ   NOT NULL,
    attempt_count   INTEGER       NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_document_version_storage_cleanup_locator UNIQUE (locator),
    CONSTRAINT ck_document_version_storage_cleanup_locator_kind CHECK (locator_kind = 'OBJECT_KEY'),
    CONSTRAINT ck_document_version_storage_cleanup_locator CHECK (BTRIM(locator) <> ''),
    CONSTRAINT ck_document_version_storage_cleanup_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX ix_document_version_storage_cleanup_due
    ON document_version_storage_cleanup (next_attempt_at);
