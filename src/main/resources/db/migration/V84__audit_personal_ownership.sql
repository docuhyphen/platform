-- Audit ownership is explicit so a missing organization is no longer overloaded to mean both
-- platform ownership and personal ownership. Existing organization_id columns remain during the
-- compatible rollout for readers that have not yet moved to owner_type and owner_id.

CREATE FUNCTION audit_owner_from_legacy_organization()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.owner_type IS NULL THEN
        IF NEW.organization_id IS NULL THEN
            NEW.owner_type := 'PLATFORM';
            NEW.owner_id := NULL;
        ELSE
            NEW.owner_type := 'ORGANIZATION';
            NEW.owner_id := NEW.organization_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE audit_outbox
    ADD COLUMN owner_type VARCHAR(32),
    ADD COLUMN owner_id UUID;

CREATE TRIGGER audit_outbox_legacy_owner
BEFORE INSERT ON audit_outbox
FOR EACH ROW EXECUTE FUNCTION audit_owner_from_legacy_organization();

ALTER TABLE audit_outbox DISABLE TRIGGER audit_outbox_deny_update;
UPDATE audit_outbox
SET owner_type = CASE WHEN organization_id IS NULL THEN 'PLATFORM' ELSE 'ORGANIZATION' END,
    owner_id = organization_id;
ALTER TABLE audit_outbox ENABLE TRIGGER audit_outbox_deny_update;

ALTER TABLE audit_outbox
    ALTER COLUMN owner_type SET NOT NULL,
    ADD CONSTRAINT ck_audit_outbox_owner CHECK (
        (owner_type = 'PLATFORM' AND owner_id IS NULL AND organization_id IS NULL)
        OR (owner_type = 'ORGANIZATION' AND owner_id IS NOT NULL AND organization_id = owner_id)
        OR (owner_type = 'USER' AND owner_id IS NOT NULL AND organization_id IS NULL)
    );

CREATE INDEX idx_audit_outbox_owner ON audit_outbox (owner_type, owner_id, recorded_at);

ALTER TABLE audit_ledger_event
    ADD COLUMN owner_type VARCHAR(32),
    ADD COLUMN owner_id UUID;

CREATE TRIGGER audit_ledger_event_legacy_owner
BEFORE INSERT ON audit_ledger_event
FOR EACH ROW EXECUTE FUNCTION audit_owner_from_legacy_organization();

ALTER TABLE audit_ledger_event DISABLE TRIGGER audit_ledger_event_deny_update;
UPDATE audit_ledger_event
SET owner_type = CASE WHEN organization_id IS NULL THEN 'PLATFORM' ELSE 'ORGANIZATION' END,
    owner_id = organization_id;
ALTER TABLE audit_ledger_event ENABLE TRIGGER audit_ledger_event_deny_update;

ALTER TABLE audit_ledger_event
    ALTER COLUMN owner_type SET NOT NULL,
    ADD CONSTRAINT ck_audit_ledger_event_owner CHECK (
        (owner_type = 'PLATFORM' AND owner_id IS NULL AND organization_id IS NULL)
        OR (owner_type = 'ORGANIZATION' AND owner_id IS NOT NULL AND organization_id = owner_id)
        OR (owner_type = 'USER' AND owner_id IS NOT NULL AND organization_id IS NULL)
    );

CREATE INDEX idx_audit_ledger_event_owner_time
    ON audit_ledger_event (owner_type, owner_id, occurred_at, event_id);

ALTER TABLE audit_analytics_fact
    ADD COLUMN owner_type VARCHAR(32),
    ADD COLUMN owner_id UUID;

CREATE TRIGGER audit_analytics_fact_legacy_owner
BEFORE INSERT ON audit_analytics_fact
FOR EACH ROW EXECUTE FUNCTION audit_owner_from_legacy_organization();

UPDATE audit_analytics_fact
SET owner_type = CASE WHEN organization_id IS NULL THEN 'PLATFORM' ELSE 'ORGANIZATION' END,
    owner_id = organization_id;

ALTER TABLE audit_analytics_fact
    ALTER COLUMN owner_type SET NOT NULL,
    ADD CONSTRAINT ck_audit_analytics_fact_owner CHECK (
        (owner_type = 'PLATFORM' AND owner_id IS NULL AND organization_id IS NULL)
        OR (owner_type = 'ORGANIZATION' AND owner_id IS NOT NULL AND organization_id = owner_id)
        OR (owner_type = 'USER' AND owner_id IS NOT NULL AND organization_id IS NULL)
    );

CREATE INDEX idx_audit_analytics_fact_owner_date
    ON audit_analytics_fact (owner_type, owner_id, occurred_date);

ALTER TABLE audit_export
    ADD COLUMN owner_type VARCHAR(32),
    ADD COLUMN owner_id UUID;

CREATE TRIGGER audit_export_legacy_owner
BEFORE INSERT ON audit_export
FOR EACH ROW EXECUTE FUNCTION audit_owner_from_legacy_organization();

UPDATE audit_export
SET owner_type = CASE WHEN organization_id IS NULL THEN 'PLATFORM' ELSE 'ORGANIZATION' END,
    owner_id = organization_id;

ALTER TABLE audit_export
    ALTER COLUMN owner_type SET NOT NULL,
    ADD CONSTRAINT ck_audit_export_owner CHECK (
        (owner_type = 'PLATFORM' AND owner_id IS NULL AND organization_id IS NULL)
        OR (owner_type = 'ORGANIZATION' AND owner_id IS NOT NULL AND organization_id = owner_id)
        OR (owner_type = 'USER' AND owner_id IS NOT NULL AND organization_id IS NULL)
    );

CREATE INDEX idx_audit_export_owner_status
    ON audit_export (owner_type, owner_id, status, requested_at);

ALTER TABLE audit_retention_policy
    ADD COLUMN owner_type VARCHAR(32),
    ADD COLUMN owner_id UUID;

CREATE TRIGGER audit_retention_policy_legacy_owner
BEFORE INSERT ON audit_retention_policy
FOR EACH ROW EXECUTE FUNCTION audit_owner_from_legacy_organization();

UPDATE audit_retention_policy
SET owner_type = 'ORGANIZATION',
    owner_id = organization_id;

ALTER TABLE audit_retention_policy
    ALTER COLUMN owner_type SET NOT NULL,
    ALTER COLUMN organization_id DROP NOT NULL,
    DROP CONSTRAINT audit_retention_policy_unique_scope,
    ADD CONSTRAINT ck_audit_retention_policy_owner CHECK (
        (owner_type = 'ORGANIZATION' AND owner_id IS NOT NULL AND organization_id = owner_id)
        OR (owner_type = 'USER' AND owner_id IS NOT NULL AND organization_id IS NULL)
    );

CREATE UNIQUE INDEX ux_audit_retention_policy_owner_category
    ON audit_retention_policy (owner_type, owner_id, category);
