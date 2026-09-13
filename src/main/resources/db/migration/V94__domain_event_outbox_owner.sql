-- The durable domain-event outbox keeps the legacy physical table name during compatible rollout,
-- but ownership is now explicit instead of overloading a missing organization to mean platform.

CREATE FUNCTION domain_event_outbox_owner_from_legacy_organization()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.owner_kind IS NULL THEN
        IF NEW.organization_id IS NULL THEN
            NEW.owner_kind := 'PLATFORM';
            NEW.owner_id := NULL;
        ELSE
            NEW.owner_kind := 'ORGANIZATION';
            NEW.owner_id := NEW.organization_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE workflow_event_outbox
    ADD COLUMN owner_kind VARCHAR(32),
    ADD COLUMN owner_id UUID;

CREATE TRIGGER workflow_event_outbox_legacy_owner
BEFORE INSERT ON workflow_event_outbox
FOR EACH ROW EXECUTE FUNCTION domain_event_outbox_owner_from_legacy_organization();

UPDATE workflow_event_outbox
SET owner_kind = CASE WHEN organization_id IS NULL THEN 'PLATFORM' ELSE 'ORGANIZATION' END,
    owner_id = organization_id;

ALTER TABLE workflow_event_outbox
    ALTER COLUMN owner_kind SET NOT NULL,
    ADD CONSTRAINT ck_workflow_event_outbox_owner CHECK (
        (owner_kind = 'PLATFORM' AND owner_id IS NULL AND organization_id IS NULL)
        OR (owner_kind = 'ORGANIZATION' AND owner_id IS NOT NULL AND organization_id = owner_id)
        OR (owner_kind = 'USER' AND owner_id IS NOT NULL AND organization_id IS NULL)
    );

CREATE INDEX idx_workflow_event_outbox_owner
    ON workflow_event_outbox (owner_kind, owner_id, created_at);
