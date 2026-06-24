-- Normalize the workflow_step_instance JSON blobs assignees_snapshot_json and
-- decisions_json into proper child tables.
--
-- assignees_snapshot_json was a mutable JSON array queried in SQL via a fragile
-- LIKE '%"id":"<uuid>"%' substring match and scanned in app memory for the per-user
-- inbox. decisions_json was a read-modify-write JSON array with no DB-level guard
-- against duplicate votes. Both are now indexed relational rows.

CREATE TABLE workflow_step_assignee (
    id                  uuid         NOT NULL,
    step_instance_id    uuid         NOT NULL REFERENCES workflow_step_instance(id) ON DELETE CASCADE,
    principal_kind      VARCHAR(32)  NOT NULL,
    principal_id        uuid         NOT NULL,
    CONSTRAINT workflow_step_assignee_pkey PRIMARY KEY (id),
    CONSTRAINT uq_step_assignee UNIQUE (step_instance_id, principal_kind, principal_id)
);

CREATE INDEX ix_step_assignee_principal ON workflow_step_assignee (principal_kind, principal_id);
CREATE INDEX ix_step_assignee_step      ON workflow_step_assignee (step_instance_id);

CREATE TABLE workflow_step_decision (
    id                  uuid         NOT NULL,
    step_instance_id    uuid         NOT NULL REFERENCES workflow_step_instance(id) ON DELETE CASCADE,
    principal_kind      VARCHAR(32)  NOT NULL,
    principal_id        uuid         NOT NULL,
    decision            VARCHAR(16)  NOT NULL,
    reason              text,
    decided_at          TIMESTAMP(6) NOT NULL,
    CONSTRAINT workflow_step_decision_pkey PRIMARY KEY (id),
    CONSTRAINT uq_step_decision UNIQUE (step_instance_id, principal_kind, principal_id)
);

CREATE INDEX ix_step_decision_step ON workflow_step_decision (step_instance_id);

ALTER TABLE workflow_step_instance
    DROP COLUMN assignees_snapshot_json,
    DROP COLUMN decisions_json;
