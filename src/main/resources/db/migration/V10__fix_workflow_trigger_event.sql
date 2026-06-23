-- V10: Fix seeded workflow trigger event (session.approval_requested → exchange.acceptance_pending)
--
-- The session-approval-in-group workflow was seeded with the pre-rename trigger name
-- 'session.approval_requested', but ExchangeInitiationService fires 'exchange.acceptance_pending'.
-- This mismatch meant findActiveForTrigger always returned null, so no WorkflowInstance was
-- ever created and approval steps never appeared.
SET search_path TO public;

UPDATE workflow_definition
SET trigger_event = 'exchange.acceptance_pending'
WHERE id = '00000000-0000-0000-0000-000000000001'
  AND trigger_event = 'session.approval_requested';
