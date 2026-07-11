-- Frozen instance execution contract.
--
-- 1. trigger_event_snapshot: the trigger event name frozen when an instance started. The engine
--    derives terminal fallback events and the condition-step subject-field registry from this
--    frozen value instead of the later, mutable definition, so retargeting a definition to a
--    different trigger cannot change how an in-flight instance ends or evaluates conditions.
--
-- 2. failure_code / failure_detail: an instance the engine cannot execute safely (a missing or
--    corrupt execution snapshot) is marked FAILED rather than fabricated as completed. failure_code
--    is a short, safe code surfaced to administrators; failure_detail is internal operator-only
--    context and is never returned through an API response.

ALTER TABLE workflow_instance
    ADD COLUMN trigger_event_snapshot varchar(128),
    ADD COLUMN failure_code           varchar(64),
    ADD COLUMN failure_detail         text;

-- Allow the new terminal FAILED status.
ALTER TABLE workflow_instance
    DROP CONSTRAINT workflow_instance_status_check;
ALTER TABLE workflow_instance
    ADD CONSTRAINT workflow_instance_status_check
        CHECK (status IN ('RUNNING', 'COMPLETED', 'REJECTED', 'CANCELLED', 'ESCALATED', 'FAILED'));

-- Allow the new FAILED transition outcome (terminal edge recorded where execution stopped).
ALTER TABLE workflow_step_transition
    DROP CONSTRAINT workflow_step_transition_outcome_check;
ALTER TABLE workflow_step_transition
    ADD CONSTRAINT workflow_step_transition_outcome_check
        CHECK (outcome IN ('DEFAULT', 'APPROVE', 'REJECT', 'TRUE', 'FALSE', 'FAILED'));

-- Best-available backfill for still-active (RUNNING / ESCALATED) rows created before this contract:
-- freeze the trigger and, where the snapshot is missing, the current steps_json. This is a
-- compatibility measure only. It CANNOT reconstruct the definition version an older instance
-- actually started under, so a definition edited between that instance's start and this migration
-- will backfill the edited topology, not the original one. New instances always freeze both fields
-- at start and never rely on this backfill.
UPDATE workflow_instance i
SET trigger_event_snapshot = d.trigger_event
FROM workflow_definition d
WHERE i.definition_id = d.id
  AND i.trigger_event_snapshot IS NULL
  AND i.status IN ('RUNNING', 'ESCALATED');

UPDATE workflow_instance i
SET definition_snapshot_json = d.steps_json
FROM workflow_definition d
WHERE i.definition_id = d.id
  AND i.definition_snapshot_json IS NULL
  AND i.status IN ('RUNNING', 'ESCALATED');
