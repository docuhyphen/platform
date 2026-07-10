-- Phase 8 (Retention, Legal Hold, Analytics Projection, Assurance) of
-- AUDIT-ARCHITECTURE-IMPLEMENTATION.md.
--
-- Four new tables, all following the same denormalized-ID rule as the rest of the audit
-- platform: no foreign key from any of these tables to a mutable business entity.
--
-- audit_retention_policy holds only *organization overrides*; the platform-wide default catalog
-- (per AuditCategory) lives in application code (AuditRetentionCatalogService), not in this
-- table, so there is always a well-defined default even for organizations with no row here.
--
-- audit_legal_hold is a normal mutable lifecycle table (place -> release), the same shape as
-- audit_engagement/audit_export: it never holds evidence itself, only the fact that a named
-- resource is currently under hold, which callers must consult before any future disposal action
-- ever deletes a searchable-projection row (the WORM ledger/archive are never deleted by design).
--
-- audit_identity_vault_key is the crypto-shredding primitive: a random per-subject data key is
-- generated, wrapped (AES-GCM) with a single application-wide master key held in the existing AWS
-- Secrets Manager (no KMS), and stored here. "Crypto-shredding" a subject means overwriting
-- wrapped_key/iv with NULL and stamping shredded_at - anything that was ever encrypted with that
-- subject's data key becomes permanently unrecoverable without deleting any audit row itself.
--
-- audit_analytics_fact is a rebuildable, idempotent (unique on ledger_event_id) dimensional
-- projection of the ledger for analytics/reconciliation. It intentionally carries no payload
-- column at all, only denormalized dimensions, so it can never copy a prohibited field forward.
--
-- global_sequence on audit_ledger_event is an additive, nullable-by-default BIGSERIAL used purely
-- as an efficient cross-stream append-order cursor for the analytics projector (and any future
-- global consumer); it does not participate in the per-stream hash chain and is not touched by the
-- append-only trigger's protected columns, it is simply another column that trigger continues to
-- protect from UPDATE/DELETE like every other column on this table.
ALTER TABLE audit_ledger_event
    ADD COLUMN global_sequence BIGSERIAL;

CREATE UNIQUE INDEX idx_audit_ledger_event_global_sequence ON audit_ledger_event (global_sequence);

CREATE TABLE audit_retention_policy
(
    id                      UUID PRIMARY KEY,

    organization_id         UUID         NOT NULL,
    category                VARCHAR(32)  NOT NULL,

    ledger_retention_days   INTEGER      NOT NULL,
    archive_retention_days  INTEGER      NOT NULL,
    legal_hold_eligible     BOOLEAN      NOT NULL DEFAULT true,
    identity_treatment      VARCHAR(32)  NOT NULL DEFAULT 'READABLE',

    updated_by_user_id      UUID         NOT NULL,
    created_at              TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT audit_retention_policy_unique_scope UNIQUE (organization_id, category),
    CONSTRAINT audit_retention_policy_ledger_days_positive CHECK (ledger_retention_days > 0),
    CONSTRAINT audit_retention_policy_archive_days_positive CHECK (archive_retention_days > 0),
    CONSTRAINT audit_retention_policy_identity_treatment_valid CHECK (
        identity_treatment IN ('READABLE', 'MASKED', 'PSEUDONYMIZED', 'PROHIBITED')
    )
);

CREATE TABLE audit_legal_hold
(
    id                  UUID PRIMARY KEY,

    organization_id     UUID,
    resource_type       VARCHAR(64)  NOT NULL,
    resource_id         VARCHAR(128) NOT NULL,

    reason              VARCHAR(2048) NOT NULL,
    case_reference      VARCHAR(256),

    status              VARCHAR(32)  NOT NULL,
    placed_by_user_id   UUID         NOT NULL,
    placed_at           TIMESTAMP    NOT NULL DEFAULT now(),
    released_by_user_id UUID,
    released_at         TIMESTAMP,

    created_at          TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT audit_legal_hold_status_valid CHECK (status IN ('ACTIVE', 'RELEASED'))
);

CREATE INDEX idx_audit_legal_hold_resource ON audit_legal_hold (organization_id, resource_type, resource_id, status);

CREATE TABLE audit_identity_vault_key
(
    id           UUID PRIMARY KEY,

    subject_type VARCHAR(32)  NOT NULL,
    subject_id   VARCHAR(128) NOT NULL,

    wrapped_key  TEXT,
    iv           TEXT,

    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    shredded_at  TIMESTAMP,

    CONSTRAINT audit_identity_vault_key_unique_subject UNIQUE (subject_type, subject_id)
);

CREATE TABLE audit_analytics_fact
(
    id              UUID PRIMARY KEY,

    ledger_event_id UUID         NOT NULL UNIQUE,
    organization_id UUID,
    stream_id       VARCHAR(128) NOT NULL,
    category        VARCHAR(32)  NOT NULL,
    event_type_key  VARCHAR(128) NOT NULL,
    outcome         VARCHAR(32)  NOT NULL,
    actor_kind      VARCHAR(32)  NOT NULL,

    occurred_at     TIMESTAMP    NOT NULL,
    occurred_date   DATE         NOT NULL,
    schema_version  INTEGER      NOT NULL,

    projected_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_analytics_fact_org_date ON audit_analytics_fact (organization_id, occurred_date);
CREATE INDEX idx_audit_analytics_fact_category_date ON audit_analytics_fact (category, occurred_date);
