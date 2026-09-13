-- What a template asks for, and the policy under which one version asks for it.
--
-- The kind of thing being asked for belongs to the stable requirement. A requirement that was a
-- typed answer in one version and a document in the next is not the same requirement, and every
-- response already recorded against it would silently change meaning. So the type is added to the
-- identity and the identity guard is widened to refuse rewriting it.
--
-- Everything a later version may legitimately restate about that same requirement belongs to the
-- binding: the wording used to ask, who is expected to answer, whether an answer is owed, which
-- answers are permitted, whether a reviewer must look at the result, which compartment the response
-- falls into, what decides whether the requirement applies, and which repeated occurrence it is
-- answered against. Two of those are sets rather than single values, so permitted answers and
-- supporting evidence are their own tables keyed to the binding.
--
-- No requirement or binding has a writer yet, so both tables are empty and the required columns are
-- added without a backfill. There is no honest type or prompt to invent for a row placed by hand
-- before the columns existed, so such a row stops the upgrade instead.

-- ── Requirement type (part of the stable identity) ───────────────────────────────────────────
ALTER TABLE information_request_template_requirement
    ADD COLUMN requirement_type VARCHAR(32) NOT NULL;

ALTER TABLE information_request_template_requirement
    ADD CONSTRAINT ck_request_template_requirement_type CHECK (
        requirement_type IN ('FIELD', 'DOCUMENT', 'RESPONSE_ATTESTATION')
        );

-- ── Respondent policy of one binding ─────────────────────────────────────────────────────────
ALTER TABLE information_request_template_requirement_binding
    ADD COLUMN prompt                          VARCHAR(1024) NOT NULL,
    ADD COLUMN help_text                       VARCHAR(2048),
    -- What the nominated party may do with the item, which is separate from how sensitive the
    -- response is and from who may review it. Read and write are both decided here.
    ADD COLUMN response_mode                   VARCHAR(32)   NOT NULL,
    ADD COLUMN requiredness                    VARCHAR(32)   NOT NULL,
    ADD COLUMN contributor_role                VARCHAR(32)   NOT NULL,
    ADD COLUMN review_policy                   VARCHAR(32)   NOT NULL,
    -- Compartment names are authored by the owner, so this is a key into their own vocabulary
    -- rather than a platform value. Absent means the request's own access rules are the only ones.
    ADD COLUMN confidentiality_compartment_key VARCHAR(64),
    -- Names the versioned rule that decides whether this requirement applies, and the repeatable
    -- group whose occurrences it is answered once per. Both are resolved against configuration that
    -- belongs to the same version.
    ADD COLUMN conditional_rule_key            VARCHAR(128),
    ADD COLUMN occurrence_anchor_key           VARCHAR(128);

ALTER TABLE information_request_template_requirement_binding
    ADD CONSTRAINT ck_request_template_binding_prompt CHECK (BTRIM(prompt) <> ''),
    ADD CONSTRAINT ck_request_template_binding_response_mode CHECK (
        response_mode IN ('PROVIDE', 'PROVIDE_ONCE', 'VIEW_ONLY', 'NOT_DISCLOSED')
        ),
    ADD CONSTRAINT ck_request_template_binding_requiredness CHECK (
        requiredness IN ('REQUIRED', 'OPTIONAL', 'CONDITIONAL')
        ),
    -- A party that reviews an answer is not a party that gives one. Review roles are absent from
    -- this vocabulary rather than merely discouraged, so the separation cannot be configured away.
    ADD CONSTRAINT ck_request_template_binding_contributor_role CHECK (
        contributor_role IN ('SUBJECT', 'CONTRIBUTOR', 'PREPARER', 'ATTESTOR')
        ),
    ADD CONSTRAINT ck_request_template_binding_review_policy CHECK (
        review_policy IN ('NOT_REQUIRED', 'REQUIRED', 'REQUIRED_ON_EXCEPTION')
        ),
    ADD CONSTRAINT ck_request_template_binding_policy_keys CHECK (
        (confidentiality_compartment_key IS NULL OR BTRIM(confidentiality_compartment_key) <> '') AND
        (conditional_rule_key IS NULL OR BTRIM(conditional_rule_key) <> '') AND
        (occurrence_anchor_key IS NULL OR BTRIM(occurrence_anchor_key) <> '')
        ),
    -- A requirement that only sometimes applies has to say what decides it. Without the rule
    -- nothing can resolve later whether the answer was ever owed.
    ADD CONSTRAINT ck_request_template_binding_conditional_rule CHECK (
        requiredness <> 'CONDITIONAL' OR conditional_rule_key IS NOT NULL
        ),
    -- Composite key so the sets below can be held to a binding of their own version.
    ADD CONSTRAINT uq_request_template_binding_version UNIQUE (id, template_version_id);

-- ── Permitted answers for one binding ────────────────────────────────────────────────────────
-- Templates restrict which of the platform dispositions a respondent may choose. NOT_ANSWERED is
-- deliberately not among them: it is the state every requirement starts in, so permitting it as an
-- answer would leave an unanswered requirement indistinguishable from a resolved one.
CREATE TABLE information_request_template_binding_disposition
(
    id                  uuid        NOT NULL,
    template_binding_id uuid        NOT NULL,
    template_version_id uuid        NOT NULL,
    disposition         VARCHAR(32) NOT NULL,
    CONSTRAINT information_request_template_binding_disposition_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_disposition_binding_fkey
        FOREIGN KEY (template_binding_id, template_version_id)
            REFERENCES information_request_template_requirement_binding (id, template_version_id),
    CONSTRAINT ck_request_template_disposition_value CHECK (
        disposition IN ('PROVIDED', 'PARTIALLY_PROVIDED', 'NOT_APPLICABLE', 'UNAVAILABLE',
                        'EXCEPTION_REQUESTED', 'SATISFIED_BY_REFERENCE', 'WAIVED')
        ),
    CONSTRAINT ux_request_template_disposition_binding UNIQUE (template_binding_id, disposition)
);

CREATE INDEX ix_request_template_disposition_version
    ON information_request_template_binding_disposition (template_version_id);

-- ── Supporting evidence for one binding ──────────────────────────────────────────────────────
-- An answer may be supported by documents requested elsewhere in the same version. The relation
-- runs one way only, from something that is not a document to something that is, so a chain of
-- supporting evidence can never close into a cycle and no cycle detection is needed.
CREATE TABLE information_request_template_binding_evidence_link
(
    id                             uuid NOT NULL,
    template_binding_id            uuid NOT NULL,
    supporting_template_binding_id uuid NOT NULL,
    template_version_id            uuid NOT NULL,
    CONSTRAINT information_request_template_binding_evidence_link_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_evidence_link_binding_fkey
        FOREIGN KEY (template_binding_id, template_version_id)
            REFERENCES information_request_template_requirement_binding (id, template_version_id),
    CONSTRAINT request_template_evidence_link_supporting_fkey
        FOREIGN KEY (supporting_template_binding_id, template_version_id)
            REFERENCES information_request_template_requirement_binding (id, template_version_id),
    -- A backstop rather than the working rule: the one-way type relation below already refuses a
    -- self-reference, and this keeps the shortest possible cycle impossible if that ever widens.
    CONSTRAINT ck_request_template_evidence_link_distinct CHECK (
        template_binding_id <> supporting_template_binding_id
        ),
    CONSTRAINT ux_request_template_evidence_link_pair
        UNIQUE (template_binding_id, supporting_template_binding_id)
);

CREATE INDEX ix_request_template_evidence_link_supporting
    ON information_request_template_binding_evidence_link (supporting_template_binding_id);
CREATE INDEX ix_request_template_evidence_link_version
    ON information_request_template_binding_evidence_link (template_version_id);

-- ── The type a binding is placing ────────────────────────────────────────────────────────────
CREATE FUNCTION request_template_binding_requirement_type(candidate uuid)
    RETURNS VARCHAR AS
$$
DECLARE
    resolved VARCHAR(32);
BEGIN
    SELECT requirement.requirement_type
    INTO resolved
    FROM information_request_template_requirement_binding binding
             JOIN information_request_template_requirement requirement
                  ON requirement.id = binding.template_requirement_id
    WHERE binding.id = candidate;

    RETURN resolved;
END;
$$ LANGUAGE plpgsql;

CREATE FUNCTION request_template_evidence_link_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF request_template_binding_requirement_type(NEW.template_binding_id) = 'DOCUMENT' THEN
        RAISE EXCEPTION 'a requested document does not itself declare supporting evidence';
    END IF;

    IF request_template_binding_requirement_type(NEW.supporting_template_binding_id)
        IS DISTINCT FROM 'DOCUMENT' THEN
        RAISE EXCEPTION 'supporting evidence must be a requested document';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER request_template_evidence_link_guard_write
    BEFORE INSERT OR UPDATE
    ON information_request_template_binding_evidence_link
    FOR EACH ROW
EXECUTE FUNCTION request_template_evidence_link_guard();

-- ── Both sets freeze with the version that holds them ────────────────────────────────────────
-- Each set carries the version of its binding, so the guard already written for sections and
-- bindings applies to them unchanged.
CREATE TRIGGER request_template_disposition_guard_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_binding_disposition
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

CREATE TRIGGER request_template_evidence_link_freeze_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_binding_evidence_link
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

-- ── A stable requirement identity now includes its type ──────────────────────────────────────
CREATE OR REPLACE FUNCTION request_template_requirement_identity_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NEW.id <> OLD.id
        OR NEW.template_definition_id <> OLD.template_definition_id
        OR NEW.requirement_key <> OLD.requirement_key
        OR NEW.requirement_type <> OLD.requirement_type
    THEN
        RAISE EXCEPTION 'the stable identity of an information request template requirement cannot be rewritten';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ── A version that asks for typed data names the contract it resolves against ────────────────
-- The schema reference stays optional, because a version that asks only for documents and
-- assertions needs no typed-data contract. It becomes required at the moment the configuration
-- freezes, which is the last point at which the answer can still be supplied.
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

    IF NEW.status = 'PUBLISHED'
        AND NEW.schema_version_id IS NULL
        AND EXISTS (SELECT 1
                    FROM information_request_template_requirement_binding binding
                             JOIN information_request_template_requirement requirement
                                  ON requirement.id = binding.template_requirement_id
                    WHERE binding.template_version_id = NEW.id
                      AND requirement.requirement_type = 'FIELD')
    THEN
        RAISE EXCEPTION 'an information request template version that requests field data must name the schema version it resolves against';
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
