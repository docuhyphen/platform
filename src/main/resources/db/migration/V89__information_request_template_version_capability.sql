-- What a frozen version needs a runtime to supply.
--
-- Configuration and the runtime that executes it move separately. A version can be authored and
-- frozen while the runtime that serves part of it is still being built, so a version has to state
-- what it needs rather than assume it. Each row names one capability and the contract version of it
-- the version was frozen against.
--
-- Which capabilities those are is not an author's free choice: they follow from what the version
-- actually configures. The derivation is held in one function so there is one answer, and
-- publication refuses a recorded set that disagrees with it in either direction. A missing entry
-- would let a version be issued into a runtime that cannot serve it, and an extra one would refuse
-- issuance for a reason no author could act on, since there is no configuration to remove.
--
-- Which contract version is needed is recorded rather than derived, because whether an installed
-- runtime can serve it is answered in code, where the installed executors are known. That question
-- belongs to issuance rather than to publication: a version stays publishable while the runtime it
-- needs is still being built, and only creating a request against it requires the runtime to be
-- there.

-- ── Capabilities one version requires ────────────────────────────────────────────────────────
CREATE TABLE information_request_template_version_capability
(
    id                        uuid        NOT NULL,
    template_version_id       uuid        NOT NULL,
    capability_key            VARCHAR(48) NOT NULL,
    -- The contract version of the capability this version was frozen against. An installed runtime
    -- serves a range of contract versions and this is the one it has to include.
    required_contract_version INTEGER     NOT NULL,
    CONSTRAINT information_request_template_version_capability_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_capability_version_fkey
        FOREIGN KEY (template_version_id) REFERENCES information_request_template_version (id),
    CONSTRAINT ck_request_template_capability_key CHECK (
        capability_key IN ('STRUCTURED_RESPONSE', 'DOCUMENT_EVIDENCE', 'RESPONSE_ATTESTATION',
                           'CONDITIONAL_REQUIREMENT', 'REPEATABLE_OCCURRENCE', 'RESPONSE_REVIEW',
                           'CONFIDENTIALITY_COMPARTMENT', 'EVIDENCE_WAIVER', 'SUBSTITUTE_EVIDENCE',
                           'SUPPORTING_EVIDENCE', 'RESPONSE_SUBMISSION')
        ),
    CONSTRAINT ck_request_template_capability_contract_version CHECK (
        required_contract_version >= 1
        ),
    -- One capability is needed at one contract version. Two entries would both be in force and an
    -- installed runtime could satisfy one while failing the other.
    CONSTRAINT ux_request_template_capability_version_key
        UNIQUE (template_version_id, capability_key)
);

-- ── The set follows from the configuration ───────────────────────────────────────────────────
-- Every entry traces to a configured fact that needs somebody to serve it. Requesting typed data,
-- a document, or an assertion each needs the runtime that collects that kind of answer; anything
-- collected at all needs the runtime that submits the result. The rest are the optional policies a
-- binding or an evidence policy may state, each of which is inert unless something executes it.
CREATE FUNCTION request_template_required_capabilities(candidate uuid)
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

-- ── The set freezes with the version that recorded it ────────────────────────────────────────
-- Each row carries the version it belongs to, so the guard already written for sections, bindings,
-- and evidence policy applies to it unchanged.
CREATE TRIGGER request_template_capability_freeze_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_version_capability
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

-- ── Publication compares the recorded set against the derived one ────────────────────────────
CREATE OR REPLACE FUNCTION request_template_publication_completeness(
    candidate uuid,
    resolved_schema_version uuid
)
    RETURNS void AS
$$
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

    -- A document collected under no stated policy leaves nothing to judge the files by, and the
    -- absence is indistinguishable from an author who never reached the question.
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

    -- Restricting the values of an attribute nobody states is a rule that can never be applied.
    -- CONTENT_TYPE is read from the file rather than stated, so it is not one of the captured
    -- attributes and nothing constrains restricting it.
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

    -- The permitted answers decide whether a waiver is reachable; the evidence policy decides how
    -- one may be reached. Either statement without the other leaves a rule that never takes effect
    -- or an answer that nothing governs.
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

    -- Publication is the last point at which the runtime a version needs can still be stated. An
    -- unrecorded capability would let the version be issued into a runtime that cannot serve it.
    IF EXISTS (SELECT 1
               FROM request_template_required_capabilities(candidate) required
               WHERE NOT EXISTS (SELECT 1
                                 FROM information_request_template_version_capability recorded
                                 WHERE recorded.template_version_id = candidate
                                   AND recorded.capability_key = required.capability_key))
    THEN
        RAISE EXCEPTION 'an information request template version must record every runtime capability its configuration requires';
    END IF;

    -- The other direction matters just as much: a requirement nothing configured would refuse
    -- issuance for a reason no author could act on.
    IF EXISTS (SELECT 1
               FROM information_request_template_version_capability recorded
               WHERE recorded.template_version_id = candidate
                 AND NOT EXISTS (SELECT 1
                                 FROM request_template_required_capabilities(candidate) required
                                 WHERE required.capability_key = recorded.capability_key))
    THEN
        RAISE EXCEPTION 'an information request template version cannot require a runtime capability its configuration does not use';
    END IF;
END;
$$ LANGUAGE plpgsql;
