-- Concurrency guard: at most one step instance per position within a workflow instance.
--
-- Advancement materialises the next step at a fixed graph index frozen in the instance's
-- execution snapshot. Definition validation rejects cycles and revisits, so a given index is
-- entered at most once per instance and SLA escalation reassigns a step in place rather than
-- creating a new row. This unique index makes that invariant a database guarantee: if two
-- transactions race to advance the same completed source step, only one can commit the next-step
-- instance and the loser rolls back. Combined with the existing per-source-step transition guard
-- (uq_step_transition_from_step), every completed source step yields at most one committed
-- transition and at most one next-step instance, independent of the application-level existence
-- checks and the row locks the engine also takes.

CREATE UNIQUE INDEX uq_step_instance_index
    ON workflow_step_instance (instance_id, step_index);
