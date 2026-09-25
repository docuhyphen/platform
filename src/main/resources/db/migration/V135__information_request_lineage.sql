CREATE TABLE information_request_recurrence
(
    id                        uuid        NOT NULL,
    origin_request_id         uuid        NOT NULL,
    interval_unit             VARCHAR(16) NOT NULL,
    interval_count            INTEGER     NOT NULL,
    first_due_at              TIMESTAMPTZ NOT NULL,
    maximum_occurrences       INTEGER,
    created_by_principal_kind VARCHAR(32) NOT NULL,
    created_by_principal_id   uuid        NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_recurrence_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_recurrence_origin_fkey
        FOREIGN KEY (origin_request_id) REFERENCES information_request (id),
    CONSTRAINT ck_information_request_recurrence_unit CHECK (interval_unit IN ('DAY', 'WEEK', 'MONTH', 'YEAR')),
    CONSTRAINT ck_information_request_recurrence_count CHECK (interval_count >= 1),
    CONSTRAINT ck_information_request_recurrence_maximum CHECK (maximum_occurrences IS NULL OR maximum_occurrences >= 1),
    CONSTRAINT ck_information_request_recurrence_actor CHECK (
        created_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                                      'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        ),
    CONSTRAINT ux_information_request_recurrence_origin UNIQUE (origin_request_id)
);

CREATE TABLE information_request_refresh_rule
(
    id                        uuid         NOT NULL,
    information_request_id    uuid         NOT NULL,
    requirement_key           VARCHAR(128) NOT NULL,
    lead_days                 INTEGER      NOT NULL,
    created_by_principal_kind VARCHAR(32)  NOT NULL,
    created_by_principal_id   uuid         NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_refresh_rule_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_refresh_rule_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT ck_information_request_refresh_rule_key CHECK (BTRIM(requirement_key) <> ''),
    CONSTRAINT ck_information_request_refresh_rule_lead CHECK (lead_days >= 0),
    CONSTRAINT ck_information_request_refresh_rule_actor CHECK (
        created_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                                      'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        ),
    CONSTRAINT ux_information_request_refresh_rule_requirement UNIQUE (information_request_id, requirement_key),
    CONSTRAINT uq_information_request_refresh_rule_request UNIQUE (id, information_request_id)
);

CREATE TABLE information_request_lineage
(
    id                        uuid        NOT NULL,
    successor_request_id      uuid        NOT NULL,
    source_request_id         uuid        NOT NULL,
    source_package_id         uuid,
    lineage_kind              VARCHAR(32) NOT NULL,
    recurrence_id             uuid,
    recurrence_sequence       INTEGER,
    refresh_rule_id           uuid,
    reason_code               VARCHAR(128),
    created_by_principal_kind VARCHAR(32) NOT NULL,
    created_by_principal_id   uuid        NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_lineage_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_lineage_successor_fkey
        FOREIGN KEY (successor_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_lineage_source_fkey
        FOREIGN KEY (source_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_lineage_package_fkey
        FOREIGN KEY (source_package_id, source_request_id)
            REFERENCES information_request_submission_package (id, information_request_id),
    CONSTRAINT information_request_lineage_recurrence_fkey
        FOREIGN KEY (recurrence_id) REFERENCES information_request_recurrence (id),
    CONSTRAINT information_request_lineage_refresh_rule_fkey
        FOREIGN KEY (refresh_rule_id, source_request_id)
            REFERENCES information_request_refresh_rule (id, information_request_id),
    CONSTRAINT ck_information_request_lineage_kind CHECK (
        lineage_kind IN ('SUPPLEMENT', 'RECURRENCE', 'REFRESH', 'SUPERSEDING')
        ),
    CONSTRAINT ck_information_request_lineage_distinct CHECK (successor_request_id <> source_request_id),
    CONSTRAINT ck_information_request_lineage_recurrence CHECK (
        (lineage_kind = 'RECURRENCE') = (recurrence_id IS NOT NULL) AND
        (recurrence_id IS NULL) = (recurrence_sequence IS NULL) AND
        (recurrence_sequence IS NULL OR recurrence_sequence >= 1)
        ),
    CONSTRAINT ck_information_request_lineage_refresh CHECK (refresh_rule_id IS NULL OR lineage_kind = 'REFRESH'),
    CONSTRAINT ck_information_request_lineage_reason CHECK (reason_code IS NULL OR BTRIM(reason_code) <> ''),
    CONSTRAINT ck_information_request_lineage_actor CHECK (
        created_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                                      'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        ),
    CONSTRAINT ux_information_request_lineage_successor UNIQUE (successor_request_id),
    CONSTRAINT ux_information_request_lineage_recurrence UNIQUE (recurrence_id, recurrence_sequence),
    CONSTRAINT uq_information_request_lineage_successor UNIQUE (id, successor_request_id)
);

CREATE INDEX ix_information_request_lineage_source
    ON information_request_lineage (source_request_id, created_at);

CREATE FUNCTION information_request_lineage_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_request successor
                 JOIN information_request source ON source.exchange_id = successor.exchange_id
        WHERE successor.id = NEW.successor_request_id
          AND source.id = NEW.source_request_id
          AND successor.owner_type = source.owner_type
          AND successor.owner_organization_id IS NOT DISTINCT FROM source.owner_organization_id
          AND successor.owner_user_id IS NOT DISTINCT FROM source.owner_user_id
    ) THEN
        RAISE EXCEPTION 'a follow-up request belongs to the Exchange and owner of the request it follows';
    END IF;

    IF NEW.recurrence_id IS NOT NULL AND NOT EXISTS (
        SELECT 1
        FROM information_request_recurrence recurrence
        WHERE recurrence.id = NEW.recurrence_id
          AND (recurrence.origin_request_id = NEW.source_request_id OR EXISTS (
              SELECT 1
              FROM information_request_lineage earlier
              WHERE earlier.recurrence_id = NEW.recurrence_id
                AND earlier.successor_request_id = NEW.source_request_id
          ))
    ) THEN
        RAISE EXCEPTION 'a recurring request follows a request of its own recurrence';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_lineage_write
    BEFORE INSERT
    ON information_request_lineage
    FOR EACH ROW
EXECUTE FUNCTION information_request_lineage_guard();

CREATE TABLE information_request_carry_forward
(
    id                                 uuid        NOT NULL,
    lineage_id                         uuid        NOT NULL,
    information_request_id             uuid        NOT NULL,
    information_request_requirement_id uuid        NOT NULL,
    source_package_id                  uuid        NOT NULL,
    source_item_id                     uuid        NOT NULL,
    decision                           VARCHAR(32) NOT NULL,
    reason_code                        VARCHAR(64),
    created_at                         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_carry_forward_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_carry_forward_lineage_fkey
        FOREIGN KEY (lineage_id, information_request_id)
            REFERENCES information_request_lineage (id, successor_request_id),
    CONSTRAINT information_request_carry_forward_requirement_fkey
        FOREIGN KEY (information_request_requirement_id, information_request_id)
            REFERENCES information_request_requirement (id, information_request_id),
    CONSTRAINT information_request_carry_forward_item_fkey
        FOREIGN KEY (source_item_id) REFERENCES information_request_submission_item (id),
    CONSTRAINT ck_information_request_carry_forward_decision CHECK (decision IN ('OFFERED', 'INVALIDATED')),
    CONSTRAINT ck_information_request_carry_forward_reason CHECK (
        (decision = 'INVALIDATED') = (reason_code IS NOT NULL)
        ),
    CONSTRAINT ux_information_request_carry_forward_requirement UNIQUE (lineage_id, information_request_requirement_id)
);

CREATE FUNCTION information_request_carry_forward_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_request_lineage lineage
                 JOIN information_request_submission_item item ON item.package_id = lineage.source_package_id
        WHERE lineage.id = NEW.lineage_id
          AND lineage.source_package_id = NEW.source_package_id
          AND item.id = NEW.source_item_id
    ) THEN
        RAISE EXCEPTION 'a carry-forward decision names an item of the package its follow-up request preserves';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_carry_forward_write
    BEFORE INSERT
    ON information_request_carry_forward
    FOR EACH ROW
EXECUTE FUNCTION information_request_carry_forward_guard();

CREATE TRIGGER information_request_recurrence_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_recurrence
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_refresh_rule_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_refresh_rule
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_lineage_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_lineage
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_carry_forward_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_carry_forward
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
