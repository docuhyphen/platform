ALTER TABLE information_request_imported_value
    ADD COLUMN requirement_revision_id uuid;

ALTER TABLE information_request_imported_value DISABLE TRIGGER information_request_imported_value_append_only;

UPDATE information_request_imported_value imported
SET requirement_revision_id = COALESCE(
        (SELECT revision.id
         FROM information_request_requirement_revision revision
         WHERE revision.information_request_requirement_id = imported.information_request_requirement_id
           AND revision.effective_from <= imported.recorded_at
         ORDER BY revision.revision_number DESC
        LIMIT 1),
        (SELECT revision.id
         FROM information_request_requirement_revision revision
         WHERE revision.information_request_requirement_id = imported.information_request_requirement_id
         ORDER BY revision.revision_number
         LIMIT 1));

ALTER TABLE information_request_imported_value ENABLE TRIGGER information_request_imported_value_append_only;

ALTER TABLE information_request_imported_value
    ALTER COLUMN requirement_revision_id SET NOT NULL,
    ADD CONSTRAINT information_request_imported_value_revision_fkey
        FOREIGN KEY (requirement_revision_id) REFERENCES information_request_requirement_revision (id);

CREATE
OR REPLACE FUNCTION information_request_imported_value_scope_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF
NOT EXISTS (SELECT 1
                   FROM information_request_requirement requirement
                   WHERE requirement.id = NEW.information_request_requirement_id
                     AND requirement.information_request_id = NEW.information_request_id)
    THEN
        RAISE EXCEPTION 'an imported value belongs to a Requirement of its own request';
END IF;
    IF
NOT EXISTS (SELECT 1
                   FROM information_request_requirement_revision revision
                   WHERE revision.id = NEW.requirement_revision_id
                     AND revision.information_request_requirement_id = NEW.information_request_requirement_id
                     AND revision.information_request_id = NEW.information_request_id)
    THEN
        RAISE EXCEPTION 'an imported value is recorded against a revision of its own Requirement';
END IF;
    IF
NEW.connector_exchange_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM information_request_connector_exchange exchange
        WHERE exchange.id = NEW.connector_exchange_id
          AND exchange.information_request_id = NEW.information_request_id
          AND exchange.state = 'COMPLETED')
    THEN
        RAISE EXCEPTION 'a connector value comes from a completed exchange of its own request';
END IF;
RETURN NEW;
END;
$$
LANGUAGE plpgsql;

ALTER TABLE information_request_imported_value_reconciliation_item
DROP
CONSTRAINT ck_information_request_reconciliation_item_outcome;

ALTER TABLE information_request_imported_value_reconciliation_item
    ADD CONSTRAINT ck_information_request_reconciliation_item_outcome CHECK (
        outcome IN ('MATCHES', 'DIFFERS', 'NO_ANSWER', 'NOT_COMPARABLE', 'FIELD_CHANGED', 'EXPIRED')
        );
