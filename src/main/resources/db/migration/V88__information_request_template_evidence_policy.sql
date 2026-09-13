-- The policy a requested document is judged by, stated per version.
--
-- A requirement that asks for a document is not answered by wording alone. How many files are
-- expected, what they may be, how large, how recent, what they have to cover, what has to be stated
-- about them, what may stand in for them, and what happens when they fall short are all part of the
-- question. None of that belongs to the stable requirement: a later version may legitimately ask
-- the same thing more or less strictly and still be asking the same thing. So the policy is held
-- per binding and freezes with the version that holds it.
--
-- Only a requested document carries one. Counts, sizes, pages, issuance, coverage, and technical
-- conformance describe files, and attaching them to a typed answer or an assertion would state a
-- rule that nothing could ever evaluate. A trigger holds that line rather than a comment.
--
-- Restrictions that name accepted values are a set keyed by which attribute they restrict, not one
-- column per attribute, so making a further attribute restrictable needs no table of its own.
-- Substitute evidence is a flat set of alternatives: neither end of it may itself be chained, so
-- resolving what may stand in for what never walks a graph and no cycle is reachable.

-- ── Evidence policy of one binding ───────────────────────────────────────────────────────────
CREATE TABLE information_request_template_evidence_policy
(
    id                              uuid        NOT NULL,
    template_binding_id             uuid        NOT NULL,
    template_version_id             uuid        NOT NULL,
    -- How many files answer the requirement. An absent maximum is unbounded.
    minimum_file_count              INTEGER     NOT NULL,
    maximum_file_count              INTEGER,
    -- Per file, then across the whole collection answering the requirement.
    maximum_file_size_bytes         BIGINT,
    maximum_total_size_bytes        BIGINT,
    -- Per file, like maximum_file_size_bytes. How many pages a collection runs to in total is a
    -- consequence of the file count and these bounds rather than a separate rule.
    minimum_page_count              INTEGER,
    maximum_page_count              INTEGER,
    -- What has to be stated about a file. Each of these draws from one vocabulary, because they are
    -- the same decision asked about different attributes: never captured, captured when available,
    -- or always required. Which values are then accepted is a set, not a column.
    issuer_requirement              VARCHAR(32) NOT NULL,
    jurisdiction_requirement        VARCHAR(32) NOT NULL,
    language_requirement            VARCHAR(32) NOT NULL,
    issue_date_requirement          VARCHAR(32) NOT NULL,
    expiry_date_requirement         VARCHAR(32) NOT NULL,
    coverage_period_requirement     VARCHAR(32) NOT NULL,
    certification_requirement       VARCHAR(32) NOT NULL,
    signature_requirement           VARCHAR(32) NOT NULL,
    -- Bounds measured against the dates above. Each is meaningless without the date it counts from,
    -- so each is admitted only when that date is required.
    maximum_issue_age_days          INTEGER,
    minimum_remaining_validity_days INTEGER,
    minimum_coverage_days           INTEGER,
    -- Whether several files covering several periods have to leave no gap between them.
    coverage_continuity_required    BOOLEAN     NOT NULL,
    -- Whether the requirement may be resolved without the evidence, and what reaches that outcome.
    waiver_policy                   VARCHAR(32) NOT NULL,
    -- Whether a file that fails technical conformance is refused outright or may still be
    -- submitted for a reviewer to decide on.
    conformance_policy              VARCHAR(32) NOT NULL,
    CONSTRAINT information_request_template_evidence_policy_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_evidence_policy_binding_fkey
        FOREIGN KEY (template_binding_id, template_version_id)
            REFERENCES information_request_template_requirement_binding (id, template_version_id),
    -- One document is judged by one policy. Two would both be in force and disagree.
    CONSTRAINT ux_request_template_evidence_policy_binding UNIQUE (template_binding_id),
    CONSTRAINT ck_request_template_evidence_file_count CHECK (
        minimum_file_count >= 1 AND
        (maximum_file_count IS NULL OR maximum_file_count >= minimum_file_count)
        ),
    -- A collection cannot be allowed less room in total than one of its files.
    CONSTRAINT ck_request_template_evidence_file_size CHECK (
        (maximum_file_size_bytes IS NULL OR maximum_file_size_bytes > 0) AND
        (maximum_total_size_bytes IS NULL OR maximum_total_size_bytes > 0) AND
        (maximum_total_size_bytes IS NULL OR maximum_file_size_bytes IS NULL OR
         maximum_total_size_bytes >= maximum_file_size_bytes)
        ),
    CONSTRAINT ck_request_template_evidence_page_count CHECK (
        (minimum_page_count IS NULL OR minimum_page_count >= 1) AND
        (maximum_page_count IS NULL OR maximum_page_count >= 1) AND
        (maximum_page_count IS NULL OR minimum_page_count IS NULL OR
         maximum_page_count >= minimum_page_count)
        ),
    CONSTRAINT ck_request_template_evidence_attribute_requirement CHECK (
        issuer_requirement IN ('NOT_CAPTURED', 'OPTIONAL', 'REQUIRED') AND
        jurisdiction_requirement IN ('NOT_CAPTURED', 'OPTIONAL', 'REQUIRED') AND
        language_requirement IN ('NOT_CAPTURED', 'OPTIONAL', 'REQUIRED') AND
        issue_date_requirement IN ('NOT_CAPTURED', 'OPTIONAL', 'REQUIRED') AND
        expiry_date_requirement IN ('NOT_CAPTURED', 'OPTIONAL', 'REQUIRED') AND
        coverage_period_requirement IN ('NOT_CAPTURED', 'OPTIONAL', 'REQUIRED') AND
        certification_requirement IN ('NOT_CAPTURED', 'OPTIONAL', 'REQUIRED') AND
        signature_requirement IN ('NOT_CAPTURED', 'OPTIONAL', 'REQUIRED')
        ),
    -- An age is measured from an issue date. Bounding it while the date is optional or never
    -- captured leaves a rule that cannot be evaluated for the files that omit the date.
    CONSTRAINT ck_request_template_evidence_freshness CHECK (
        maximum_issue_age_days IS NULL OR
        (maximum_issue_age_days >= 1 AND issue_date_requirement = 'REQUIRED')
        ),
    CONSTRAINT ck_request_template_evidence_validity CHECK (
        minimum_remaining_validity_days IS NULL OR
        (minimum_remaining_validity_days >= 0 AND expiry_date_requirement = 'REQUIRED')
        ),
    CONSTRAINT ck_request_template_evidence_coverage_bounds CHECK (
        ((minimum_coverage_days IS NULL AND coverage_continuity_required IS FALSE) OR
         coverage_period_requirement = 'REQUIRED') AND
        (minimum_coverage_days IS NULL OR minimum_coverage_days >= 1)
        ),
    CONSTRAINT ck_request_template_evidence_waiver_policy CHECK (
        waiver_policy IN ('NOT_PERMITTED', 'RESPONDENT_DECLARED', 'REVIEW_APPROVAL_REQUIRED')
        ),
    CONSTRAINT ck_request_template_evidence_conformance_policy CHECK (
        conformance_policy IN ('CONFORMANCE_REQUIRED', 'DEFICIENCY_REVIEWABLE')
        ),
    -- Composite key so the accepted-value set below can be held to a policy of its own version.
    CONSTRAINT uq_request_template_evidence_policy_version UNIQUE (id, template_version_id)
);

CREATE INDEX ix_request_template_evidence_policy_version
    ON information_request_template_evidence_policy (template_version_id);

-- ── Accepted values for one evidence attribute ───────────────────────────────────────────────
-- An empty set means the attribute is unrestricted. CONTENT_TYPE is restrictable without being one
-- of the captured attributes above, because the type of a file is read from the file rather than
-- stated by the party supplying it.
CREATE TABLE information_request_template_evidence_accepted_value
(
    id                  uuid         NOT NULL,
    evidence_policy_id  uuid         NOT NULL,
    template_version_id uuid         NOT NULL,
    attribute           VARCHAR(32)  NOT NULL,
    accepted_value      VARCHAR(255) NOT NULL,
    CONSTRAINT information_request_template_evidence_accepted_value_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_evidence_accepted_policy_fkey
        FOREIGN KEY (evidence_policy_id, template_version_id)
            REFERENCES information_request_template_evidence_policy (id, template_version_id),
    CONSTRAINT ck_request_template_evidence_accepted_attribute CHECK (
        attribute IN ('CONTENT_TYPE', 'ISSUER', 'JURISDICTION', 'LANGUAGE')
        ),
    CONSTRAINT ck_request_template_evidence_accepted_value CHECK (BTRIM(accepted_value) <> ''),
    CONSTRAINT ux_request_template_evidence_accepted_value
        UNIQUE (evidence_policy_id, attribute, accepted_value)
);

CREATE INDEX ix_request_template_evidence_accepted_version
    ON information_request_template_evidence_accepted_value (template_version_id);

-- ── Substitute evidence for one requested document ───────────────────────────────────────────
CREATE TABLE information_request_template_binding_substitute
(
    id                             uuid NOT NULL,
    template_binding_id            uuid NOT NULL,
    substitute_template_binding_id uuid NOT NULL,
    template_version_id            uuid NOT NULL,
    CONSTRAINT information_request_template_binding_substitute_pkey PRIMARY KEY (id),
    CONSTRAINT request_template_substitute_binding_fkey
        FOREIGN KEY (template_binding_id, template_version_id)
            REFERENCES information_request_template_requirement_binding (id, template_version_id),
    CONSTRAINT request_template_substitute_alternative_fkey
        FOREIGN KEY (substitute_template_binding_id, template_version_id)
            REFERENCES information_request_template_requirement_binding (id, template_version_id),
    -- Nothing stands in for itself, so the shortest chain is unreachable before the flatness rule
    -- below is consulted at all.
    CONSTRAINT ck_request_template_substitute_distinct CHECK (
        template_binding_id <> substitute_template_binding_id
        ),
    CONSTRAINT ux_request_template_substitute_pair
        UNIQUE (template_binding_id, substitute_template_binding_id)
);

CREATE INDEX ix_request_template_substitute_alternative
    ON information_request_template_binding_substitute (substitute_template_binding_id);
CREATE INDEX ix_request_template_substitute_version
    ON information_request_template_binding_substitute (template_version_id);

-- ── An evidence policy belongs to a requested document ───────────────────────────────────────
CREATE FUNCTION request_template_evidence_policy_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF request_template_binding_requirement_type(NEW.template_binding_id) IS DISTINCT FROM 'DOCUMENT' THEN
        RAISE EXCEPTION 'an evidence policy belongs to a requested document';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER request_template_evidence_policy_guard_write
    BEFORE INSERT OR UPDATE
    ON information_request_template_evidence_policy
    FOR EACH ROW
EXECUTE FUNCTION request_template_evidence_policy_guard();

-- ── Substitution is a flat set between two requested documents ───────────────────────────────
CREATE FUNCTION request_template_substitute_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF request_template_binding_requirement_type(NEW.template_binding_id) IS DISTINCT FROM 'DOCUMENT' THEN
        RAISE EXCEPTION 'only a requested document declares substitute evidence';
    END IF;

    IF request_template_binding_requirement_type(NEW.substitute_template_binding_id)
        IS DISTINCT FROM 'DOCUMENT' THEN
        RAISE EXCEPTION 'substitute evidence must be a requested document';
    END IF;

    -- Both directions of a chain are refused, so the set of alternatives for one document is
    -- exactly what it names and resolving it never has to follow a second hop.
    IF EXISTS (SELECT 1
               FROM information_request_template_binding_substitute existing
               WHERE existing.substitute_template_binding_id = NEW.template_binding_id) THEN
        RAISE EXCEPTION 'a requirement that is itself substitute evidence cannot declare substitutes';
    END IF;

    IF EXISTS (SELECT 1
               FROM information_request_template_binding_substitute existing
               WHERE existing.template_binding_id = NEW.substitute_template_binding_id) THEN
        RAISE EXCEPTION 'substitute evidence cannot itself declare substitutes';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER request_template_substitute_guard_write
    BEFORE INSERT OR UPDATE
    ON information_request_template_binding_substitute
    FOR EACH ROW
EXECUTE FUNCTION request_template_substitute_guard();

-- ── All three freeze with the version that holds them ────────────────────────────────────────
-- Each row carries the version of the binding it belongs to, so the guard already written for
-- sections and bindings applies to them unchanged.
CREATE TRIGGER request_template_evidence_policy_freeze_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_evidence_policy
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

CREATE TRIGGER request_template_evidence_accepted_freeze_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_evidence_accepted_value
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

CREATE TRIGGER request_template_substitute_freeze_write
    BEFORE INSERT OR UPDATE OR DELETE
    ON information_request_template_binding_substitute
    FOR EACH ROW
EXECUTE FUNCTION request_template_configuration_guard();

-- ── What a version has to have settled before it freezes ─────────────────────────────────────
-- Publication is the last point at which a missing or self-contradictory answer can still be
-- supplied. The typed-data contract rule already applied here is joined by the document rules,
-- gathered into one function so the version guard states the transition and this states
-- completeness.
CREATE FUNCTION request_template_publication_completeness(
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
