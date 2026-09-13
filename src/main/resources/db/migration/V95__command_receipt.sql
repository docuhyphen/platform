CREATE TABLE command_receipt
(
    id UUID PRIMARY KEY,
    resource_type VARCHAR(64) NOT NULL,
    resource_id UUID NOT NULL,
    operation_name VARCHAR(96) NOT NULL,
    actor_kind VARCHAR(32) NOT NULL,
    actor_id UUID NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    request_fingerprint_sha256 VARCHAR(128) NOT NULL,
    result_resource_type VARCHAR(64) NOT NULL,
    result_resource_id UUID NOT NULL,
    result_revision BIGINT,
    result_etag VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_command_receipt_scope_key UNIQUE
        (resource_type, resource_id, operation_name, actor_kind, actor_id, idempotency_key),
    CONSTRAINT ck_command_receipt_text_present CHECK (
        btrim(resource_type) <> ''
        AND btrim(operation_name) <> ''
        AND btrim(actor_kind) <> ''
        AND btrim(idempotency_key) <> ''
        AND btrim(request_fingerprint_sha256) <> ''
        AND btrim(result_resource_type) <> ''
    )
);

CREATE INDEX idx_command_receipt_resource
    ON command_receipt (resource_type, resource_id, created_at);

CREATE INDEX idx_command_receipt_actor
    ON command_receipt (actor_kind, actor_id, created_at);
