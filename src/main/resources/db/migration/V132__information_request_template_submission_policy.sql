ALTER TABLE information_request_template_version
    ADD COLUMN submission_mode           VARCHAR(32) NOT NULL DEFAULT 'WHOLE_PACKAGE',
    ADD COLUMN submission_stage_ordering VARCHAR(32) NOT NULL DEFAULT 'ANY_ORDER';

ALTER TABLE information_request_template_version
    ADD CONSTRAINT ck_request_template_version_submission_mode CHECK (
        submission_mode IN ('WHOLE_PACKAGE', 'STAGED')
        ),
    ADD CONSTRAINT ck_request_template_version_stage_ordering CHECK (
        submission_stage_ordering IN ('ANY_ORDER', 'SEQUENTIAL')
            AND (submission_mode = 'STAGED' OR submission_stage_ordering = 'ANY_ORDER')
        );

ALTER TABLE information_request_template_section
    ADD COLUMN submission_stage_key VARCHAR(128);

ALTER TABLE information_request_template_section
    ADD CONSTRAINT ck_request_template_section_stage_key CHECK (
        submission_stage_key IS NULL OR BTRIM(submission_stage_key) <> ''
        );

CREATE TABLE information_request_template_attestation_policy
(
    id                              uuid        NOT NULL,
    template_version_id             uuid        NOT NULL,
    template_binding_id             uuid        NOT NULL,
    ordering                        VARCHAR(32) NOT NULL,
    minimum_assent_count            INTEGER     NOT NULL,
    minimum_authentication_strength VARCHAR(32) NOT NULL,
    validity_hours                  INTEGER,
    external_signature_reference    VARCHAR(32) NOT NULL,
    CONSTRAINT information_request_template_attestation_policy_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_attestation_policy_binding_fkey
        FOREIGN KEY (template_binding_id, template_version_id)
            REFERENCES information_request_template_requirement_binding (id, template_version_id),
    CONSTRAINT ck_request_template_attestation_policy_ordering CHECK (
        ordering IN ('ANY_ORDER', 'ROLE_SEQUENCE')
        ),
    CONSTRAINT ck_request_template_attestation_policy_assents CHECK (minimum_assent_count >= 1),
    CONSTRAINT ck_request_template_attestation_policy_strength CHECK (
        minimum_authentication_strength IN ('VERIFIED_CONTACT', 'ACCOUNT_SIGN_IN', 'MULTI_FACTOR')
        ),
    CONSTRAINT ck_request_template_attestation_policy_validity CHECK (
        validity_hours IS NULL OR validity_hours >= 1
        ),
    CONSTRAINT ck_request_template_attestation_policy_signature CHECK (
        external_signature_reference IN ('NOT_ACCEPTED', 'OPTIONAL', 'REQUIRED')
        ),
    CONSTRAINT ux_request_template_attestation_policy_binding UNIQUE (template_binding_id),
    CONSTRAINT uq_request_template_attestation_policy_version UNIQUE (id, template_version_id)
);

CREATE INDEX ix_request_template_attestation_policy_version
    ON information_request_template_attestation_policy (template_version_id);

CREATE TABLE information_request_template_attestation_role
(
    id                    uuid        NOT NULL,
    template_version_id   uuid        NOT NULL,
    attestation_policy_id uuid        NOT NULL,
    role_key              VARCHAR(32) NOT NULL,
    position              INTEGER     NOT NULL,
    CONSTRAINT information_request_template_attestation_role_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_attestation_role_policy_fkey
        FOREIGN KEY (attestation_policy_id, template_version_id)
            REFERENCES information_request_template_attestation_policy (id, template_version_id),
    CONSTRAINT ck_request_template_attestation_role_key CHECK (
        role_key IN ('SUBJECT', 'CONTRIBUTOR', 'PREPARER', 'ATTESTOR')
        ),
    CONSTRAINT ck_request_template_attestation_role_position CHECK (position >= 1),
    CONSTRAINT ux_request_template_attestation_role_key UNIQUE (attestation_policy_id, role_key),
    CONSTRAINT ux_request_template_attestation_role_position UNIQUE (attestation_policy_id, position)
);

CREATE INDEX ix_request_template_attestation_role_version
    ON information_request_template_attestation_role (template_version_id);

CREATE FUNCTION request_template_attestation_policy_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    nominated_mode VARCHAR(32);
BEGIN
    IF request_template_binding_requirement_type(NEW.template_binding_id) IS DISTINCT FROM 'RESPONSE_ATTESTATION' THEN
        RAISE EXCEPTION 'only an assertion states an attestation policy';
    END IF;

    SELECT response_mode INTO nominated_mode
    FROM information_request_template_requirement_binding
    WHERE id = NEW.template_binding_id;

    IF nominated_mode NOT IN ('PROVIDE', 'PROVIDE_ONCE') THEN
        RAISE EXCEPTION 'an assertion its nominated party cannot answer has no attestation policy';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION request_template_state_default_attestation_policies(candidate uuid)
    RETURNS void AS
$$
BEGIN
    WITH stated AS (
        INSERT INTO information_request_template_attestation_policy
            (id, template_version_id, template_binding_id, ordering, minimum_assent_count,
             minimum_authentication_strength, validity_hours, external_signature_reference)
            SELECT gen_random_uuid(), binding.template_version_id, binding.id, 'ANY_ORDER', 1,
                   'VERIFIED_CONTACT', NULL, 'NOT_ACCEPTED'
            FROM information_request_template_requirement_binding binding
                     JOIN information_request_template_requirement requirement
                          ON requirement.id = binding.template_requirement_id
            WHERE binding.template_version_id = candidate
              AND requirement.requirement_type = 'RESPONSE_ATTESTATION'
              AND binding.response_mode IN ('PROVIDE', 'PROVIDE_ONCE')
              AND NOT EXISTS (SELECT 1
                              FROM information_request_template_attestation_policy existing
                              WHERE existing.template_binding_id = binding.id)
            RETURNING id, template_version_id, template_binding_id)
    INSERT INTO information_request_template_attestation_role
        (id, template_version_id, attestation_policy_id, role_key, position)
    SELECT gen_random_uuid(), stated.template_version_id, stated.id, binding.contributor_role, 1
    FROM stated
             JOIN information_request_template_requirement_binding binding
                  ON binding.id = stated.template_binding_id;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER request_template_attestation_policy_guard_write
    BEFORE INSERT OR UPDATE
    ON information_request_template_attestation_policy
    FOR EACH ROW
EXECUTE FUNCTION request_template_attestation_policy_guard();

CREATE TRIGGER request_template_attestation_policy_freeze_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_attestation_policy
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

CREATE TRIGGER request_template_attestation_role_freeze_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_attestation_role
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

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

CREATE OR REPLACE FUNCTION request_template_required_capabilities(candidate uuid)
    RETURNS TABLE
            (
                capability_key VARCHAR
            )
AS
$$
SELECT DISTINCT derived.capability
FROM (SELECT CASE requirement.requirement_type
                 WHEN 'FIELD' THEN 'STRUCTURED_RESPONSE'
                 WHEN 'DOCUMENT' THEN 'DOCUMENT_EVIDENCE'
                 WHEN 'RESPONSE_ATTESTATION' THEN 'RESPONSE_ATTESTATION'
                 END::VARCHAR AS capability
      FROM information_request_template_requirement_binding binding
               JOIN information_request_template_requirement requirement
                    ON requirement.id = binding.template_requirement_id
      WHERE binding.template_version_id = candidate

      UNION ALL

      SELECT 'RESPONSE_SUBMISSION'::VARCHAR
      FROM information_request_template_requirement_binding binding
      WHERE binding.template_version_id = candidate

      UNION ALL

      SELECT 'CONDITIONAL_REQUIREMENT'::VARCHAR
      FROM information_request_template_requirement_binding binding
      WHERE binding.template_version_id = candidate
        AND binding.conditional_rule_key IS NOT NULL

      UNION ALL

      SELECT 'REPEATABLE_OCCURRENCE'::VARCHAR
      FROM information_request_template_requirement_binding binding
      WHERE binding.template_version_id = candidate
        AND binding.occurrence_anchor_key IS NOT NULL

      UNION ALL

      SELECT 'RESPONSE_REVIEW'::VARCHAR
      FROM information_request_template_requirement_binding binding
      WHERE binding.template_version_id = candidate
        AND binding.review_policy <> 'NOT_REQUIRED'

      UNION ALL

      SELECT 'RESPONSE_REVIEW'::VARCHAR
      FROM information_request_template_evidence_policy policy
      WHERE policy.template_version_id = candidate
        AND (policy.conformance_policy = 'DEFICIENCY_REVIEWABLE'
          OR policy.waiver_policy = 'REVIEW_APPROVAL_REQUIRED')

      UNION ALL

      SELECT 'CONFIDENTIALITY_COMPARTMENT'::VARCHAR
      FROM information_request_template_requirement_binding binding
      WHERE binding.template_version_id = candidate
        AND binding.confidentiality_compartment_key IS NOT NULL

      UNION ALL

      SELECT 'EVIDENCE_WAIVER'::VARCHAR
      FROM information_request_template_evidence_policy policy
      WHERE policy.template_version_id = candidate
        AND policy.waiver_policy <> 'NOT_PERMITTED'

      UNION ALL

      SELECT 'SUBSTITUTE_EVIDENCE'::VARCHAR
      FROM information_request_template_binding_substitute substitute
      WHERE substitute.template_version_id = candidate

      UNION ALL

      SELECT 'SUPPORTING_EVIDENCE'::VARCHAR
      FROM information_request_template_binding_evidence_link link
      WHERE link.template_version_id = candidate) derived
WHERE derived.capability IS NOT NULL;
$$ LANGUAGE sql STABLE;

ALTER TABLE information_request_template_version_capability
    DISABLE TRIGGER request_template_capability_freeze_write;

INSERT INTO information_request_template_version_capability
    (id, template_version_id, capability_key, required_contract_version)
SELECT gen_random_uuid(), version.id, 'RESPONSE_REVIEW', 1
FROM information_request_template_version version
WHERE version.status <> 'DRAFT'
  AND EXISTS (SELECT 1
              FROM request_template_required_capabilities(version.id) required
              WHERE required.capability_key = 'RESPONSE_REVIEW')
  AND NOT EXISTS (SELECT 1
                  FROM information_request_template_version_capability recorded
                  WHERE recorded.template_version_id = version.id
                    AND recorded.capability_key = 'RESPONSE_REVIEW');

ALTER TABLE information_request_template_version_capability
    ENABLE TRIGGER request_template_capability_freeze_write;

ALTER TABLE information_request_template_attestation_policy
    DISABLE TRIGGER request_template_attestation_policy_freeze_write;
ALTER TABLE information_request_template_attestation_role
    DISABLE TRIGGER request_template_attestation_role_freeze_write;

SELECT request_template_state_default_attestation_policies(version.id)
FROM information_request_template_version version
WHERE version.status <> 'DRAFT';

ALTER TABLE information_request_template_attestation_policy
    ENABLE TRIGGER request_template_attestation_policy_freeze_write;
ALTER TABLE information_request_template_attestation_role
    ENABLE TRIGGER request_template_attestation_role_freeze_write;

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
END;
$$ LANGUAGE plpgsql;
