CREATE TABLE information_request_document_placeholder
(
    id                                   uuid        NOT NULL,
    information_request_id               uuid        NOT NULL,
    source_blueprint_document_default_id uuid        NOT NULL,
    title                                TEXT        NOT NULL,
    restricted_type                      VARCHAR(16),
    restrict_type                        BOOLEAN     NOT NULL DEFAULT FALSE,
    required                             BOOLEAN     NOT NULL DEFAULT FALSE,
    library_document_id                  uuid REFERENCES document_library (id) ON DELETE SET NULL,
    library_title                        TEXT,
    library_description                  VARCHAR(1024),
    library_document_type                VARCHAR(64),
    library_file_name                    VARCHAR(512),
    library_file_size_bytes              BIGINT,
    library_content_hash                 VARCHAR(128),
    display_order                        INTEGER     NOT NULL DEFAULT 0,
    created_at                           TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_document_placeholder_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_document_placeholder_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_document_placeholder_default_fkey
        FOREIGN KEY (source_blueprint_document_default_id) REFERENCES blueprint_document_default (id),
    CONSTRAINT ck_information_request_document_placeholder_title CHECK (BTRIM(title) <> ''),
    CONSTRAINT ck_information_request_document_placeholder_size CHECK (
        library_file_size_bytes IS NULL OR library_file_size_bytes >= 0
        )
);

CREATE INDEX ix_information_request_document_placeholder_request
    ON information_request_document_placeholder (information_request_id, display_order, id);
