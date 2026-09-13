ALTER TABLE information_request_template_definition
    ADD COLUMN origin_kind VARCHAR(32) NOT NULL DEFAULT 'REUSABLE';

ALTER TABLE information_request_template_definition
    ADD COLUMN origin_request_id uuid;

ALTER TABLE information_request_template_definition
    ADD CONSTRAINT ck_request_template_definition_origin_kind CHECK (
        origin_kind IN ('REUSABLE', 'AD_HOC_REQUEST')
        );

ALTER TABLE information_request_template_definition
    ADD CONSTRAINT ck_request_template_definition_origin CHECK (
        (origin_kind = 'REUSABLE' AND origin_request_id IS NULL) OR
        (origin_kind = 'AD_HOC_REQUEST' AND origin_request_id IS NOT NULL)
        );

ALTER TABLE information_request_template_definition
    ADD CONSTRAINT request_template_definition_origin_request_fkey
        FOREIGN KEY (origin_request_id)
            REFERENCES information_request (id)
            DEFERRABLE INITIALLY DEFERRED;

CREATE UNIQUE INDEX ux_request_template_definition_origin_request
    ON information_request_template_definition (origin_request_id)
    WHERE origin_request_id IS NOT NULL;

CREATE INDEX ix_request_template_definition_origin
    ON information_request_template_definition (origin_kind);
