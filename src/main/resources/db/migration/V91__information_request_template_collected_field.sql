-- The field a typed requirement collects.
--
-- A requirement that asks for typed data has to say which attribute of the version's typed-data
-- contract the answer is recorded against. Without it a version can freeze while resolving typed
-- answers against nothing in particular, and nothing later can decide where one response belongs.
--
-- The stable field identity is named rather than one of its contract versions. A contract version
-- is what a schema version binds, and two schema versions may bind different contract versions of
-- the same field; the thing that stays the same across both, and across a later template version
-- asking the same requirement again, is the definition.
--
-- Whether the version's own schema version actually binds that field is asked at publication rather
-- than on every write. An author names the schema version and the fields it collects in one
-- authored document, and the two arrive in separate statements, so a write-time comparison would
-- refuse a coherent document for the order its parts reached storage in.

ALTER TABLE information_request_template_requirement_binding
    ADD COLUMN collected_field_definition_id uuid;

ALTER TABLE information_request_template_requirement_binding
    ADD CONSTRAINT request_template_binding_collected_field_fkey
        FOREIGN KEY (collected_field_definition_id) REFERENCES field_definition (id);

-- One version collects one field once. Two requirements resolving to the same field would record
-- two answers against one attribute, and nothing could say which of them the field holds.
CREATE UNIQUE INDEX ux_request_template_binding_collected_field
    ON information_request_template_requirement_binding (template_version_id, collected_field_definition_id)
    WHERE collected_field_definition_id IS NOT NULL;

-- ── Only a typed requirement collects a field, and every typed requirement does ───────────────
-- The kind of thing being asked for lives on the stable requirement rather than on the binding, so
-- this cannot be a check constraint. The requirement is read through the binding's own reference to
-- it rather than through the stored binding, because the binding does not exist yet on insert.
CREATE FUNCTION request_template_binding_collected_field_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    asked VARCHAR(32);
BEGIN
    SELECT requirement.requirement_type
    INTO asked
    FROM information_request_template_requirement requirement
    WHERE requirement.id = NEW.template_requirement_id;

    IF asked = 'FIELD' AND NEW.collected_field_definition_id IS NULL THEN
        RAISE EXCEPTION 'a requirement that asks for typed data must name the field it collects';
    END IF;

    IF asked IS DISTINCT FROM 'FIELD' AND NEW.collected_field_definition_id IS NOT NULL THEN
        RAISE EXCEPTION 'only a requirement that asks for typed data names a collected field';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER request_template_binding_collected_field_write
    BEFORE INSERT OR UPDATE
    ON information_request_template_requirement_binding
    FOR EACH ROW
EXECUTE FUNCTION request_template_binding_collected_field_guard();

-- ── Publication checks the named fields against the contract the version resolves against ─────
-- Restated in full because it is one function; the rule added at the end is the only new one.
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

    -- A named field the version's own contract does not carry has nowhere to record an answer. The
    -- contract is the whole of what the version can collect, so a field outside it is unreachable
    -- however valid the field itself is.
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
END;
$$ LANGUAGE plpgsql;

