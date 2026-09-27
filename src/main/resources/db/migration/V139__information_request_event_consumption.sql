ALTER TABLE workflow_trigger_event_registry
    ADD COLUMN subject_resource_type VARCHAR(32),
    ADD COLUMN subject_schema_version INTEGER;

UPDATE workflow_trigger_event_registry
SET subject_resource_type  = 'EXCHANGE',
    subject_schema_version = 1;

ALTER TABLE workflow_trigger_event_registry
    ALTER COLUMN subject_resource_type SET NOT NULL,
    ALTER COLUMN subject_schema_version SET NOT NULL,
    ADD CONSTRAINT ck_workflow_trigger_event_registry_subject_type
        CHECK (subject_resource_type IN ('EXCHANGE', 'INFORMATION_REQUEST')),
    ADD CONSTRAINT ck_workflow_trigger_event_registry_schema_version
        CHECK (subject_schema_version >= 1);

INSERT INTO workflow_trigger_event_registry
    (event_name, description, subject_fields_json, is_active, created_at, subject_resource_type, subject_schema_version)
VALUES
    ('information_request.request.issue',
     'Runs when an Information Request is issued to its parties.',
     '[{"name":"requestId","type":"UUID","description":"Information Request id"},{"name":"exchangeId","type":"UUID","description":"Exchange the request belongs to"},{"name":"templateVersionId","type":"UUID","description":"Template Version the request pins"},{"name":"orgId","type":"UUID","description":"Owning organization id"},{"name":"state","type":"STRING","description":"DRAFT | ISSUED | IN_PROGRESS | CLOSED | CANCELLED | SUPERSEDED | EXPIRED"},{"name":"transitionSequence","type":"INTEGER","description":"Sequence number of the recorded transition"}]',
     TRUE, NOW(), 'INFORMATION_REQUEST', 1),
    ('information_request.request.view',
     'Runs the first time a responding party opens an issued Information Request.',
     '[{"name":"requestId","type":"UUID","description":"Information Request id"},{"name":"exchangeId","type":"UUID","description":"Exchange the request belongs to"},{"name":"templateVersionId","type":"UUID","description":"Template Version the request pins"},{"name":"orgId","type":"UUID","description":"Owning organization id"},{"name":"state","type":"STRING","description":"DRAFT | ISSUED | IN_PROGRESS | CLOSED | CANCELLED | SUPERSEDED | EXPIRED"},{"name":"transitionSequence","type":"INTEGER","description":"Sequence number of the recorded transition"}]',
     TRUE, NOW(), 'INFORMATION_REQUEST', 1),
    ('information_request.request.start',
     'Runs when a responding party first saves a response, file, confirmation, or submission.',
     '[{"name":"requestId","type":"UUID","description":"Information Request id"},{"name":"exchangeId","type":"UUID","description":"Exchange the request belongs to"},{"name":"templateVersionId","type":"UUID","description":"Template Version the request pins"},{"name":"orgId","type":"UUID","description":"Owning organization id"},{"name":"state","type":"STRING","description":"DRAFT | ISSUED | IN_PROGRESS | CLOSED | CANCELLED | SUPERSEDED | EXPIRED"},{"name":"transitionSequence","type":"INTEGER","description":"Sequence number of the recorded transition"}]',
     TRUE, NOW(), 'INFORMATION_REQUEST', 1),
    ('information_request.request.submit',
     'Runs when a Submission Package is submitted for an Information Request.',
     '[{"name":"requestId","type":"UUID","description":"Information Request id"},{"name":"exchangeId","type":"UUID","description":"Exchange the request belongs to"},{"name":"templateVersionId","type":"UUID","description":"Template Version the request pins"},{"name":"orgId","type":"UUID","description":"Owning organization id"},{"name":"state","type":"STRING","description":"DRAFT | ISSUED | IN_PROGRESS | CLOSED | CANCELLED | SUPERSEDED | EXPIRED"},{"name":"transitionSequence","type":"INTEGER","description":"Sequence number of the recorded transition"},{"name":"submissionPackageId","type":"UUID","description":"Submitted package id"},{"name":"packageNumber","type":"INTEGER","description":"Submitted package number"},{"name":"stageKey","type":"STRING","description":"Submitted stage key, empty for a whole package"}]',
     TRUE, NOW(), 'INFORMATION_REQUEST', 1),
    ('information_request.request.correction',
     'Runs when a review returns items of a Submission Package for correction.',
     '[{"name":"requestId","type":"UUID","description":"Information Request id"},{"name":"exchangeId","type":"UUID","description":"Exchange the request belongs to"},{"name":"templateVersionId","type":"UUID","description":"Template Version the request pins"},{"name":"orgId","type":"UUID","description":"Owning organization id"},{"name":"state","type":"STRING","description":"DRAFT | ISSUED | IN_PROGRESS | CLOSED | CANCELLED | SUPERSEDED | EXPIRED"},{"name":"transitionSequence","type":"INTEGER","description":"Sequence number of the recorded transition"},{"name":"submissionPackageId","type":"UUID","description":"Package returned for correction"},{"name":"reviewId","type":"UUID","description":"Review that requested changes"},{"name":"correctionId","type":"UUID","description":"Opened correction id"},{"name":"returnedRequirementCount","type":"INTEGER","description":"Number of returned Requirements"},{"name":"stageKey","type":"STRING","description":"Returned stage key, empty for a whole package"}]',
     TRUE, NOW(), 'INFORMATION_REQUEST', 1),
    ('information_request.request.close',
     'Runs when an Information Request is satisfied and closed.',
     '[{"name":"requestId","type":"UUID","description":"Information Request id"},{"name":"exchangeId","type":"UUID","description":"Exchange the request belongs to"},{"name":"templateVersionId","type":"UUID","description":"Template Version the request pins"},{"name":"orgId","type":"UUID","description":"Owning organization id"},{"name":"state","type":"STRING","description":"DRAFT | ISSUED | IN_PROGRESS | CLOSED | CANCELLED | SUPERSEDED | EXPIRED"},{"name":"transitionSequence","type":"INTEGER","description":"Sequence number of the recorded transition"},{"name":"submissionPackageId","type":"UUID","description":"Package that satisfied the request"}]',
     TRUE, NOW(), 'INFORMATION_REQUEST', 1),
    ('information_request.request.expire',
     'Runs when an Information Request expires.',
     '[{"name":"requestId","type":"UUID","description":"Information Request id"},{"name":"exchangeId","type":"UUID","description":"Exchange the request belongs to"},{"name":"templateVersionId","type":"UUID","description":"Template Version the request pins"},{"name":"orgId","type":"UUID","description":"Owning organization id"},{"name":"state","type":"STRING","description":"DRAFT | ISSUED | IN_PROGRESS | CLOSED | CANCELLED | SUPERSEDED | EXPIRED"},{"name":"transitionSequence","type":"INTEGER","description":"Sequence number of the recorded transition"},{"name":"reasonCode","type":"STRING","description":"Stable expiry reason code"},{"name":"clockId","type":"UUID","description":"Request clock that expired the request"},{"name":"clockKey","type":"STRING","description":"Key of that request clock"}]',
     TRUE, NOW(), 'INFORMATION_REQUEST', 1),
    ('information_request.request.cancel',
     'Runs when an Information Request is cancelled.',
     '[{"name":"requestId","type":"UUID","description":"Information Request id"},{"name":"exchangeId","type":"UUID","description":"Exchange the request belongs to"},{"name":"templateVersionId","type":"UUID","description":"Template Version the request pins"},{"name":"orgId","type":"UUID","description":"Owning organization id"},{"name":"state","type":"STRING","description":"DRAFT | ISSUED | IN_PROGRESS | CLOSED | CANCELLED | SUPERSEDED | EXPIRED"},{"name":"transitionSequence","type":"INTEGER","description":"Sequence number of the recorded transition"},{"name":"reasonCode","type":"STRING","description":"Stable cancellation reason code"}]',
     TRUE, NOW(), 'INFORMATION_REQUEST', 1),
    ('information_request.request.supersede',
     'Runs when an Information Request is superseded by another request.',
     '[{"name":"requestId","type":"UUID","description":"Information Request id"},{"name":"exchangeId","type":"UUID","description":"Exchange the request belongs to"},{"name":"templateVersionId","type":"UUID","description":"Template Version the request pins"},{"name":"orgId","type":"UUID","description":"Owning organization id"},{"name":"state","type":"STRING","description":"DRAFT | ISSUED | IN_PROGRESS | CLOSED | CANCELLED | SUPERSEDED | EXPIRED"},{"name":"transitionSequence","type":"INTEGER","description":"Sequence number of the recorded transition"},{"name":"supersededByRequestId","type":"UUID","description":"Request that supersedes this one"},{"name":"reasonCode","type":"STRING","description":"Stable supersession reason code"}]',
     TRUE, NOW(), 'INFORMATION_REQUEST', 1),
    ('information_request.request.overdue',
     'Runs once when a request clock passes its due time.',
     '[{"name":"requestId","type":"UUID","description":"Information Request id"},{"name":"exchangeId","type":"UUID","description":"Exchange the request belongs to"},{"name":"templateVersionId","type":"UUID","description":"Template Version the request pins"},{"name":"orgId","type":"UUID","description":"Owning organization id"},{"name":"state","type":"STRING","description":"DRAFT | ISSUED | IN_PROGRESS | CLOSED | CANCELLED | SUPERSEDED | EXPIRED"},{"name":"transitionSequence","type":"INTEGER","description":"Sequence number of the recorded transition"},{"name":"clockId","type":"UUID","description":"Request clock that is overdue"},{"name":"clockKey","type":"STRING","description":"Key of that request clock"},{"name":"dueAt","type":"STRING","description":"Due instant the clock passed"}]',
     TRUE, NOW(), 'INFORMATION_REQUEST', 1);

DROP TRIGGER workflow_event_outbox_legacy_owner ON workflow_event_outbox;

DROP FUNCTION domain_event_outbox_owner_from_legacy_organization();

ALTER TABLE workflow_event_outbox
    ADD COLUMN ordering_key VARCHAR(160),
    ADD COLUMN sequence_number BIGINT GENERATED ALWAYS AS IDENTITY,
    ADD CONSTRAINT ck_workflow_event_outbox_ordering_key CHECK (ordering_key IS NULL OR BTRIM(ordering_key) <> ''),
    ADD CONSTRAINT uq_workflow_event_outbox_sequence UNIQUE (sequence_number);

CREATE INDEX idx_workflow_event_outbox_ordering
    ON workflow_event_outbox (ordering_key, sequence_number)
    WHERE status = 'PENDING' AND ordering_key IS NOT NULL;

CREATE TABLE domain_event_consumption
(
    consumer_key VARCHAR(128) NOT NULL,
    event_id     uuid         NOT NULL,
    outcome      VARCHAR(16)  NOT NULL,
    detail       VARCHAR(512),
    consumed_at  TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT domain_event_consumption_pkey PRIMARY KEY (consumer_key, event_id),
    CONSTRAINT ck_domain_event_consumption_consumer CHECK (BTRIM(consumer_key) <> ''),
    CONSTRAINT ck_domain_event_consumption_outcome CHECK (outcome IN ('APPLIED', 'SKIPPED'))
);

CREATE FUNCTION domain_event_consumption_append_only_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    RAISE EXCEPTION 'domain event consumption is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER domain_event_consumption_append_only
    BEFORE UPDATE OR DELETE
    ON domain_event_consumption
    FOR EACH ROW
EXECUTE FUNCTION domain_event_consumption_append_only_guard();

ALTER TABLE information_request
    ADD COLUMN first_viewed_at TIMESTAMPTZ,
    ADD COLUMN started_at      TIMESTAMPTZ,
    ADD COLUMN expired_at      TIMESTAMPTZ,
    DROP CONSTRAINT ck_information_request_terminal_dates;

ALTER TABLE information_request
    ADD CONSTRAINT ck_information_request_terminal_dates CHECK (
        (state <> 'ISSUED' OR issued_at IS NOT NULL) AND
        (state <> 'IN_PROGRESS' OR (issued_at IS NOT NULL AND started_at IS NOT NULL)) AND
        (state <> 'CLOSED' OR closed_at IS NOT NULL) AND
        (state <> 'CANCELLED' OR cancelled_at IS NOT NULL) AND
        (state <> 'SUPERSEDED' OR superseded_at IS NOT NULL) AND
        (state <> 'EXPIRED' OR expired_at IS NOT NULL)
        );

ALTER TABLE information_request_transition
    DROP CONSTRAINT ck_information_request_transition_mutation;

ALTER TABLE information_request_transition
    ADD CONSTRAINT ck_information_request_transition_mutation CHECK (
        mutation IN ('CREATE_DRAFT', 'ISSUE', 'RECORD_FIRST_VIEW', 'START_RESPONSE', 'SAVE_RESPONSE',
                     'ATTEST_RESPONSE', 'ADMINISTER_EVIDENCE', 'SUBMIT', 'START_REVIEW',
                     'REQUEST_CORRECTION', 'CLOSE', 'AMEND', 'REASSIGN', 'CANCEL',
                     'SUPERSEDE', 'EXPIRE', 'WITHDRAW_SUBMISSION', 'CREATE_SUCCESSOR',
                     'SCHEDULE_FOLLOW_UP', 'ASSIGN_REVIEWER', 'SAVE_REVIEW_DRAFT',
                     'RECORD_REVIEW_DECISION', 'RECORD_FINDING', 'RECORD_REVIEW_COMMENT',
                     'SETTLE_REVIEW', 'PROMOTE_FACT', 'REVOKE_FACT', 'RECORD_BUSINESS_DECISION',
                     'CHANGE_COMPLETION_GATE', 'START_CLOCK', 'PAUSE_CLOCK', 'RESUME_CLOCK',
                     'EXTEND_CLOCK', 'RECORD_REMINDER', 'RECORD_OVERDUE', 'RECORD_ESCALATION')
        );

CREATE UNIQUE INDEX ux_information_request_transition_first_view
    ON information_request_transition (information_request_id)
    WHERE mutation = 'RECORD_FIRST_VIEW';

CREATE UNIQUE INDEX ux_information_request_transition_start
    ON information_request_transition (information_request_id)
    WHERE mutation = 'START_RESPONSE';
