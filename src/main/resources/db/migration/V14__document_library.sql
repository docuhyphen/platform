CREATE TABLE document_library (
    id                      uuid            NOT NULL,
    title                   VARCHAR(255)    NOT NULL,
    description             VARCHAR(1024),
    scope                   VARCHAR(16)     NOT NULL
        CONSTRAINT ck_doc_lib_scope CHECK (scope IN ('APP', 'ORG', 'PERSONAL')),
    organization_id         uuid            REFERENCES organization(id),
    created_by_app_user_id  uuid            REFERENCES app_user(id),
    document_type           VARCHAR(16)
        CONSTRAINT ck_doc_lib_type CHECK (document_type IN
            ('PDF','DOCX','DOC','XLSX','XLS','PPTX','PPT','PNG','JPG')),
    file_name               VARCHAR(512),
    file_size_bytes         BIGINT,
    storage_path            VARCHAR(1024),
    content_hash            VARCHAR(128),
    is_published            boolean         NOT NULL DEFAULT false,
    is_active               boolean         NOT NULL DEFAULT true,
    is_deleted              boolean         NOT NULL DEFAULT false,
    source_document_id      uuid            REFERENCES document_library(id),
    general_tags            text            NOT NULL DEFAULT '[]',
    created_at              TIMESTAMP(6)    NOT NULL,
    updated_at              TIMESTAMP(6)    NOT NULL,
    CONSTRAINT document_library_pkey PRIMARY KEY (id)
);

CREATE INDEX ix_doc_lib_org     ON document_library (organization_id)        WHERE is_deleted = false;
CREATE INDEX ix_doc_lib_creator ON document_library (created_by_app_user_id) WHERE is_deleted = false;
CREATE INDEX ix_doc_lib_scope   ON document_library (scope, is_active)       WHERE is_deleted = false;
CREATE INDEX ix_doc_lib_title   ON document_library USING gin (to_tsvector('english', title));
