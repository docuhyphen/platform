ALTER TABLE document_comment
    ADD COLUMN page_number INTEGER,
    ADD COLUMN document_version_id UUID;

ALTER TABLE document_comment
    ADD CONSTRAINT ck_document_comment_page_number
        CHECK (page_number IS NULL OR page_number > 0),
    ADD CONSTRAINT fk_document_comment_version
        FOREIGN KEY (document_version_id) REFERENCES document_version (id) ON DELETE SET NULL;

CREATE INDEX idx_document_comment_document_version
    ON document_comment (document_version_id);
