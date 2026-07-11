-- audit_ledger_event is the ordered, hash-chained, per-stream ledger that LedgerProcessor builds
-- from committed audit_outbox rows. Same denormalized-ID rule as audit_outbox/access_audit_log:
-- no foreign keys to business tables, so a business entity can be deleted/changed without
-- cascading into or orphaning ledger history.
--
-- stream_head tracks, per stream, the last assigned sequence number and hash so LedgerProcessor
-- can atomically extend the chain (via a row-level PESSIMISTIC_WRITE lock on the stream_head row)
-- without two concurrent appenders forking or reordering the same stream.
CREATE TABLE stream_head
(
    stream_id     VARCHAR(128) PRIMARY KEY,
    last_sequence BIGINT       NOT NULL DEFAULT 0,
    last_hash     VARCHAR(128),
    updated_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE audit_ledger_event
(
    id                 UUID PRIMARY KEY,

    -- Stable identity, denormalized from audit_outbox.event_id (no FK - see header note).
    event_id           UUID         NOT NULL,
    event_type_key     VARCHAR(128) NOT NULL,
    category           VARCHAR(32)  NOT NULL,
    outcome            VARCHAR(32)  NOT NULL,
    schema_version     INTEGER      NOT NULL,

    -- Canonical UTC timestamps: when the business event occurred, when AuditRecorder durably
    -- accepted it, and when LedgerProcessor appended it to the ledger.
    occurred_at        TIMESTAMP    NOT NULL,
    recorded_at        TIMESTAMP    NOT NULL,
    ledger_time        TIMESTAMP    NOT NULL DEFAULT now(),

    -- Ordering: one strictly increasing sequence per stream, assigned via stream_head.
    stream_id          VARCHAR(128) NOT NULL,
    stream_sequence    BIGINT       NOT NULL,

    -- Actor / session / correlation context (denormalized, no FK).
    actor_kind         VARCHAR(32)  NOT NULL,
    actor_id           UUID,
    actor_role         VARCHAR(64),
    session_id         VARCHAR(128),
    server_trace_id    VARCHAR(64),
    correlation_id     VARCHAR(64),
    causation_id       VARCHAR(64),

    -- Owner scope + primary resource reference, denormalized IDs/labels only.
    organization_id    UUID,
    target_type        VARCHAR(64),
    target_id          VARCHAR(128),

    reason             VARCHAR(2048),
    payload_json       TEXT         NOT NULL,

    -- Tamper-evidence chain.
    prev_hash          VARCHAR(128),
    event_hash         VARCHAR(128) NOT NULL,

    -- Populated when an archived segment is signed; nullable for ledger-only events.
    signing_key_id     VARCHAR(64),
    checkpoint_ref     VARCHAR(128),

    CONSTRAINT audit_ledger_event_event_id_unique UNIQUE (event_id),
    CONSTRAINT audit_ledger_event_stream_sequence_unique UNIQUE (stream_id, stream_sequence)
);

CREATE INDEX idx_audit_ledger_event_organization_id ON audit_ledger_event (organization_id);
CREATE INDEX idx_audit_ledger_event_target ON audit_ledger_event (target_type, target_id);
CREATE INDEX idx_audit_ledger_event_event_type_key ON audit_ledger_event (event_type_key);
CREATE INDEX idx_audit_ledger_event_recorded_at ON audit_ledger_event (recorded_at);
CREATE INDEX idx_audit_ledger_event_stream ON audit_ledger_event (stream_id, stream_sequence);

CREATE OR REPLACE FUNCTION audit_ledger_event_deny_mutation() RETURNS TRIGGER AS
$$
BEGIN
    RAISE EXCEPTION 'audit_ledger_event is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_ledger_event_deny_update
    BEFORE UPDATE
    ON audit_ledger_event
    FOR EACH ROW
EXECUTE FUNCTION audit_ledger_event_deny_mutation();

CREATE TRIGGER audit_ledger_event_deny_delete
    BEFORE DELETE
    ON audit_ledger_event
    FOR EACH ROW
EXECUTE FUNCTION audit_ledger_event_deny_mutation();
