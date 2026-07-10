-- Adds the human-readable label columns the audit architecture always called for
-- ("denormalized IDs+labels") but that never made it into V41/V42's column list. These are
-- plain nullable strings captured once, at event time, alongside the existing id columns -
-- never a foreign key to the mutable business row they describe, so a later rename or deletion
-- of that row never changes or breaks historical audit rendering.
ALTER TABLE audit_outbox
    ADD COLUMN actor_label VARCHAR(256),
    ADD COLUMN target_label VARCHAR(256),
    ADD COLUMN organization_label VARCHAR(256);

ALTER TABLE audit_ledger_event
    ADD COLUMN actor_label VARCHAR(256),
    ADD COLUMN target_label VARCHAR(256),
    ADD COLUMN organization_label VARCHAR(256);
