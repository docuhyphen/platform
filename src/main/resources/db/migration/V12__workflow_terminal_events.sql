SET search_path TO public;

-- ============================================================================
-- Part 1: Patch workflow definitions (fixes future flows)
-- ============================================================================
-- Add definition-level terminal events to all `exchange.acceptance_pending`
-- workflows that are missing them. Without these, emitDefinitionTerminalEvent
-- reads null and never publishes exchange.activated, leaving all recipient
-- shares permanently PENDING_APPROVAL.
UPDATE workflow_definition
SET steps_json = (
    steps_json::jsonb
    || '{"onComplete": "exchange.activated", "onReject": "session.rejected"}'::jsonb
)::text
WHERE trigger_event = 'exchange.acceptance_pending'
  AND (steps_json::jsonb ->> 'onComplete') IS NULL;


-- ============================================================================
-- Part 2: Backfill — activate shares stuck as PENDING_APPROVAL
-- ============================================================================
-- Targets shares on exchanges where every acceptance_pending workflow instance
-- has reached a terminal state (COMPLETED/REJECTED/CANCELLED) and none are
-- still RUNNING or ESCALATED (those are legitimately awaiting a decision).
UPDATE share
SET status = 'ACTIVE'
WHERE status = 'PENDING_APPROVAL'
  AND resource_type = 'EXCHANGE'
  AND resource_id NOT IN (
      SELECT wi.subject_resource_id
      FROM workflow_instance wi
      JOIN workflow_definition wd ON wd.id = wi.definition_id
      WHERE wd.trigger_event = 'exchange.acceptance_pending'
        AND wi.status IN ('RUNNING', 'ESCALATED')
        AND wi.subject_resource_id IS NOT NULL
  )
  AND resource_id IN (
      SELECT wi.subject_resource_id
      FROM workflow_instance wi
      JOIN workflow_definition wd ON wd.id = wi.definition_id
      WHERE wd.trigger_event = 'exchange.acceptance_pending'
        AND wi.status = 'COMPLETED'
        AND wi.subject_resource_id IS NOT NULL
  );


-- ============================================================================
-- Part 3: Backfill — fix exchange status INITIATED → ACCEPTED_STARTED
-- ============================================================================
-- Same predicate as Part 2. Exchanges whose acceptance workflow completed but
-- the status was never advanced because the event was never fired.
UPDATE exchange
SET status = 'ACCEPTED_STARTED'
WHERE status = 'INITIATED'
  AND id NOT IN (
      SELECT wi.subject_resource_id
      FROM workflow_instance wi
      JOIN workflow_definition wd ON wd.id = wi.definition_id
      WHERE wd.trigger_event = 'exchange.acceptance_pending'
        AND wi.status IN ('RUNNING', 'ESCALATED')
        AND wi.subject_resource_id IS NOT NULL
  )
  AND id IN (
      SELECT wi.subject_resource_id
      FROM workflow_instance wi
      JOIN workflow_definition wd ON wd.id = wi.definition_id
      WHERE wd.trigger_event = 'exchange.acceptance_pending'
        AND wi.status = 'COMPLETED'
        AND wi.subject_resource_id IS NOT NULL
  );
