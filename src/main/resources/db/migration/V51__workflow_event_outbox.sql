-- workflow_event_outbox is the durable, transactional record of workflow engine domain events
-- (step assigned, outcome, terminal, escalation, failure). A row is written in the same
-- transaction as the authoritative workflow state mutation that produced it, so the event intent
-- can never be lost if the process dies between the state commit and event routing. A scheduled
-- dispatcher claims committed rows after commit, routes them, marks them delivered, and retries
-- transient failures with bounded exponential backoff. This reuses the existing PostgreSQL service
-- and adds no new infrastructure.
--
-- Denormalized only: no foreign keys to workflow_instance or business tables, so a subject or
-- instance can change without cascading into or orphaning pending event intent.
CREATE TABLE workflow_event_outbox
(
    id               UUID PRIMARY KEY,
    event_id         UUID         NOT NULL,
    idempotency_key  VARCHAR(256) NOT NULL,
    event_type       VARCHAR(128) NOT NULL,
    organization_id  UUID,
    envelope_json    TEXT         NOT NULL,
    status           VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    attempt_count    INTEGER      NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),
    next_attempt_at  TIMESTAMP    NOT NULL DEFAULT now(),
    delivered_at     TIMESTAMP,
    last_error       VARCHAR(1024),

    CONSTRAINT workflow_event_outbox_event_id_unique UNIQUE (event_id),
    CONSTRAINT workflow_event_outbox_idempotency_key_unique UNIQUE (idempotency_key),
    CONSTRAINT workflow_event_outbox_status_check CHECK (status IN ('PENDING', 'DELIVERED', 'FAILED'))
);

-- The dispatcher's claim query filters on status + next_attempt_at and orders by next_attempt_at,
-- so index that access path. A partial index on pending rows keeps it small as delivered rows
-- accumulate.
CREATE INDEX idx_workflow_event_outbox_claim
    ON workflow_event_outbox (next_attempt_at)
    WHERE status = 'PENDING';

CREATE INDEX idx_workflow_event_outbox_organization_id ON workflow_event_outbox (organization_id);
