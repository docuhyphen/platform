-- Phase 6 (Verifiable Evidence Exports) of AUDIT-ARCHITECTURE-IMPLEMENTATION.md.
--
-- audit_export is a normal mutable lifecycle table (request -> approve -> build -> ready ->
-- download/expire/revoke), the same pattern as audit_engagement: it tracks the state machine and
-- denormalized scope/reference fields, never the WORM evidence itself. The evidence is the signed
-- bundle object (manifest.json/events.jsonl/events.csv/integrity.json/signature/README, zipped)
-- uploaded via AuditArchiveStorage, reusing the same archive bucket/local directory as Phase 4 -
-- no new AWS service.
--
-- audit_export_approval is a separate append-style table (rows are never updated, only inserted)
-- recording each dual-control approval independently of the export row itself, so the number and
-- identity of approvers is directly queryable/auditable rather than folded into a single column.
--
-- Same denormalized-ID rule as every other audit table: organization/user references are scalar
-- IDs only, no foreign keys to mutable business tables.
CREATE TABLE audit_export
(
    id                    UUID PRIMARY KEY,

    organization_id       UUID,
    requested_by_user_id  UUID         NOT NULL,
    requested_at          TIMESTAMP    NOT NULL DEFAULT now(),

    categories_csv        TEXT         NOT NULL,
    occurred_after         TIMESTAMP    NOT NULL,
    occurred_before        TIMESTAMP    NOT NULL,
    purpose               VARCHAR(2048) NOT NULL,
    case_reference        VARCHAR(256),
    legal_basis           VARCHAR(2048),

    status                VARCHAR(32)  NOT NULL,

    required_approvals    INTEGER      NOT NULL DEFAULT 0,
    approval_count        INTEGER      NOT NULL DEFAULT 0,

    built_at              TIMESTAMP,
    ready_at              TIMESTAMP,
    expires_at            TIMESTAMP,
    failed_at             TIMESTAMP,
    failure_reason        VARCHAR(2048),
    revoked_by_user_id    UUID,
    revoked_at            TIMESTAMP,

    download_count        INTEGER      NOT NULL DEFAULT 0,
    download_limit        INTEGER,

    event_count           INTEGER,
    bundle_object_key      VARCHAR(512),
    bundle_digest          VARCHAR(128),
    signing_key_id        VARCHAR(64),

    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT audit_export_range_valid CHECK (occurred_before > occurred_after),
    CONSTRAINT audit_export_status_valid CHECK (
        status IN ('REQUESTED', 'APPROVAL_PENDING', 'BUILDING', 'READY', 'EXPIRED', 'FAILED', 'REVOKED')
    )
);

CREATE INDEX idx_audit_export_scope ON audit_export (organization_id, status);
CREATE INDEX idx_audit_export_requester ON audit_export (requested_by_user_id, status);
CREATE INDEX idx_audit_export_status_pending ON audit_export (status);

CREATE TABLE audit_export_approval
(
    id                  UUID PRIMARY KEY,
    export_id           UUID         NOT NULL,
    approved_by_user_id UUID         NOT NULL,
    approved_at         TIMESTAMP    NOT NULL DEFAULT now(),
    note                VARCHAR(1024),

    CONSTRAINT audit_export_approval_unique_approver UNIQUE (export_id, approved_by_user_id)
);

CREATE INDEX idx_audit_export_approval_export ON audit_export_approval (export_id);
