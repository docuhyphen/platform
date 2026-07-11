-- Stores auditor engagements and the authorization data used to search audit records.
--
-- audit_engagement grants a named auditor principal or approved principal group a time-bound,
-- explicitly scoped ability to read a subset of immutable audit evidence without inheriting
-- standing customer-content access. This is a normal mutable lifecycle table: request, approve,
-- expire, deny, and revoke all update the same row over time.
--
-- Same denormalized-ID rule as the rest of the audit platform: organization, user, group, and
-- resource references are stored as scalar IDs only, with no foreign keys to mutable business
-- tables.
CREATE TABLE audit_engagement
(
    id                   UUID PRIMARY KEY,

    organization_id      UUID,
    resource_type        VARCHAR(64),
    resource_id          VARCHAR(128),

    auditor_user_id      UUID,
    principal_group_id   UUID,
    categories_csv       TEXT         NOT NULL,
    sensitivity_level    VARCHAR(32)  NOT NULL,

    starts_at            TIMESTAMP    NOT NULL,
    expires_at           TIMESTAMP    NOT NULL,
    purpose              VARCHAR(2048) NOT NULL,
    case_reference       VARCHAR(256),
    legal_basis          VARCHAR(2048) NOT NULL,

    export_permitted     BOOLEAN      NOT NULL DEFAULT false,
    max_query_range_days INTEGER,
    download_limit       INTEGER,

    requested_by_user_id UUID         NOT NULL,
    requested_at         TIMESTAMP    NOT NULL DEFAULT now(),
    approved_by_user_id  UUID,
    approved_at          TIMESTAMP,
    denied_by_user_id    UUID,
    denied_at            TIMESTAMP,
    revoked_by_user_id   UUID,
    revoked_at           TIMESTAMP,

    status               VARCHAR(32)  NOT NULL,
    created_at           TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT audit_engagement_principal_required CHECK (auditor_user_id IS NOT NULL OR principal_group_id IS NOT NULL),
    CONSTRAINT audit_engagement_expiry_after_start CHECK (expires_at > starts_at),
    CONSTRAINT audit_engagement_status_valid CHECK (status IN ('REQUESTED', 'ACTIVE', 'EXPIRED', 'REVOKED', 'DENIED')),
    CONSTRAINT audit_engagement_sensitivity_valid CHECK (sensitivity_level IN ('METADATA_ONLY', 'STANDARD', 'SENSITIVE'))
);

CREATE INDEX idx_audit_engagement_scope ON audit_engagement (organization_id, status, starts_at, expires_at);
CREATE INDEX idx_audit_engagement_auditor_user ON audit_engagement (auditor_user_id, status, starts_at, expires_at);
CREATE INDEX idx_audit_engagement_group ON audit_engagement (principal_group_id, status, starts_at, expires_at);
CREATE INDEX idx_audit_engagement_resource ON audit_engagement (resource_type, resource_id, status);
