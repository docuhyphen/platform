-- Stores the graph snapshot used by the React Flow workflow preview.
--
-- 1. definition_snapshot_json: the exact raw steps_json used when the instance
--    started. The Exchange diagram reproduces the same topology and labels the
--    builder showed (including the frontend-only step `name`, which per-step
--    spec_snapshot_json drops), regardless of later definition edits or version
--    bumps. Pre-release: no backfill (Decision 19); the engine sets this on every
--    new instance.
--
-- 2. workflow_step_transition: explicit traversed-edge history. The frontend
--    renders traversed edges ONLY from these rows, never inferred from step order,
--    status, decisions, or timestamps. A START transition (from_step_instance_id
--    NULL) records the entry edge into the first step; every subsequent row records
--    the edge taken when a concrete step instance advanced or terminated.

ALTER TABLE workflow_instance
    ADD COLUMN definition_snapshot_json text;

CREATE TABLE workflow_step_transition (
    id                    uuid         NOT NULL,
    instance_id           uuid         NOT NULL REFERENCES workflow_instance(id) ON DELETE CASCADE,
    from_step_instance_id uuid         REFERENCES workflow_step_instance(id) ON DELETE CASCADE,
    from_step_index       integer,
    to_step_index         integer,
    outcome               VARCHAR(16)  NOT NULL,
    recorded_at           TIMESTAMP(6) NOT NULL,
    CONSTRAINT workflow_step_transition_pkey PRIMARY KEY (id),
    CONSTRAINT workflow_step_transition_outcome_check
        CHECK (outcome IN ('DEFAULT', 'APPROVE', 'REJECT', 'TRUE', 'FALSE'))
);

CREATE INDEX ix_step_transition_instance ON workflow_step_transition (instance_id);

-- One transition per source step instance: a step advances/terminates exactly once,
-- so repeated scheduler ticks or retries cannot duplicate its edge.
CREATE UNIQUE INDEX uq_step_transition_from_step
    ON workflow_step_transition (from_step_instance_id)
    WHERE from_step_instance_id IS NOT NULL;

-- Exactly one START edge per instance.
CREATE UNIQUE INDEX uq_step_transition_start
    ON workflow_step_transition (instance_id)
    WHERE from_step_instance_id IS NULL;
