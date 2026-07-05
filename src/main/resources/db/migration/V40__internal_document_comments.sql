ALTER TABLE document_comment
    ADD COLUMN internal_organization_id UUID NULL;

ALTER TABLE document_comment
    ADD CONSTRAINT fk_document_comment_internal_organization
        FOREIGN KEY (internal_organization_id) REFERENCES organization (id);

CREATE INDEX idx_document_comment_internal_organization
    ON document_comment (internal_organization_id);
