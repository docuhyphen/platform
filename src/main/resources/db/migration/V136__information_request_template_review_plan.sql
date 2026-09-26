ALTER TABLE information_request_template_version
    ADD COLUMN review_stage_ordering  VARCHAR(32) NOT NULL DEFAULT 'SEQUENTIAL',
    ADD COLUMN fact_reuse_purpose_key VARCHAR(128);

ALTER TABLE information_request_template_version
    ADD CONSTRAINT ck_request_template_version_review_ordering CHECK (
        review_stage_ordering IN ('SEQUENTIAL', 'PARALLEL')
        ),
    ADD CONSTRAINT ck_request_template_version_fact_purpose CHECK (
        fact_reuse_purpose_key IS NULL OR fact_reuse_purpose_key ~ '^[a-z0-9][a-z0-9._-]{0,127}$'
        );

CREATE TABLE information_request_template_review_stage
(
    id                        uuid         NOT NULL,
    template_version_id       uuid         NOT NULL,
    stage_key                 VARCHAR(128) NOT NULL,
    position                  INTEGER      NOT NULL,
    title                     VARCHAR(255) NOT NULL,
    aggregation               VARCHAR(32)  NOT NULL,
    quorum_count              INTEGER,
    minimum_reviewer_count    INTEGER      NOT NULL,
    tie_resolution            VARCHAR(32)  NOT NULL,
    override_permitted        BOOLEAN      NOT NULL,
    excludes_response_parties BOOLEAN      NOT NULL,
    excludes_prior_reviewers  BOOLEAN      NOT NULL,
    CONSTRAINT information_request_template_review_stage_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_review_stage_version_fkey
        FOREIGN KEY (template_version_id) REFERENCES information_request_template_version (id),
    CONSTRAINT ux_request_template_review_stage_key UNIQUE (template_version_id, stage_key),
    CONSTRAINT ux_request_template_review_stage_position UNIQUE (template_version_id, position),
    CONSTRAINT uq_request_template_review_stage_version UNIQUE (id, template_version_id),
    CONSTRAINT ck_request_template_review_stage_values CHECK (
        BTRIM(stage_key) <> '' AND BTRIM(title) <> ''
        ),
    CONSTRAINT ck_request_template_review_stage_position CHECK (position >= 1),
    CONSTRAINT ck_request_template_review_stage_aggregation CHECK (
        aggregation IN ('ALL', 'ANY', 'QUORUM', 'CONSENSUS')
        ),
    CONSTRAINT ck_request_template_review_stage_minimum CHECK (minimum_reviewer_count >= 1),
    CONSTRAINT ck_request_template_review_stage_quorum CHECK (
        (aggregation = 'QUORUM') = (quorum_count IS NOT NULL)
            AND (quorum_count IS NULL OR (quorum_count >= 1 AND quorum_count <= minimum_reviewer_count))
        ),
    CONSTRAINT ck_request_template_review_stage_tie CHECK (
        tie_resolution IN ('MOST_SEVERE_OUTCOME', 'REQUIRE_OVERRIDE')
            AND (tie_resolution <> 'REQUIRE_OVERRIDE' OR override_permitted)
        )
);

CREATE TABLE information_request_template_review_stage_section
(
    id                  uuid NOT NULL,
    template_version_id uuid NOT NULL,
    review_stage_id     uuid NOT NULL,
    template_section_id uuid NOT NULL,
    CONSTRAINT information_request_template_review_stage_section_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_review_stage_section_stage_fkey
        FOREIGN KEY (review_stage_id, template_version_id)
            REFERENCES information_request_template_review_stage (id, template_version_id),
    CONSTRAINT request_template_review_stage_section_section_fkey
        FOREIGN KEY (template_section_id, template_version_id)
            REFERENCES information_request_template_section (id, template_version_id),
    CONSTRAINT ux_request_template_review_stage_section UNIQUE (review_stage_id, template_section_id)
);

CREATE INDEX ix_request_template_review_stage_section_version
    ON information_request_template_review_stage_section (template_version_id);

CREATE TRIGGER request_template_review_stage_freeze_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_review_stage
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

CREATE TRIGGER request_template_review_stage_section_freeze_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_review_stage_section
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

CREATE FUNCTION request_template_routes_review(candidate uuid)
    RETURNS BOOLEAN AS
$$
SELECT EXISTS (SELECT 1
               FROM request_template_required_capabilities(candidate) required
               WHERE required.capability_key = 'RESPONSE_REVIEW');
$$ LANGUAGE sql STABLE;

CREATE FUNCTION request_template_state_default_review_stage(candidate uuid)
    RETURNS void AS
$$
BEGIN
    IF request_template_routes_review(candidate)
        AND NOT EXISTS (SELECT 1
                        FROM information_request_template_review_stage stage
                        WHERE stage.template_version_id = candidate)
    THEN
        INSERT INTO information_request_template_review_stage
            (id, template_version_id, stage_key, position, title, aggregation, quorum_count,
             minimum_reviewer_count, tie_resolution, override_permitted, excludes_response_parties,
             excludes_prior_reviewers)
        VALUES (gen_random_uuid(), candidate, 'review', 1, 'Review', 'ANY', NULL, 1,
                'MOST_SEVERE_OUTCOME', FALSE, FALSE, FALSE);
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION request_template_version_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'a published information request template version is immutable and cannot be removed';
        END IF;
        RETURN OLD;
    END IF;

    IF NEW.status = 'PUBLISHED' AND OLD.status <> 'PUBLISHED' THEN
        PERFORM request_template_state_default_attestation_policies(NEW.id);
        PERFORM request_template_state_default_review_stage(NEW.id);
        PERFORM request_template_publication_completeness(NEW.id, NEW.schema_version_id);
    END IF;

    IF OLD.status = 'DRAFT' THEN
        RETURN NEW;
    END IF;

    IF OLD.status = 'RETIRED' THEN
        RAISE EXCEPTION 'a retired information request template version is immutable';
    END IF;

    IF NEW.status <> 'RETIRED'
        OR NEW.id <> OLD.id
        OR NEW.template_definition_id <> OLD.template_definition_id
        OR NEW.version_number <> OLD.version_number
        OR NEW.schema_version_id IS DISTINCT FROM OLD.schema_version_id
        OR NEW.submission_mode IS DISTINCT FROM OLD.submission_mode
        OR NEW.submission_stage_ordering IS DISTINCT FROM OLD.submission_stage_ordering
        OR NEW.review_stage_ordering IS DISTINCT FROM OLD.review_stage_ordering
        OR NEW.fact_reuse_purpose_key IS DISTINCT FROM OLD.fact_reuse_purpose_key
        OR NEW.published_at IS DISTINCT FROM OLD.published_at
        OR NEW.published_by_app_user_id IS DISTINCT FROM OLD.published_by_app_user_id
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.created_by_app_user_id IS DISTINCT FROM OLD.created_by_app_user_id
    THEN
        RAISE EXCEPTION 'a published information request template version is immutable except for retirement';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION request_template_publication_completeness(
    candidate uuid,
    resolved_schema_version uuid
)
    RETURNS void AS
$$
DECLARE
    candidate_mode VARCHAR(32);
BEGIN
    IF resolved_schema_version IS NULL
        AND EXISTS (SELECT 1
                    FROM information_request_template_requirement_binding binding
                             JOIN information_request_template_requirement requirement
                                  ON requirement.id = binding.template_requirement_id
                    WHERE binding.template_version_id = candidate
                      AND requirement.requirement_type = 'FIELD')
    THEN
        RAISE EXCEPTION 'an information request template version that requests field data must name the schema version it resolves against';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_template_requirement_binding binding
                        JOIN information_request_template_requirement requirement
                             ON requirement.id = binding.template_requirement_id
               WHERE binding.template_version_id = candidate
                 AND requirement.requirement_type = 'DOCUMENT'
                 AND NOT EXISTS (SELECT 1
                                 FROM information_request_template_evidence_policy policy
                                 WHERE policy.template_binding_id = binding.id))
    THEN
        RAISE EXCEPTION 'an information request template version that requests a document must state the evidence policy it is judged by';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_template_evidence_accepted_value accepted
                        JOIN information_request_template_evidence_policy policy
                             ON policy.id = accepted.evidence_policy_id
               WHERE policy.template_version_id = candidate
                 AND CASE accepted.attribute
                         WHEN 'ISSUER' THEN policy.issuer_requirement
                         WHEN 'JURISDICTION' THEN policy.jurisdiction_requirement
                         WHEN 'LANGUAGE' THEN policy.language_requirement
                         ELSE NULL
                         END = 'NOT_CAPTURED')
    THEN
        RAISE EXCEPTION 'an evidence attribute that is never captured cannot restrict which values are accepted';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_template_evidence_policy policy
               WHERE policy.template_version_id = candidate
                 AND policy.waiver_policy <> 'NOT_PERMITTED'
                 AND NOT EXISTS (SELECT 1
                                 FROM information_request_template_binding_disposition permitted
                                 WHERE permitted.template_binding_id = policy.template_binding_id
                                   AND permitted.disposition = 'WAIVED'))
    THEN
        RAISE EXCEPTION 'an evidence waiver rule requires the requirement to permit a waived answer';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_template_evidence_policy policy
                        JOIN information_request_template_binding_disposition permitted
                             ON permitted.template_binding_id = policy.template_binding_id
               WHERE policy.template_version_id = candidate
                 AND policy.waiver_policy = 'NOT_PERMITTED'
                 AND permitted.disposition = 'WAIVED')
    THEN
        RAISE EXCEPTION 'a requirement that permits a waived answer must state the evidence waiver rule that reaches it';
    END IF;

    IF EXISTS (SELECT 1
               FROM request_template_required_capabilities(candidate) required
               WHERE NOT EXISTS (SELECT 1
                                 FROM information_request_template_version_capability recorded
                                 WHERE recorded.template_version_id = candidate
                                   AND recorded.capability_key = required.capability_key))
    THEN
        RAISE EXCEPTION 'an information request template version must record every runtime capability its configuration requires';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_template_version_capability recorded
               WHERE recorded.template_version_id = candidate
                 AND NOT EXISTS (SELECT 1
                                 FROM request_template_required_capabilities(candidate) required
                                 WHERE required.capability_key = recorded.capability_key))
    THEN
        RAISE EXCEPTION 'an information request template version cannot require a runtime capability its configuration does not use';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_template_requirement_binding binding
               WHERE binding.template_version_id = candidate
                 AND binding.collected_field_definition_id IS NOT NULL
                 AND NOT EXISTS (SELECT 1
                                 FROM schema_field_binding bound
                                 WHERE bound.schema_version_id = resolved_schema_version
                                   AND bound.field_definition_id = binding.collected_field_definition_id))
    THEN
        RAISE EXCEPTION 'an information request template version cannot collect a field its schema version does not bind';
    END IF;

    SELECT submission_mode INTO candidate_mode
    FROM information_request_template_version
    WHERE id = candidate;

    IF candidate_mode = 'STAGED' AND EXISTS (SELECT 1
                                             FROM information_request_template_section section
                                             WHERE section.template_version_id = candidate
                                               AND section.submission_stage_key IS NULL)
    THEN
        RAISE EXCEPTION 'a staged version must name the submission stage of every section';
    END IF;

    IF candidate_mode = 'WHOLE_PACKAGE' AND EXISTS (SELECT 1
                                                    FROM information_request_template_section section
                                                    WHERE section.template_version_id = candidate
                                                      AND section.submission_stage_key IS NOT NULL)
    THEN
        RAISE EXCEPTION 'a whole-package version cannot name a submission stage';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_template_attestation_policy policy
               WHERE policy.template_version_id = candidate
                 AND NOT EXISTS (SELECT 1
                                 FROM information_request_template_attestation_role role
                                 WHERE role.attestation_policy_id = policy.id))
    THEN
        RAISE EXCEPTION 'an attestation policy must name at least one role that makes the assertion';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_template_attestation_policy policy
               WHERE policy.template_version_id = candidate
                 AND policy.minimum_assent_count < (SELECT COUNT(*)
                                                    FROM information_request_template_attestation_role role
                                                    WHERE role.attestation_policy_id = policy.id))
    THEN
        RAISE EXCEPTION 'an attestation policy must need at least one assent from each role it names';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_template_attestation_policy policy
               WHERE policy.template_version_id = candidate
                 AND (SELECT MAX(role.position)
                      FROM information_request_template_attestation_role role
                      WHERE role.attestation_policy_id = policy.id)
                     <> (SELECT COUNT(*)
                         FROM information_request_template_attestation_role role
                         WHERE role.attestation_policy_id = policy.id))
    THEN
        RAISE EXCEPTION 'attestation role positions must run from one without a gap';
    END IF;

    IF NOT request_template_routes_review(candidate)
        AND EXISTS (SELECT 1
                    FROM information_request_template_review_stage stage
                    WHERE stage.template_version_id = candidate)
    THEN
        RAISE EXCEPTION 'a version that routes no work to a reviewer states no review stage';
    END IF;

    IF (SELECT COALESCE(MAX(stage.position), 0)
        FROM information_request_template_review_stage stage
        WHERE stage.template_version_id = candidate)
        <> (SELECT COUNT(*)
            FROM information_request_template_review_stage stage
            WHERE stage.template_version_id = candidate)
    THEN
        RAISE EXCEPTION 'review stage positions must run from one without a gap';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_template_requirement_binding binding
               WHERE binding.template_version_id = candidate
                 AND (binding.review_policy <> 'NOT_REQUIRED'
                   OR EXISTS (SELECT 1
                              FROM information_request_template_evidence_policy policy
                              WHERE policy.template_binding_id = binding.id
                                AND (policy.conformance_policy = 'DEFICIENCY_REVIEWABLE'
                                  OR policy.waiver_policy = 'REVIEW_APPROVAL_REQUIRED')))
                 AND NOT EXISTS (SELECT 1
                                 FROM information_request_template_review_stage stage
                                 WHERE stage.template_version_id = candidate
                                   AND (NOT EXISTS (SELECT 1
                                                    FROM information_request_template_review_stage_section covered
                                                    WHERE covered.review_stage_id = stage.id)
                                     OR EXISTS (SELECT 1
                                                FROM information_request_template_review_stage_section covered
                                                WHERE covered.review_stage_id = stage.id
                                                  AND covered.template_section_id = binding.template_section_id))))
    THEN
        RAISE EXCEPTION 'every requirement that routes work to a reviewer must be covered by a review stage';
    END IF;
END;
$$ LANGUAGE plpgsql;

ALTER TABLE information_request_template_review_stage
    DISABLE TRIGGER request_template_review_stage_freeze_write;

SELECT request_template_state_default_review_stage(version.id)
FROM information_request_template_version version
WHERE version.status <> 'DRAFT';

ALTER TABLE information_request_template_review_stage
    ENABLE TRIGGER request_template_review_stage_freeze_write;
