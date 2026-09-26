ALTER TABLE information_request
    DROP CONSTRAINT ck_information_request_state,
    DROP CONSTRAINT ck_information_request_terminal_dates;

ALTER TABLE information_request
    ADD CONSTRAINT ck_information_request_state CHECK (
        state IN ('DRAFT', 'ISSUED', 'IN_PROGRESS', 'CLOSED', 'CANCELLED', 'SUPERSEDED', 'EXPIRED')
        ),
    ADD CONSTRAINT ck_information_request_terminal_dates CHECK (
        (state <> 'ISSUED' OR issued_at IS NOT NULL) AND
        (state <> 'IN_PROGRESS' OR issued_at IS NOT NULL) AND
        (state <> 'CLOSED' OR closed_at IS NOT NULL) AND
        (state <> 'CANCELLED' OR cancelled_at IS NOT NULL) AND
        (state <> 'SUPERSEDED' OR superseded_at IS NOT NULL)
        );

ALTER TABLE information_request_transition
    DROP CONSTRAINT ck_information_request_transition_state,
    DROP CONSTRAINT ck_information_request_transition_mutation;

ALTER TABLE information_request_transition
    ADD CONSTRAINT ck_information_request_transition_state CHECK (
        (from_state IS NULL OR from_state IN ('DRAFT', 'ISSUED', 'IN_PROGRESS', 'CLOSED', 'CANCELLED',
                                             'SUPERSEDED', 'EXPIRED')) AND
        to_state IN ('DRAFT', 'ISSUED', 'IN_PROGRESS', 'CLOSED', 'CANCELLED', 'SUPERSEDED', 'EXPIRED')
        ),
    ADD CONSTRAINT ck_information_request_transition_mutation CHECK (
        mutation IN ('CREATE_DRAFT', 'ISSUE', 'RECORD_FIRST_VIEW', 'SAVE_RESPONSE',
                     'ATTEST_RESPONSE', 'ADMINISTER_EVIDENCE', 'SUBMIT', 'START_REVIEW',
                     'REQUEST_CORRECTION', 'CLOSE', 'AMEND', 'REASSIGN', 'CANCEL',
                     'SUPERSEDE', 'EXPIRE', 'WITHDRAW_SUBMISSION', 'CREATE_SUCCESSOR',
                     'SCHEDULE_FOLLOW_UP', 'ASSIGN_REVIEWER', 'SAVE_REVIEW_DRAFT',
                     'RECORD_REVIEW_DECISION', 'RECORD_FINDING', 'RECORD_REVIEW_COMMENT',
                     'SETTLE_REVIEW', 'PROMOTE_FACT', 'REVOKE_FACT', 'RECORD_BUSINESS_DECISION')
        );

CREATE TABLE information_request_review
(
    id                        uuid        NOT NULL,
    information_request_id    uuid        NOT NULL,
    package_id                uuid        NOT NULL,
    review_number             INTEGER     NOT NULL,
    kind                      VARCHAR(32) NOT NULL,
    prior_review_id           uuid,
    template_version_id       uuid        NOT NULL,
    state                     VARCHAR(32) NOT NULL,
    review_revision           BIGINT      NOT NULL,
    opening_reason            text,
    opened_by_principal_kind  VARCHAR(32) NOT NULL,
    opened_by_principal_id    uuid        NOT NULL,
    opened_at                 TIMESTAMPTZ NOT NULL,
    settled_at                TIMESTAMPTZ,
    settled_by_principal_kind VARCHAR(32),
    settled_by_principal_id   uuid,
    CONSTRAINT information_request_review_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_review_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_review_package_fkey
        FOREIGN KEY (package_id, information_request_id)
            REFERENCES information_request_submission_package (id, information_request_id),
    CONSTRAINT information_request_review_prior_fkey
        FOREIGN KEY (prior_review_id, information_request_id)
            REFERENCES information_request_review (id, information_request_id),
    CONSTRAINT information_request_review_version_fkey
        FOREIGN KEY (template_version_id) REFERENCES information_request_template_version (id),
    CONSTRAINT ux_information_request_review_number UNIQUE (information_request_id, review_number),
    CONSTRAINT uq_information_request_review_request UNIQUE (id, information_request_id),
    CONSTRAINT uq_information_request_review_package UNIQUE (id, package_id),
    CONSTRAINT ck_information_request_review_number CHECK (review_number >= 1),
    CONSTRAINT ck_information_request_review_revision CHECK (review_revision >= 1),
    CONSTRAINT ck_information_request_review_kind CHECK (
        kind IN ('INITIAL', 'RESUBMISSION', 'RECONSIDERATION', 'APPEAL')
        ),
    CONSTRAINT ck_information_request_review_prior CHECK ((kind = 'INITIAL') = (prior_review_id IS NULL)),
    CONSTRAINT ck_information_request_review_reason CHECK (
        (kind IN ('RECONSIDERATION', 'APPEAL')) = (opening_reason IS NOT NULL AND BTRIM(opening_reason) <> '')
        ),
    CONSTRAINT ck_information_request_review_state CHECK (
        state IN ('PENDING', 'IN_REVIEW', 'CHANGES_REQUESTED', 'REJECTED', 'SATISFIED',
                  'SATISFIED_WITH_EXCEPTION', 'WITHDRAWN')
        ),
    CONSTRAINT ck_information_request_review_settlement CHECK (
        (state IN ('PENDING', 'IN_REVIEW')) = (settled_at IS NULL)
            AND (settled_at IS NULL) = (settled_by_principal_kind IS NULL)
            AND (settled_at IS NULL) = (settled_by_principal_id IS NULL)
        ),
    CONSTRAINT ck_information_request_review_principals CHECK (
        opened_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                     'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
            AND (settled_by_principal_kind IS NULL OR settled_by_principal_kind IN
                                                      ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK'))
        )
);

CREATE UNIQUE INDEX ux_information_request_review_open
    ON information_request_review (package_id)
    WHERE state IN ('PENDING', 'IN_REVIEW');

CREATE INDEX ix_information_request_review_request
    ON information_request_review (information_request_id, package_id, review_number);

CREATE FUNCTION information_request_review_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    next_number     INTEGER;
    package_version uuid;
    previous_id     uuid;
    prior_package   uuid;
    prior_state     VARCHAR(32);
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'information request history is append-only';
    END IF;

    IF TG_OP = 'INSERT' THEN
        SELECT COALESCE(MAX(review_number), 0) + 1 INTO next_number
        FROM information_request_review
        WHERE information_request_id = NEW.information_request_id;

        IF NEW.review_number <> next_number THEN
            RAISE EXCEPTION 'a review numbers from one without a gap';
        END IF;

        SELECT template_version_id, previous_package_id INTO package_version, previous_id
        FROM information_request_submission_package
        WHERE id = NEW.package_id;

        IF package_version IS DISTINCT FROM NEW.template_version_id THEN
            RAISE EXCEPTION 'a review pins the template version its package froze';
        END IF;

        IF NEW.prior_review_id IS NOT NULL THEN
            SELECT package_id, state INTO prior_package, prior_state
            FROM information_request_review
            WHERE id = NEW.prior_review_id;

            IF NEW.kind = 'RESUBMISSION'
                AND (prior_package IS DISTINCT FROM previous_id OR prior_state <> 'CHANGES_REQUESTED')
            THEN
                RAISE EXCEPTION 'a resubmission review follows the review that requested changes of the package it resubmits';
            END IF;

            IF NEW.kind IN ('RECONSIDERATION', 'APPEAL')
                AND (prior_package IS DISTINCT FROM NEW.package_id
                    OR prior_state NOT IN ('REJECTED', 'CHANGES_REQUESTED'))
            THEN
                RAISE EXCEPTION 'a reconsideration or appeal follows a settled review of the same package';
            END IF;
        END IF;

        RETURN NEW;
    END IF;

    IF OLD.state NOT IN ('PENDING', 'IN_REVIEW') THEN
        RAISE EXCEPTION 'a settled review is immutable';
    END IF;

    IF NEW.id <> OLD.id
        OR NEW.information_request_id <> OLD.information_request_id
        OR NEW.package_id <> OLD.package_id
        OR NEW.review_number <> OLD.review_number
        OR NEW.kind <> OLD.kind
        OR NEW.prior_review_id IS DISTINCT FROM OLD.prior_review_id
        OR NEW.template_version_id <> OLD.template_version_id
        OR NEW.opening_reason IS DISTINCT FROM OLD.opening_reason
        OR NEW.opened_by_principal_kind <> OLD.opened_by_principal_kind
        OR NEW.opened_by_principal_id <> OLD.opened_by_principal_id
        OR NEW.opened_at <> OLD.opened_at
    THEN
        RAISE EXCEPTION 'a review keeps the identity it opened with';
    END IF;

    IF NEW.review_revision <= OLD.review_revision THEN
        RAISE EXCEPTION 'a review revision only moves forward';
    END IF;

    IF OLD.state = 'IN_REVIEW' AND NEW.state IN ('PENDING', 'WITHDRAWN') THEN
        RAISE EXCEPTION 'a review in progress cannot return to pending or be withdrawn';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_review_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_review
    FOR EACH ROW
EXECUTE FUNCTION information_request_review_guard();

CREATE TABLE information_request_review_assignment
(
    id                           uuid         NOT NULL,
    review_id                    uuid         NOT NULL,
    information_request_id       uuid         NOT NULL,
    template_review_stage_id     uuid         NOT NULL,
    stage_key                    VARCHAR(128) NOT NULL,
    reviewer_party_id            uuid         NOT NULL,
    reviewer_principal_kind      VARCHAR(32)  NOT NULL,
    reviewer_principal_id        uuid         NOT NULL,
    delegated_from_assignment_id uuid,
    due_at                       TIMESTAMPTZ,
    state                        VARCHAR(32)  NOT NULL,
    draft_revision               BIGINT       NOT NULL,
    assigned_by_principal_kind   VARCHAR(32)  NOT NULL,
    assigned_by_principal_id     uuid         NOT NULL,
    assigned_at                  TIMESTAMPTZ  NOT NULL,
    change_reason_code           VARCHAR(128),
    change_narrative             text,
    changed_by_principal_kind    VARCHAR(32),
    changed_by_principal_id      uuid,
    changed_at                   TIMESTAMPTZ,
    decided_at                   TIMESTAMPTZ,
    CONSTRAINT information_request_review_assignment_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_review_assignment_review_fkey
        FOREIGN KEY (review_id, information_request_id)
            REFERENCES information_request_review (id, information_request_id),
    CONSTRAINT information_request_review_assignment_stage_fkey
        FOREIGN KEY (template_review_stage_id) REFERENCES information_request_template_review_stage (id),
    CONSTRAINT information_request_review_assignment_party_fkey
        FOREIGN KEY (reviewer_party_id) REFERENCES information_request_party (id),
    CONSTRAINT information_request_review_assignment_delegated_fkey
        FOREIGN KEY (delegated_from_assignment_id, review_id)
            REFERENCES information_request_review_assignment (id, review_id),
    CONSTRAINT uq_information_request_review_assignment_review UNIQUE (id, review_id),
    CONSTRAINT ck_information_request_review_assignment_state CHECK (
        state IN ('ACTIVE', 'RECUSED', 'DELEGATED', 'REVOKED')
        ),
    CONSTRAINT ck_information_request_review_assignment_change CHECK (
        (state = 'ACTIVE') = (changed_at IS NULL)
            AND (changed_at IS NULL) = (changed_by_principal_kind IS NULL)
            AND (changed_at IS NULL) = (changed_by_principal_id IS NULL)
        ),
    CONSTRAINT ck_information_request_review_assignment_recusal CHECK (
        state <> 'RECUSED' OR (change_reason_code IS NOT NULL AND BTRIM(change_reason_code) <> '')
        ),
    CONSTRAINT ck_information_request_review_assignment_decided CHECK (decided_at IS NULL OR state = 'ACTIVE'),
    CONSTRAINT ck_information_request_review_assignment_revision CHECK (draft_revision >= 1),
    CONSTRAINT ck_information_request_review_assignment_principals CHECK (
        reviewer_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                    'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
            AND assigned_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                               'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE UNIQUE INDEX ux_information_request_review_assignment_active
    ON information_request_review_assignment (review_id, stage_key, reviewer_party_id)
    WHERE state = 'ACTIVE';

CREATE INDEX ix_information_request_review_assignment_reviewer
    ON information_request_review_assignment (reviewer_principal_kind, reviewer_principal_id, state);

CREATE FUNCTION information_request_review_assignment_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'information request history is append-only';
    END IF;

    IF TG_OP = 'INSERT' THEN
        IF NOT EXISTS (SELECT 1
                       FROM information_request_party party
                       WHERE party.id = NEW.reviewer_party_id
                         AND party.information_request_id = NEW.information_request_id
                         AND party.role_key = 'REVIEWER')
        THEN
            RAISE EXCEPTION 'a review assignment names a reviewer party of its request';
        END IF;

        IF NOT EXISTS (SELECT 1
                       FROM information_request_template_review_stage stage
                                JOIN information_request_review review ON review.id = NEW.review_id
                       WHERE stage.id = NEW.template_review_stage_id
                         AND stage.template_version_id = review.template_version_id
                         AND stage.stage_key = NEW.stage_key)
        THEN
            RAISE EXCEPTION 'a review assignment names a stage of its review''s template version';
        END IF;

        IF EXISTS (SELECT 1
                   FROM information_request_review review
                   WHERE review.id = NEW.review_id
                     AND review.state NOT IN ('PENDING', 'IN_REVIEW'))
        THEN
            RAISE EXCEPTION 'a settled review takes no new assignment';
        END IF;

        RETURN NEW;
    END IF;

    IF OLD.state <> 'ACTIVE' THEN
        RAISE EXCEPTION 'an assignment that has left its stage does not change again';
    END IF;

    IF NEW.id <> OLD.id
        OR NEW.review_id <> OLD.review_id
        OR NEW.information_request_id <> OLD.information_request_id
        OR NEW.template_review_stage_id <> OLD.template_review_stage_id
        OR NEW.stage_key <> OLD.stage_key
        OR NEW.reviewer_party_id <> OLD.reviewer_party_id
        OR NEW.reviewer_principal_kind <> OLD.reviewer_principal_kind
        OR NEW.reviewer_principal_id <> OLD.reviewer_principal_id
        OR NEW.delegated_from_assignment_id IS DISTINCT FROM OLD.delegated_from_assignment_id
        OR NEW.due_at IS DISTINCT FROM OLD.due_at
        OR NEW.assigned_by_principal_kind <> OLD.assigned_by_principal_kind
        OR NEW.assigned_by_principal_id <> OLD.assigned_by_principal_id
        OR NEW.assigned_at <> OLD.assigned_at
    THEN
        RAISE EXCEPTION 'a review assignment keeps the identity it was made with';
    END IF;

    IF NEW.draft_revision < OLD.draft_revision THEN
        RAISE EXCEPTION 'a worksheet revision only moves forward';
    END IF;

    IF OLD.decided_at IS NOT NULL AND (NEW.decided_at IS DISTINCT FROM OLD.decided_at OR NEW.state <> 'ACTIVE') THEN
        RAISE EXCEPTION 'an assignment that recorded its decisions keeps them';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_review_assignment_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_review_assignment
    FOR EACH ROW
EXECUTE FUNCTION information_request_review_assignment_guard();

CREATE TABLE information_request_review_draft_item
(
    id                 uuid        NOT NULL,
    assignment_id      uuid        NOT NULL,
    review_id          uuid        NOT NULL,
    submission_item_id uuid        NOT NULL,
    outcome            VARCHAR(32) NOT NULL,
    narrative          text,
    updated_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT information_request_review_draft_item_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_review_draft_item_assignment_fkey
        FOREIGN KEY (assignment_id, review_id) REFERENCES information_request_review_assignment (id, review_id),
    CONSTRAINT information_request_review_draft_item_item_fkey
        FOREIGN KEY (submission_item_id) REFERENCES information_request_submission_item (id),
    CONSTRAINT ux_information_request_review_draft_item UNIQUE (assignment_id, submission_item_id),
    CONSTRAINT ck_information_request_review_draft_item_outcome CHECK (
        outcome IN ('SATISFIED', 'SATISFIED_WITH_EXCEPTION', 'WAIVED', 'CHANGES_REQUIRED', 'REJECTED')
        )
);

CREATE FUNCTION information_request_review_draft_item_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    candidate_assignment uuid;
    candidate_review     uuid;
    candidate_item       uuid;
BEGIN
    IF TG_OP = 'DELETE' THEN
        candidate_assignment := OLD.assignment_id;
        candidate_review := OLD.review_id;
        candidate_item := OLD.submission_item_id;
    ELSE
        candidate_assignment := NEW.assignment_id;
        candidate_review := NEW.review_id;
        candidate_item := NEW.submission_item_id;
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_review_assignment assignment
                   WHERE assignment.id = candidate_assignment
                     AND assignment.state = 'ACTIVE'
                     AND assignment.decided_at IS NULL)
    THEN
        RAISE EXCEPTION 'only an active assignment that has not recorded its decisions keeps a worksheet';
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_submission_item item
                            JOIN information_request_review review ON review.package_id = item.package_id
                   WHERE item.id = candidate_item
                     AND review.id = candidate_review)
    THEN
        RAISE EXCEPTION 'a worksheet names an item of its review''s package';
    END IF;

    IF TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_review_draft_item_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_review_draft_item
    FOR EACH ROW
EXECUTE FUNCTION information_request_review_draft_item_guard();

CREATE TABLE information_request_review_decision
(
    id                        uuid         NOT NULL,
    review_id                 uuid         NOT NULL,
    information_request_id    uuid         NOT NULL,
    package_id                uuid         NOT NULL,
    submission_item_id        uuid         NOT NULL,
    requirement_id            uuid         NOT NULL,
    template_review_stage_id  uuid         NOT NULL,
    stage_key                 VARCHAR(128) NOT NULL,
    kind                      VARCHAR(32)  NOT NULL,
    assignment_id             uuid,
    carried_from_decision_id  uuid,
    outcome                   VARCHAR(32)  NOT NULL,
    narrative                 text,
    decided_by_principal_kind VARCHAR(32)  NOT NULL,
    decided_by_principal_id   uuid         NOT NULL,
    decided_at                TIMESTAMPTZ  NOT NULL,
    sequence_number           INTEGER      NOT NULL,
    CONSTRAINT information_request_review_decision_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_review_decision_review_fkey
        FOREIGN KEY (review_id, information_request_id)
            REFERENCES information_request_review (id, information_request_id),
    CONSTRAINT information_request_review_decision_package_fkey
        FOREIGN KEY (review_id, package_id) REFERENCES information_request_review (id, package_id),
    CONSTRAINT information_request_review_decision_item_fkey
        FOREIGN KEY (submission_item_id, package_id) REFERENCES information_request_submission_item (id, package_id),
    CONSTRAINT information_request_review_decision_stage_fkey
        FOREIGN KEY (template_review_stage_id) REFERENCES information_request_template_review_stage (id),
    CONSTRAINT information_request_review_decision_assignment_fkey
        FOREIGN KEY (assignment_id, review_id) REFERENCES information_request_review_assignment (id, review_id),
    CONSTRAINT information_request_review_decision_carried_fkey
        FOREIGN KEY (carried_from_decision_id) REFERENCES information_request_review_decision (id),
    CONSTRAINT ux_information_request_review_decision_sequence UNIQUE (review_id, sequence_number),
    CONSTRAINT ck_information_request_review_decision_sequence CHECK (sequence_number >= 1),
    CONSTRAINT ck_information_request_review_decision_kind CHECK (kind IN ('REVIEWER', 'OVERRIDE', 'CARRIED')),
    CONSTRAINT ck_information_request_review_decision_assignment CHECK (
        (kind = 'REVIEWER') = (assignment_id IS NOT NULL)
            AND (kind = 'CARRIED') = (carried_from_decision_id IS NOT NULL)
        ),
    CONSTRAINT ck_information_request_review_decision_outcome CHECK (
        outcome IN ('SATISFIED', 'SATISFIED_WITH_EXCEPTION', 'WAIVED', 'CHANGES_REQUIRED', 'REJECTED')
        ),
    CONSTRAINT ck_information_request_review_decision_narrative CHECK (
        (kind <> 'OVERRIDE' AND (outcome NOT IN ('SATISFIED_WITH_EXCEPTION', 'WAIVED') OR kind = 'CARRIED'))
            OR (narrative IS NOT NULL AND BTRIM(narrative) <> '')
        ),
    CONSTRAINT ck_information_request_review_decision_principal CHECK (
        decided_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                      'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE UNIQUE INDEX ux_information_request_review_decision_reviewer
    ON information_request_review_decision (assignment_id, submission_item_id)
    WHERE kind = 'REVIEWER';

CREATE UNIQUE INDEX ux_information_request_review_decision_settling
    ON information_request_review_decision (review_id, stage_key, submission_item_id, kind)
    WHERE kind IN ('OVERRIDE', 'CARRIED');

CREATE INDEX ix_information_request_review_decision_requirement
    ON information_request_review_decision (requirement_id, review_id);

CREATE FUNCTION information_request_review_decision_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    next_number INTEGER;
BEGIN
    SELECT COALESCE(MAX(sequence_number), 0) + 1 INTO next_number
    FROM information_request_review_decision
    WHERE review_id = NEW.review_id;

    IF NEW.sequence_number <> next_number THEN
        RAISE EXCEPTION 'review decisions number from one without a gap';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_review review
               WHERE review.id = NEW.review_id
                 AND review.state NOT IN ('PENDING', 'IN_REVIEW'))
    THEN
        RAISE EXCEPTION 'a settled review records no further decision';
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_submission_item item
                   WHERE item.id = NEW.submission_item_id
                     AND item.package_id = NEW.package_id
                     AND item.information_request_requirement_id = NEW.requirement_id)
    THEN
        RAISE EXCEPTION 'a review decision names an item of its review''s package';
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_template_review_stage stage
                            JOIN information_request_review review ON review.id = NEW.review_id
                   WHERE stage.id = NEW.template_review_stage_id
                     AND stage.template_version_id = review.template_version_id
                     AND stage.stage_key = NEW.stage_key)
    THEN
        RAISE EXCEPTION 'a review decision names a stage of its review''s template version';
    END IF;

    IF NEW.assignment_id IS NOT NULL AND NOT EXISTS (SELECT 1
                                                     FROM information_request_review_assignment assignment
                                                     WHERE assignment.id = NEW.assignment_id
                                                       AND assignment.stage_key = NEW.stage_key
                                                       AND assignment.state = 'ACTIVE')
    THEN
        RAISE EXCEPTION 'a reviewer decision is made through an active assignment on its stage';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_review_decision_guard_write
    BEFORE INSERT
    ON information_request_review_decision
    FOR EACH ROW
EXECUTE FUNCTION information_request_review_decision_guard();

CREATE TRIGGER information_request_review_decision_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_review_decision
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_review_finding
(
    id                         uuid         NOT NULL,
    review_id                  uuid         NOT NULL,
    information_request_id     uuid         NOT NULL,
    package_id                 uuid         NOT NULL,
    submission_item_id         uuid         NOT NULL,
    requirement_id             uuid         NOT NULL,
    evidence_version_id        uuid,
    assignment_id              uuid,
    reason_code                VARCHAR(128) NOT NULL,
    narrative                  text         NOT NULL,
    severity                   VARCHAR(32)  NOT NULL,
    visibility                 VARCHAR(32)  NOT NULL,
    correction_scope           VARCHAR(32)  NOT NULL,
    retests_finding_id         uuid,
    retest_result              VARCHAR(32),
    recorded_by_principal_kind VARCHAR(32)  NOT NULL,
    recorded_by_principal_id   uuid         NOT NULL,
    recorded_at                TIMESTAMPTZ  NOT NULL,
    sequence_number            INTEGER      NOT NULL,
    CONSTRAINT information_request_review_finding_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_review_finding_review_fkey
        FOREIGN KEY (review_id, information_request_id)
            REFERENCES information_request_review (id, information_request_id),
    CONSTRAINT information_request_review_finding_package_fkey
        FOREIGN KEY (review_id, package_id) REFERENCES information_request_review (id, package_id),
    CONSTRAINT information_request_review_finding_item_fkey
        FOREIGN KEY (submission_item_id, package_id) REFERENCES information_request_submission_item (id, package_id),
    CONSTRAINT information_request_review_finding_evidence_fkey
        FOREIGN KEY (evidence_version_id) REFERENCES information_request_evidence_version (id),
    CONSTRAINT information_request_review_finding_assignment_fkey
        FOREIGN KEY (assignment_id, review_id) REFERENCES information_request_review_assignment (id, review_id),
    CONSTRAINT information_request_review_finding_retest_fkey
        FOREIGN KEY (retests_finding_id) REFERENCES information_request_review_finding (id),
    CONSTRAINT ux_information_request_review_finding_sequence UNIQUE (review_id, sequence_number),
    CONSTRAINT uq_information_request_review_finding_review UNIQUE (id, review_id),
    CONSTRAINT ck_information_request_review_finding_sequence CHECK (sequence_number >= 1),
    CONSTRAINT ck_information_request_review_finding_reason CHECK (reason_code ~ '^[a-z0-9][a-z0-9._-]{0,127}$'),
    CONSTRAINT ck_information_request_review_finding_narrative CHECK (BTRIM(narrative) <> ''),
    CONSTRAINT ck_information_request_review_finding_severity CHECK (
        severity IN ('OBSERVATION', 'MINOR', 'MAJOR', 'CRITICAL')
        ),
    CONSTRAINT ck_information_request_review_finding_visibility CHECK (
        visibility IN ('RESPONDENT_VISIBLE', 'REVIEWERS_ONLY')
        ),
    CONSTRAINT ck_information_request_review_finding_scope CHECK (
        correction_scope IN ('NONE', 'RESPONSE', 'EVIDENCE_VERSION', 'ADDITIONAL_EVIDENCE')
        ),
    CONSTRAINT ck_information_request_review_finding_evidence CHECK (
        correction_scope <> 'EVIDENCE_VERSION' OR evidence_version_id IS NOT NULL
        ),
    CONSTRAINT ck_information_request_review_finding_retest CHECK (
        (retests_finding_id IS NULL) = (retest_result IS NULL)
            AND (retest_result IS NULL OR retest_result IN ('RESOLVED', 'UNRESOLVED'))
        ),
    CONSTRAINT ck_information_request_review_finding_principal CHECK (
        recorded_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_information_request_review_finding_item
    ON information_request_review_finding (submission_item_id, review_id);

CREATE FUNCTION information_request_review_finding_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    next_number INTEGER;
BEGIN
    SELECT COALESCE(MAX(sequence_number), 0) + 1 INTO next_number
    FROM information_request_review_finding
    WHERE review_id = NEW.review_id;

    IF NEW.sequence_number <> next_number THEN
        RAISE EXCEPTION 'review findings number from one without a gap';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_review review
               WHERE review.id = NEW.review_id
                 AND review.state NOT IN ('PENDING', 'IN_REVIEW'))
    THEN
        RAISE EXCEPTION 'a settled review records no further finding';
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_submission_item item
                   WHERE item.id = NEW.submission_item_id
                     AND item.package_id = NEW.package_id
                     AND item.information_request_requirement_id = NEW.requirement_id)
    THEN
        RAISE EXCEPTION 'a finding names an item of its review''s package';
    END IF;

    IF NEW.evidence_version_id IS NOT NULL AND NOT EXISTS (SELECT 1
                                                           FROM information_request_submission_evidence member
                                                           WHERE member.package_id = NEW.package_id
                                                             AND member.item_id = NEW.submission_item_id
                                                             AND member.evidence_version_id = NEW.evidence_version_id)
    THEN
        RAISE EXCEPTION 'a finding names an evidence version its package froze for the item';
    END IF;

    IF NEW.retests_finding_id IS NOT NULL AND NOT EXISTS (SELECT 1
                                                          FROM information_request_review_finding retested
                                                                   JOIN information_request_review review
                                                                        ON review.prior_review_id = retested.review_id
                                                          WHERE retested.id = NEW.retests_finding_id
                                                            AND review.id = NEW.review_id)
    THEN
        RAISE EXCEPTION 'a finding retests a finding of the review its review follows';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_review_finding_guard_write
    BEFORE INSERT
    ON information_request_review_finding
    FOR EACH ROW
EXECUTE FUNCTION information_request_review_finding_guard();

CREATE TRIGGER information_request_review_finding_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_review_finding
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_correction
(
    id                     uuid        NOT NULL,
    information_request_id uuid        NOT NULL,
    review_id              uuid        NOT NULL,
    package_id             uuid        NOT NULL,
    state                  VARCHAR(32) NOT NULL,
    opened_at              TIMESTAMPTZ NOT NULL,
    closed_at              TIMESTAMPTZ,
    resubmitted_package_id uuid,
    CONSTRAINT information_request_correction_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_correction_review_fkey
        FOREIGN KEY (review_id, package_id) REFERENCES information_request_review (id, package_id),
    CONSTRAINT information_request_correction_package_fkey
        FOREIGN KEY (package_id, information_request_id)
            REFERENCES information_request_submission_package (id, information_request_id),
    CONSTRAINT information_request_correction_resubmitted_fkey
        FOREIGN KEY (resubmitted_package_id, information_request_id)
            REFERENCES information_request_submission_package (id, information_request_id),
    CONSTRAINT ux_information_request_correction_review UNIQUE (review_id),
    CONSTRAINT uq_information_request_correction_request UNIQUE (id, information_request_id),
    CONSTRAINT ck_information_request_correction_state CHECK (state IN ('OPEN', 'RESUBMITTED', 'SUPERSEDED')),
    CONSTRAINT ck_information_request_correction_closure CHECK ((state = 'OPEN') = (closed_at IS NULL)),
    CONSTRAINT ck_information_request_correction_resubmission CHECK (
        (state = 'RESUBMITTED') = (resubmitted_package_id IS NOT NULL)
        )
);

CREATE UNIQUE INDEX ux_information_request_correction_open
    ON information_request_correction (package_id)
    WHERE state = 'OPEN';

CREATE FUNCTION information_request_correction_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'information request history is append-only';
    END IF;

    IF TG_OP = 'INSERT' THEN
        IF NOT EXISTS (SELECT 1
                       FROM information_request_review review
                       WHERE review.id = NEW.review_id
                         AND review.package_id = NEW.package_id
                         AND review.state = 'CHANGES_REQUESTED')
        THEN
            RAISE EXCEPTION 'a correction follows a review that requested changes';
        END IF;
        RETURN NEW;
    END IF;

    IF OLD.state <> 'OPEN' THEN
        RAISE EXCEPTION 'a closed correction is immutable';
    END IF;

    IF NEW.id <> OLD.id
        OR NEW.information_request_id <> OLD.information_request_id
        OR NEW.review_id <> OLD.review_id
        OR NEW.package_id <> OLD.package_id
        OR NEW.opened_at <> OLD.opened_at
    THEN
        RAISE EXCEPTION 'a correction keeps the identity it opened with';
    END IF;

    IF NEW.resubmitted_package_id IS NOT NULL AND NOT EXISTS (SELECT 1
                                                              FROM information_request_submission_package resubmitted
                                                              WHERE resubmitted.id = NEW.resubmitted_package_id
                                                                AND resubmitted.previous_package_id = NEW.package_id)
    THEN
        RAISE EXCEPTION 'a correction is resubmitted by a package that follows the corrected one';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_correction_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_correction
    FOR EACH ROW
EXECUTE FUNCTION information_request_correction_guard();

CREATE TABLE information_request_correction_item
(
    id                     uuid NOT NULL,
    correction_id          uuid NOT NULL,
    information_request_id uuid NOT NULL,
    submission_item_id     uuid NOT NULL,
    requirement_id         uuid NOT NULL,
    CONSTRAINT information_request_correction_item_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_correction_item_correction_fkey
        FOREIGN KEY (correction_id, information_request_id)
            REFERENCES information_request_correction (id, information_request_id),
    CONSTRAINT information_request_correction_item_item_fkey
        FOREIGN KEY (submission_item_id) REFERENCES information_request_submission_item (id),
    CONSTRAINT ux_information_request_correction_item_requirement UNIQUE (correction_id, requirement_id),
    CONSTRAINT uq_information_request_correction_item_correction UNIQUE (id, correction_id)
);

CREATE FUNCTION information_request_correction_item_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_submission_item item
                            JOIN information_request_correction correction ON correction.package_id = item.package_id
                   WHERE item.id = NEW.submission_item_id
                     AND item.information_request_requirement_id = NEW.requirement_id
                     AND correction.id = NEW.correction_id)
    THEN
        RAISE EXCEPTION 'a correction allowlists an item of its package';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_correction_item_guard_write
    BEFORE INSERT
    ON information_request_correction_item
    FOR EACH ROW
EXECUTE FUNCTION information_request_correction_item_guard();

CREATE TRIGGER information_request_correction_item_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_correction_item
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_correction_evidence
(
    id                     uuid NOT NULL,
    correction_id          uuid NOT NULL,
    correction_item_id     uuid NOT NULL,
    information_request_id uuid NOT NULL,
    evidence_artifact_id   uuid NOT NULL,
    evidence_version_id    uuid NOT NULL,
    CONSTRAINT information_request_correction_evidence_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_correction_evidence_item_fkey
        FOREIGN KEY (correction_item_id, correction_id)
            REFERENCES information_request_correction_item (id, correction_id),
    CONSTRAINT information_request_correction_evidence_artifact_fkey
        FOREIGN KEY (evidence_artifact_id) REFERENCES information_request_evidence_artifact (id),
    CONSTRAINT information_request_correction_evidence_version_fkey
        FOREIGN KEY (evidence_version_id) REFERENCES information_request_evidence_version (id),
    CONSTRAINT ux_information_request_correction_evidence_version UNIQUE (correction_id, evidence_version_id)
);

CREATE FUNCTION information_request_correction_evidence_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_correction_item corrected
                            JOIN information_request_correction correction ON correction.id = corrected.correction_id
                            JOIN information_request_submission_evidence member
                                 ON member.package_id = correction.package_id
                                     AND member.item_id = corrected.submission_item_id
                   WHERE corrected.id = NEW.correction_item_id
                     AND member.evidence_version_id = NEW.evidence_version_id
                     AND member.evidence_artifact_id = NEW.evidence_artifact_id)
    THEN
        RAISE EXCEPTION 'a correction returns only an evidence version its package froze for the item';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_correction_evidence_guard_write
    BEFORE INSERT
    ON information_request_correction_evidence
    FOR EACH ROW
EXECUTE FUNCTION information_request_correction_evidence_guard();

CREATE TRIGGER information_request_correction_evidence_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_correction_evidence
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_review_comment
(
    id                     uuid        NOT NULL,
    information_request_id uuid        NOT NULL,
    review_id              uuid        NOT NULL,
    package_id             uuid        NOT NULL,
    submission_item_id     uuid        NOT NULL,
    requirement_id         uuid        NOT NULL,
    finding_id             uuid,
    reply_to_comment_id    uuid,
    author_role            VARCHAR(32) NOT NULL,
    visibility             VARCHAR(32) NOT NULL,
    body                   text        NOT NULL,
    author_principal_kind  VARCHAR(32) NOT NULL,
    author_principal_id    uuid        NOT NULL,
    author_session_ref     VARCHAR(64),
    created_at             TIMESTAMPTZ NOT NULL,
    sequence_number        INTEGER     NOT NULL,
    CONSTRAINT information_request_review_comment_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_review_comment_review_fkey
        FOREIGN KEY (review_id, information_request_id)
            REFERENCES information_request_review (id, information_request_id),
    CONSTRAINT information_request_review_comment_package_fkey
        FOREIGN KEY (review_id, package_id) REFERENCES information_request_review (id, package_id),
    CONSTRAINT information_request_review_comment_item_fkey
        FOREIGN KEY (submission_item_id, package_id) REFERENCES information_request_submission_item (id, package_id),
    CONSTRAINT information_request_review_comment_finding_fkey
        FOREIGN KEY (finding_id, review_id) REFERENCES information_request_review_finding (id, review_id),
    CONSTRAINT information_request_review_comment_reply_fkey
        FOREIGN KEY (reply_to_comment_id) REFERENCES information_request_review_comment (id),
    CONSTRAINT ux_information_request_review_comment_sequence UNIQUE (review_id, sequence_number),
    CONSTRAINT ck_information_request_review_comment_sequence CHECK (sequence_number >= 1),
    CONSTRAINT ck_information_request_review_comment_role CHECK (
        author_role IN ('REVIEWER', 'RESPONDENT', 'ADMINISTRATOR')
        ),
    CONSTRAINT ck_information_request_review_comment_visibility CHECK (
        visibility IN ('RESPONDENT_VISIBLE', 'REVIEWERS_ONLY')
            AND (author_role <> 'RESPONDENT' OR visibility = 'RESPONDENT_VISIBLE')
        ),
    CONSTRAINT ck_information_request_review_comment_body CHECK (BTRIM(body) <> '' AND LENGTH(body) <= 4000),
    CONSTRAINT ck_information_request_review_comment_principal CHECK (
        author_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                  'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE FUNCTION information_request_review_comment_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    next_number INTEGER;
BEGIN
    SELECT COALESCE(MAX(sequence_number), 0) + 1 INTO next_number
    FROM information_request_review_comment
    WHERE review_id = NEW.review_id;

    IF NEW.sequence_number <> next_number THEN
        RAISE EXCEPTION 'review comments number from one without a gap';
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_submission_item item
                   WHERE item.id = NEW.submission_item_id
                     AND item.package_id = NEW.package_id
                     AND item.information_request_requirement_id = NEW.requirement_id)
    THEN
        RAISE EXCEPTION 'a comment names an item of its review''s package';
    END IF;

    IF NEW.finding_id IS NOT NULL AND NOT EXISTS (SELECT 1
                                                  FROM information_request_review_finding finding
                                                  WHERE finding.id = NEW.finding_id
                                                    AND finding.submission_item_id = NEW.submission_item_id)
    THEN
        RAISE EXCEPTION 'a comment answers a finding on its own item';
    END IF;

    IF NEW.reply_to_comment_id IS NOT NULL AND NOT EXISTS (SELECT 1
                                                           FROM information_request_review_comment earlier
                                                           WHERE earlier.id = NEW.reply_to_comment_id
                                                             AND earlier.review_id = NEW.review_id
                                                             AND earlier.submission_item_id = NEW.submission_item_id)
    THEN
        RAISE EXCEPTION 'a reply answers a comment on the same review item';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_review_comment_guard_write
    BEFORE INSERT
    ON information_request_review_comment
    FOR EACH ROW
EXECUTE FUNCTION information_request_review_comment_guard();

CREATE TRIGGER information_request_review_comment_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_review_comment
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_review_remediation
(
    id                       uuid        NOT NULL,
    information_request_id   uuid        NOT NULL,
    correction_id            uuid        NOT NULL,
    finding_id               uuid        NOT NULL,
    remediated_by_package_id uuid        NOT NULL,
    remediated_by_item_id    uuid        NOT NULL,
    recorded_at              TIMESTAMPTZ NOT NULL,
    CONSTRAINT information_request_review_remediation_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_review_remediation_correction_fkey
        FOREIGN KEY (correction_id, information_request_id)
            REFERENCES information_request_correction (id, information_request_id),
    CONSTRAINT information_request_review_remediation_finding_fkey
        FOREIGN KEY (finding_id) REFERENCES information_request_review_finding (id),
    CONSTRAINT information_request_review_remediation_item_fkey
        FOREIGN KEY (remediated_by_item_id, remediated_by_package_id)
            REFERENCES information_request_submission_item (id, package_id),
    CONSTRAINT ux_information_request_review_remediation UNIQUE (finding_id, remediated_by_package_id)
);

CREATE FUNCTION information_request_review_remediation_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_review_finding finding
                            JOIN information_request_correction correction ON correction.review_id = finding.review_id
                   WHERE finding.id = NEW.finding_id
                     AND correction.id = NEW.correction_id)
    THEN
        RAISE EXCEPTION 'a remediation answers a finding of the corrected review';
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_submission_package resubmitted
                            JOIN information_request_correction correction
                                 ON correction.package_id = resubmitted.previous_package_id
                   WHERE resubmitted.id = NEW.remediated_by_package_id
                     AND correction.id = NEW.correction_id)
    THEN
        RAISE EXCEPTION 'a remediation is made by a package that follows the corrected one';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_review_remediation_guard_write
    BEFORE INSERT
    ON information_request_review_remediation
    FOR EACH ROW
EXECUTE FUNCTION information_request_review_remediation_guard();

CREATE TRIGGER information_request_review_remediation_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_review_remediation
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
