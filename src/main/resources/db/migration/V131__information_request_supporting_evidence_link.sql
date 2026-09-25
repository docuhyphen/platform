CREATE TABLE information_request_supporting_evidence_link
(
    id                        uuid        NOT NULL,
    information_request_id    uuid        NOT NULL,
    supported_requirement_id  uuid        NOT NULL,
    supporting_requirement_id uuid        NOT NULL,
    template_evidence_link_id uuid        NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_supporting_evidence_link_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_supporting_evidence_link_supported_fkey
        FOREIGN KEY (supported_requirement_id, information_request_id)
            REFERENCES information_request_requirement (id, information_request_id),
    CONSTRAINT information_request_supporting_evidence_link_supporting_fkey
        FOREIGN KEY (supporting_requirement_id, information_request_id)
            REFERENCES information_request_requirement (id, information_request_id),
    CONSTRAINT information_request_supporting_evidence_link_template_fkey
        FOREIGN KEY (template_evidence_link_id) REFERENCES information_request_template_binding_evidence_link (id),
    CONSTRAINT ck_information_request_supporting_evidence_link_distinct CHECK (
        supported_requirement_id <> supporting_requirement_id
        ),
    CONSTRAINT ux_information_request_supporting_evidence_link_pair
        UNIQUE (supported_requirement_id, supporting_requirement_id)
);

CREATE INDEX ix_information_request_supporting_evidence_link_request
    ON information_request_supporting_evidence_link (information_request_id);

CREATE INDEX ix_information_request_supporting_evidence_link_supporting
    ON information_request_supporting_evidence_link (supporting_requirement_id);

CREATE FUNCTION information_request_supporting_evidence_link_guard()
    RETURNS TRIGGER AS
$$
DECLARE
    supported_binding  uuid;
    supporting_binding uuid;
    linked_binding     uuid;
    linked_supporting  uuid;
BEGIN
    SELECT source_template_binding_id INTO supported_binding
    FROM information_request_requirement
    WHERE id = NEW.supported_requirement_id AND information_request_id = NEW.information_request_id;

    SELECT source_template_binding_id INTO supporting_binding
    FROM information_request_requirement
    WHERE id = NEW.supporting_requirement_id AND information_request_id = NEW.information_request_id;

    IF supported_binding IS NULL OR supporting_binding IS NULL THEN
        RETURN NEW;
    END IF;

    SELECT template_binding_id, supporting_template_binding_id INTO linked_binding, linked_supporting
    FROM information_request_template_binding_evidence_link
    WHERE id = NEW.template_evidence_link_id;

    IF linked_binding IS DISTINCT FROM supported_binding OR linked_supporting IS DISTINCT FROM supporting_binding THEN
        RAISE EXCEPTION 'a supporting evidence link follows its Template link between the exact bindings of its request';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_supporting_evidence_link_write
    BEFORE INSERT
    ON information_request_supporting_evidence_link
    FOR EACH ROW
EXECUTE FUNCTION information_request_supporting_evidence_link_guard();

CREATE TRIGGER information_request_supporting_evidence_link_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_supporting_evidence_link
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
