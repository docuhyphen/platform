-- V13: Recipient-side workflow support
--
-- Seeds recipient-side trigger events and counterparty waiting support.
-- 4. Compound index on workflow_instance for fast counterparty re-evaluation.

-- ── Recipient-side trigger events ─────────────────────────────────────────────

INSERT INTO workflow_trigger_event_registry (event_name, description, subject_fields_json, is_active, created_at)
VALUES
    ('exchange.received',
     'Fired in a recipient org when an exchange is received and pending their acceptance',
     '[{"name":"exchangeId","type":"UUID","description":"UUID of the exchange"},{"name":"initiatorId","type":"UUID","description":"UUID of the initiating user"},{"name":"orgId","type":"UUID","description":"UUID of the initiating organisation"},{"name":"recipientOrgId","type":"UUID","description":"UUID of the recipient organisation"}]',
     TRUE, NOW()),
    ('exchange.received_activated',
     'Fired in a recipient org when an exchange they are part of becomes active',
     '[{"name":"exchangeId","type":"UUID","description":"UUID of the exchange"},{"name":"initiatorId","type":"UUID","description":"UUID of the initiating user"},{"name":"orgId","type":"UUID","description":"UUID of the initiating organisation"},{"name":"recipientOrgId","type":"UUID","description":"UUID of the recipient organisation"}]',
     TRUE, NOW()),
    ('exchange.received_ending',
     'Fired in a recipient org when an exchange they are part of is ending',
     '[{"name":"exchangeId","type":"UUID","description":"UUID of the exchange"},{"name":"initiatorId","type":"UUID","description":"UUID of the initiating user"},{"name":"orgId","type":"UUID","description":"UUID of the initiating organisation"},{"name":"recipientOrgId","type":"UUID","description":"UUID of the recipient organisation"}]',
     TRUE, NOW());

-- ── AWAITING_COUNTERPARTY step status ─────────────────────────────────────────

ALTER TABLE workflow_step_instance
    DROP CONSTRAINT IF EXISTS workflow_step_instance_status_check;

ALTER TABLE workflow_step_instance
    ADD CONSTRAINT workflow_step_instance_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'ESCALATED', 'SKIPPED', 'COMPLETED', 'AWAITING_COUNTERPARTY'));

-- ── WAIT_FOR_COUNTERPARTY_CLEARANCE step type ─────────────────────────────────

ALTER TABLE workflow_step_instance
    DROP CONSTRAINT IF EXISTS workflow_step_instance_step_type_check;

ALTER TABLE workflow_step_instance
    ADD CONSTRAINT workflow_step_instance_step_type_check
    CHECK (step_type IN ('APPROVAL', 'NOTIFICATION', 'CONDITION', 'ACTION', 'WAIT_FOR_COUNTERPARTY_CLEARANCE'));

-- ── Index for counterparty re-evaluation ──────────────────────────────────────

CREATE INDEX IF NOT EXISTS ix_wf_inst_subject_status
    ON workflow_instance (subject_resource_type, subject_resource_id, status);
