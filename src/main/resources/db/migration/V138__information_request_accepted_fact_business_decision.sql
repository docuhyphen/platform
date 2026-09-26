CREATE TABLE information_request_accepted_fact
(
    id                             uuid         NOT NULL,
    owner_type                     VARCHAR(32)  NOT NULL,
    owner_organization_id          uuid,
    owner_user_id                  uuid,
    subject_identity_ref_id        uuid         NOT NULL,
    purpose_key                    VARCHAR(128) NOT NULL,
    field_definition_id            uuid         NOT NULL,
    value_type                     VARCHAR(32)  NOT NULL,
    canonical_value                text         NOT NULL,
    source_information_request_id  uuid         NOT NULL,
    source_package_id              uuid         NOT NULL,
    source_submission_item_id      uuid         NOT NULL,
    source_requirement_id          uuid         NOT NULL,
    source_response_id             uuid         NOT NULL,
    source_response_revision       BIGINT       NOT NULL,
    source_field_value_revision_id uuid         NOT NULL,
    source_review_id               uuid,
    visibility                     VARCHAR(32)  NOT NULL,
    confidence                     VARCHAR(32)  NOT NULL,
    valid_from                     TIMESTAMPTZ  NOT NULL,
    valid_to                       TIMESTAMPTZ,
    expires_at                     TIMESTAMPTZ,
    supersedes_fact_id             uuid,
    conflict_state                 VARCHAR(32)  NOT NULL,
    conflicting_fact_id            uuid,
    promoted_by_principal_kind     VARCHAR(32)  NOT NULL,
    promoted_by_principal_id       uuid         NOT NULL,
    promoted_at                    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_accepted_fact_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_accepted_fact_organization_fkey
        FOREIGN KEY (owner_organization_id) REFERENCES organization (id),
    CONSTRAINT information_request_accepted_fact_user_fkey
        FOREIGN KEY (owner_user_id) REFERENCES app_user (id),
    CONSTRAINT information_request_accepted_fact_subject_fkey
        FOREIGN KEY (subject_identity_ref_id) REFERENCES subject_identity_ref (id),
    CONSTRAINT information_request_accepted_fact_field_fkey
        FOREIGN KEY (field_definition_id) REFERENCES field_definition (id),
    CONSTRAINT information_request_accepted_fact_request_fkey
        FOREIGN KEY (source_information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_accepted_fact_package_fkey
        FOREIGN KEY (source_package_id, source_information_request_id)
            REFERENCES information_request_submission_package (id, information_request_id),
    CONSTRAINT information_request_accepted_fact_item_fkey
        FOREIGN KEY (source_submission_item_id, source_package_id)
            REFERENCES information_request_submission_item (id, package_id),
    CONSTRAINT information_request_accepted_fact_response_fkey
        FOREIGN KEY (source_response_id) REFERENCES information_request_response (id),
    CONSTRAINT information_request_accepted_fact_revision_fkey
        FOREIGN KEY (source_field_value_revision_id) REFERENCES field_value_revision (id),
    CONSTRAINT information_request_accepted_fact_review_fkey
        FOREIGN KEY (source_review_id, source_package_id) REFERENCES information_request_review (id, package_id),
    CONSTRAINT information_request_accepted_fact_supersedes_fkey
        FOREIGN KEY (supersedes_fact_id) REFERENCES information_request_accepted_fact (id),
    CONSTRAINT information_request_accepted_fact_conflicting_fkey
        FOREIGN KEY (conflicting_fact_id) REFERENCES information_request_accepted_fact (id),
    CONSTRAINT ux_information_request_accepted_fact_supersedes UNIQUE (supersedes_fact_id),
    CONSTRAINT ck_information_request_accepted_fact_owner CHECK (
        (owner_type = 'ORGANIZATION' AND owner_organization_id IS NOT NULL AND owner_user_id IS NULL) OR
        (owner_type = 'USER' AND owner_organization_id IS NULL AND owner_user_id IS NOT NULL)
        ),
    CONSTRAINT ck_information_request_accepted_fact_purpose CHECK (purpose_key ~ '^[a-z0-9][a-z0-9._-]{0,127}$'),
    CONSTRAINT ck_information_request_accepted_fact_visibility CHECK (
        visibility IN ('REQUESTING_SIDE', 'RESPONDING_PARTIES')
        ),
    CONSTRAINT ck_information_request_accepted_fact_confidence CHECK (
        confidence IN ('DECLARED', 'REVIEWED')
            AND (confidence = 'REVIEWED') = (source_review_id IS NOT NULL)
        ),
    CONSTRAINT ck_information_request_accepted_fact_period CHECK (
        (valid_to IS NULL OR valid_to > valid_from) AND (expires_at IS NULL OR expires_at > promoted_at)
        ),
    CONSTRAINT ck_information_request_accepted_fact_conflict CHECK (
        conflict_state IN ('NONE', 'CONFLICTING')
            AND (conflict_state = 'CONFLICTING') = (conflicting_fact_id IS NOT NULL)
        ),
    CONSTRAINT ck_information_request_accepted_fact_principal CHECK (
        promoted_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_information_request_accepted_fact_reuse
    ON information_request_accepted_fact (owner_type, owner_organization_id, owner_user_id, subject_identity_ref_id,
                                          field_definition_id, purpose_key);

CREATE INDEX ix_information_request_accepted_fact_source
    ON information_request_accepted_fact (source_information_request_id, promoted_at);

CREATE TABLE information_request_accepted_fact_revocation
(
    id                        uuid         NOT NULL,
    fact_id                   uuid         NOT NULL,
    reason_code               VARCHAR(128) NOT NULL,
    narrative                 text,
    revoked_by_principal_kind VARCHAR(32)  NOT NULL,
    revoked_by_principal_id   uuid         NOT NULL,
    revoked_at                TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_accepted_fact_revocation_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_accepted_fact_revocation_fact_fkey
        FOREIGN KEY (fact_id) REFERENCES information_request_accepted_fact (id),
    CONSTRAINT ux_information_request_accepted_fact_revocation UNIQUE (fact_id),
    CONSTRAINT ck_information_request_accepted_fact_revocation_reason CHECK (BTRIM(reason_code) <> ''),
    CONSTRAINT ck_information_request_accepted_fact_revocation_principal CHECK (
        revoked_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                      'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE FUNCTION information_request_accepted_fact_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    request_owner_type VARCHAR(32);
    request_owner_organization uuid;
    request_owner_user uuid;
BEGIN
    SELECT owner_type, owner_organization_id, owner_user_id
    INTO request_owner_type, request_owner_organization, request_owner_user
    FROM information_request
    WHERE id = NEW.source_information_request_id;

    IF request_owner_type IS DISTINCT FROM NEW.owner_type
        OR request_owner_organization IS DISTINCT FROM NEW.owner_organization_id
        OR request_owner_user IS DISTINCT FROM NEW.owner_user_id
    THEN
        RAISE EXCEPTION 'an accepted fact belongs to the owner of its source request';
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_party party
                   WHERE party.information_request_id = NEW.source_information_request_id
                     AND party.role_key = 'SUBJECT'
                     AND party.subject_identity_ref_id = NEW.subject_identity_ref_id)
    THEN
        RAISE EXCEPTION 'an accepted fact is about the subject of its source request';
    END IF;

    IF NOT EXISTS (SELECT 1
                   FROM information_request_submission_item item
                            JOIN information_request_template_requirement_binding binding
                                 ON binding.id = item.template_binding_id
                   WHERE item.id = NEW.source_submission_item_id
                     AND item.package_id = NEW.source_package_id
                     AND item.requirement_type = 'FIELD'
                     AND item.completeness_state = 'COMPLETE'
                     AND item.information_request_requirement_id = NEW.source_requirement_id
                     AND item.response_id = NEW.source_response_id
                     AND item.response_revision = NEW.source_response_revision
                     AND item.field_value_revision_id = NEW.source_field_value_revision_id
                     AND binding.collected_field_definition_id = NEW.field_definition_id)
    THEN
        RAISE EXCEPTION 'an accepted fact names the exact answer its source item froze';
    END IF;

    IF NEW.source_review_id IS NOT NULL AND NOT EXISTS (SELECT 1
                                                        FROM information_request_review review
                                                        WHERE review.id = NEW.source_review_id
                                                          AND review.state IN ('SATISFIED', 'SATISFIED_WITH_EXCEPTION'))
    THEN
        RAISE EXCEPTION 'a reviewed accepted fact names a review that accepted its package';
    END IF;

    IF NEW.supersedes_fact_id IS NOT NULL THEN
        IF EXISTS (SELECT 1
                   FROM information_request_accepted_fact_revocation revocation
                   WHERE revocation.fact_id = NEW.supersedes_fact_id)
        THEN
            RAISE EXCEPTION 'a revoked accepted fact cannot be superseded';
        END IF;

        IF NOT EXISTS (SELECT 1
                       FROM information_request_accepted_fact earlier
                       WHERE earlier.id = NEW.supersedes_fact_id
                         AND earlier.owner_type = NEW.owner_type
                         AND earlier.owner_organization_id IS NOT DISTINCT FROM NEW.owner_organization_id
                         AND earlier.owner_user_id IS NOT DISTINCT FROM NEW.owner_user_id
                         AND earlier.subject_identity_ref_id = NEW.subject_identity_ref_id
                         AND earlier.field_definition_id = NEW.field_definition_id
                         AND earlier.purpose_key = NEW.purpose_key)
        THEN
            RAISE EXCEPTION 'an accepted fact supersedes a fact of the same owner, subject, field, and purpose';
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_accepted_fact_guard_write
    BEFORE INSERT
    ON information_request_accepted_fact
    FOR EACH ROW
EXECUTE FUNCTION information_request_accepted_fact_guard();

CREATE TRIGGER information_request_accepted_fact_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_accepted_fact
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_accepted_fact_revocation_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_accepted_fact_revocation
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_business_decision
(
    id                         uuid         NOT NULL,
    information_request_id     uuid         NOT NULL,
    owning_process_key         VARCHAR(128) NOT NULL,
    outcome_code               VARCHAR(128) NOT NULL,
    reason_reference           VARCHAR(512),
    external_reference         VARCHAR(512),
    kind                       VARCHAR(32)  NOT NULL,
    prior_decision_id          uuid,
    decision_revision          INTEGER      NOT NULL,
    decided_at                 TIMESTAMPTZ  NOT NULL,
    recorded_by_principal_kind VARCHAR(32)  NOT NULL,
    recorded_by_principal_id   uuid         NOT NULL,
    recorded_at                TIMESTAMPTZ  NOT NULL,
    CONSTRAINT information_request_business_decision_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_business_decision_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_business_decision_prior_fkey
        FOREIGN KEY (prior_decision_id, information_request_id)
            REFERENCES information_request_business_decision (id, information_request_id),
    CONSTRAINT uq_information_request_business_decision_request UNIQUE (id, information_request_id),
    CONSTRAINT ux_information_request_business_decision_revision
        UNIQUE (information_request_id, owning_process_key, decision_revision),
    CONSTRAINT ck_information_request_business_decision_keys CHECK (
        owning_process_key ~ '^[a-z0-9][a-z0-9._-]{0,127}$' AND outcome_code ~ '^[a-z0-9][a-z0-9._-]{0,127}$'
        ),
    CONSTRAINT ck_information_request_business_decision_references CHECK (
        (reason_reference IS NULL OR BTRIM(reason_reference) <> '')
            AND (external_reference IS NULL OR BTRIM(external_reference) <> '')
        ),
    CONSTRAINT ck_information_request_business_decision_kind CHECK (kind IN ('ORIGINAL', 'RECONSIDERATION', 'APPEAL')),
    CONSTRAINT ck_information_request_business_decision_chain CHECK (
        (kind = 'ORIGINAL') = (prior_decision_id IS NULL)
            AND (kind = 'ORIGINAL') = (decision_revision = 1)
        ),
    CONSTRAINT ck_information_request_business_decision_principal CHECK (
        recorded_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION',
                                       'APPLICATION', 'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE FUNCTION information_request_business_decision_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    next_revision INTEGER;
BEGIN
    SELECT COALESCE(MAX(decision_revision), 0) + 1 INTO next_revision
    FROM information_request_business_decision
    WHERE information_request_id = NEW.information_request_id
      AND owning_process_key = NEW.owning_process_key;

    IF NEW.decision_revision <> next_revision THEN
        RAISE EXCEPTION 'a business decision numbers its process from one without a gap';
    END IF;

    IF NEW.prior_decision_id IS NOT NULL AND NOT EXISTS (SELECT 1
                                                         FROM information_request_business_decision prior
                                                         WHERE prior.id = NEW.prior_decision_id
                                                           AND prior.owning_process_key = NEW.owning_process_key
                                                           AND prior.decision_revision = NEW.decision_revision - 1)
    THEN
        RAISE EXCEPTION 'a later business decision names the latest decision of its own process';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_business_decision_guard_write
    BEFORE INSERT
    ON information_request_business_decision
    FOR EACH ROW
EXECUTE FUNCTION information_request_business_decision_guard();

CREATE TRIGGER information_request_business_decision_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_business_decision
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
