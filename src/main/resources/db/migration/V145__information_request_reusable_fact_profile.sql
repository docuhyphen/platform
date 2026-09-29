ALTER TABLE information_request_accepted_fact
    ADD COLUMN policy_basis_key VARCHAR(128) NOT NULL;

ALTER TABLE information_request_accepted_fact
    ADD CONSTRAINT ck_information_request_accepted_fact_policy_basis
        CHECK (policy_basis_key ~ '^[a-z0-9][a-z0-9._-]{0,127}$');

CREATE TABLE information_request_accepted_fact_evidence
(
    id                            uuid NOT NULL,
    fact_id                       uuid NOT NULL,
    source_submission_evidence_id uuid NOT NULL,
    evidence_version_id           uuid NOT NULL,
    CONSTRAINT information_request_accepted_fact_evidence_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_accepted_fact_evidence_fact_fkey
        FOREIGN KEY (fact_id) REFERENCES information_request_accepted_fact (id) ON DELETE CASCADE,
    CONSTRAINT information_request_accepted_fact_evidence_source_fkey
        FOREIGN KEY (source_submission_evidence_id) REFERENCES information_request_submission_evidence (id),
    CONSTRAINT information_request_accepted_fact_evidence_version_fkey
        FOREIGN KEY (evidence_version_id) REFERENCES information_request_evidence_version (id),
    CONSTRAINT ux_information_request_accepted_fact_evidence_source
        UNIQUE (fact_id, source_submission_evidence_id),
    CONSTRAINT ux_information_request_accepted_fact_evidence_version
        UNIQUE (fact_id, evidence_version_id)
);

CREATE INDEX ix_information_request_accepted_fact_evidence_source
    ON information_request_accepted_fact_evidence (source_submission_evidence_id);

CREATE FUNCTION information_request_accepted_fact_evidence_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_accepted_fact fact
                            JOIN information_request_submission_evidence evidence
                                 ON evidence.id = NEW.source_submission_evidence_id
                            JOIN information_request_submission_item item
                                 ON item.id = evidence.item_id
                            JOIN information_request_submission_supporting_link link
                                 ON link.package_id = evidence.package_id
                                     AND link.supported_requirement_id = fact.source_requirement_id
                                     AND link.supporting_requirement_id = item.information_request_requirement_id
                   WHERE fact.id = NEW.fact_id
                     AND fact.source_package_id = evidence.package_id
                     AND fact.source_information_request_id = evidence.information_request_id
                     AND evidence.evidence_version_id = NEW.evidence_version_id
                     AND evidence.conformance = 'CONFORMING')
    THEN
        RAISE EXCEPTION 'promoted evidence is a conforming submitted version supporting the fact';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_accepted_fact_evidence_guard_write
    BEFORE INSERT
    ON information_request_accepted_fact_evidence
    FOR EACH ROW
EXECUTE FUNCTION information_request_accepted_fact_evidence_guard();

CREATE TRIGGER information_request_accepted_fact_evidence_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_accepted_fact_evidence
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
