
-- audit_outbox is the durable, immutable audit intent written by AuditRecorder in the same
-- transaction as the business change it records. It follows the AccessAuditLog denormalized-ID
-- pattern: no foreign keys to business tables, so a business entity (Exchange, document, user,
-- ...) can be deleted or changed without cascading into or orphaning audit history.
CREATE TABLE audit_outbox
(
    id                       UUID PRIMARY KEY,
    event_id                 UUID        NOT NULL,
    idempotency_key          VARCHAR(256) NOT NULL,
    event_type_key           VARCHAR(128) NOT NULL,
    category                 VARCHAR(32) NOT NULL,
    outcome                  VARCHAR(32) NOT NULL,
    actor_id                 UUID,
    actor_role               VARCHAR(64),
    target_type              VARCHAR(64),
    target_id                VARCHAR(128),
    organization_id          UUID,
    session_id               VARCHAR(128),
    reason                   VARCHAR(2048),
    payload_json             TEXT        NOT NULL,
    server_trace_id          VARCHAR(64),
    correlation_id           VARCHAR(64),
    causation_id             VARCHAR(64),
    business_transaction_id  VARCHAR(128),
    status                   VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    attempt_count            INTEGER     NOT NULL DEFAULT 0,
    occurred_at              TIMESTAMP   NOT NULL,
    recorded_at              TIMESTAMP   NOT NULL DEFAULT now(),
    catalog_version          INTEGER     NOT NULL,

    CONSTRAINT audit_outbox_event_id_unique UNIQUE (event_id),
    CONSTRAINT audit_outbox_idempotency_key_unique UNIQUE (idempotency_key)
);

CREATE INDEX idx_audit_outbox_status ON audit_outbox (status);
CREATE INDEX idx_audit_outbox_organization_id ON audit_outbox (organization_id);
CREATE INDEX idx_audit_outbox_target ON audit_outbox (target_type, target_id);
CREATE INDEX idx_audit_outbox_recorded_at ON audit_outbox (recorded_at);

CREATE OR REPLACE FUNCTION audit_outbox_deny_mutation() RETURNS TRIGGER AS
$$
BEGIN
    RAISE EXCEPTION 'audit_outbox is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_outbox_deny_update
    BEFORE UPDATE
    ON audit_outbox
    FOR EACH ROW
EXECUTE FUNCTION audit_outbox_deny_mutation();

CREATE TRIGGER audit_outbox_deny_delete
    BEFORE DELETE
    ON audit_outbox
    FOR EACH ROW
EXECUTE FUNCTION audit_outbox_deny_mutation();
