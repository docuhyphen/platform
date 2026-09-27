CREATE TABLE information_request_privacy_request
(
    id                         uuid          NOT NULL,
    owner_kind                 VARCHAR(32)   NOT NULL,
    owner_id                   uuid          NOT NULL,
    subject_identity_ref_id    uuid          NOT NULL,
    request_kind               VARCHAR(32)   NOT NULL,
    purpose_key                VARCHAR(128)  NOT NULL,
    policy_basis_key           VARCHAR(128)  NOT NULL,
    state                      VARCHAR(32)   NOT NULL,
    refusal_code               VARCHAR(128),
    refusal_detail             VARCHAR(512),
    record_export_id           uuid,
    recorded_by_principal_kind VARCHAR(32)   NOT NULL,
    recorded_by_principal_id   uuid          NOT NULL,
    recorded_at                TIMESTAMPTZ   NOT NULL,
    completed_at               TIMESTAMPTZ,
    request_revision           BIGINT        NOT NULL DEFAULT 1,
    CONSTRAINT information_request_privacy_request_pkey PRIMARY KEY (id),
    CONSTRAINT fk_information_request_privacy_request_subject FOREIGN KEY (subject_identity_ref_id)
        REFERENCES subject_identity_ref (id),
    CONSTRAINT ck_information_request_privacy_request_owner CHECK (owner_kind IN ('ORGANIZATION', 'USER')),
    CONSTRAINT ck_information_request_privacy_request_kind CHECK (
        request_kind IN ('ACCESS', 'EXPORT', 'CORRECTION', 'RESTRICTION', 'DELETION')
        ),
    CONSTRAINT ck_information_request_privacy_request_keys CHECK (
        purpose_key ~ '^[a-z][a-z0-9_.-]{0,127}$' AND policy_basis_key ~ '^[a-z][a-z0-9_.-]{0,127}$'
        ),
    CONSTRAINT ck_information_request_privacy_request_state CHECK (
        (state = 'RECORDED' AND completed_at IS NULL AND refusal_code IS NULL) OR
        (state = 'COMPLETED' AND completed_at IS NOT NULL AND refusal_code IS NULL) OR
        (state = 'REFUSED' AND completed_at IS NOT NULL AND refusal_code IS NOT NULL)
        ),
    CONSTRAINT ck_information_request_privacy_request_export CHECK (
        record_export_id IS NULL OR request_kind IN ('ACCESS', 'EXPORT')
        ),
    CONSTRAINT ck_information_request_privacy_request_revision CHECK (request_revision >= 1),
    CONSTRAINT ck_information_request_privacy_request_principal CHECK (
        recorded_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                                       'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_information_request_privacy_request_owner
    ON information_request_privacy_request (owner_kind, owner_id, recorded_at);

CREATE INDEX ix_information_request_privacy_request_subject
    ON information_request_privacy_request (subject_identity_ref_id, recorded_at);

CREATE FUNCTION information_request_privacy_request_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'a privacy request is never deleted';
    END IF;
    IF OLD.state <> 'RECORDED' THEN
        RAISE EXCEPTION 'a finished privacy request is immutable';
    END IF;
    IF NEW.id <> OLD.id
        OR NEW.owner_kind <> OLD.owner_kind
        OR NEW.owner_id <> OLD.owner_id
        OR NEW.subject_identity_ref_id <> OLD.subject_identity_ref_id
        OR NEW.request_kind <> OLD.request_kind
        OR NEW.purpose_key <> OLD.purpose_key
        OR NEW.policy_basis_key <> OLD.policy_basis_key
        OR NEW.recorded_by_principal_kind <> OLD.recorded_by_principal_kind
        OR NEW.recorded_by_principal_id <> OLD.recorded_by_principal_id
        OR NEW.recorded_at <> OLD.recorded_at
    THEN
        RAISE EXCEPTION 'a privacy request keeps the identity it was recorded with';
    END IF;
    IF NEW.request_revision <> OLD.request_revision + 1 THEN
        RAISE EXCEPTION 'a privacy request revision advances by one';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_privacy_request_guard_write
    BEFORE UPDATE OR DELETE
    ON information_request_privacy_request
    FOR EACH ROW
EXECUTE FUNCTION information_request_privacy_request_guard();

CREATE TABLE information_request_privacy_target
(
    privacy_request_id     uuid         NOT NULL,
    information_request_id uuid         NOT NULL,
    outcome                VARCHAR(32)  NOT NULL,
    reason_code            VARCHAR(128),
    disposal_claim_id      uuid,
    recorded_at            TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_privacy_target_pkey PRIMARY KEY (privacy_request_id, information_request_id),
    CONSTRAINT fk_information_request_privacy_target_request FOREIGN KEY (privacy_request_id)
        REFERENCES information_request_privacy_request (id),
    CONSTRAINT fk_information_request_privacy_target_claim FOREIGN KEY (disposal_claim_id)
        REFERENCES record_disposal_claim (id),
    CONSTRAINT ck_information_request_privacy_target_outcome CHECK (
        outcome IN ('EXPORTED', 'CORRECTED', 'RESTRICTED', 'DISPOSAL_CLAIMED', 'REFUSED')
        ),
    CONSTRAINT ck_information_request_privacy_target_reason CHECK ((outcome = 'REFUSED') = (reason_code IS NOT NULL)),
    CONSTRAINT ck_information_request_privacy_target_claim CHECK ((outcome = 'DISPOSAL_CLAIMED') = (disposal_claim_id IS NOT NULL))
);

CREATE TRIGGER information_request_privacy_target_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_privacy_target
    FOR EACH ROW
EXECUTE FUNCTION record_preservation_append_only_guard();

ALTER TABLE record_disposal_claim
    ADD CONSTRAINT fk_record_disposal_claim_privacy_request FOREIGN KEY (privacy_request_id)
        REFERENCES information_request_privacy_request (id);

CREATE TABLE information_request_subject_restriction
(
    id                        uuid        NOT NULL,
    owner_kind                VARCHAR(32) NOT NULL,
    owner_id                  uuid        NOT NULL,
    subject_identity_ref_id   uuid        NOT NULL,
    privacy_request_id        uuid        NOT NULL,
    restricted_at             TIMESTAMPTZ NOT NULL,
    lifted_at                 TIMESTAMPTZ,
    lifted_by_principal_kind  VARCHAR(32),
    lifted_by_principal_id    uuid,
    lift_reason_code          VARCHAR(128),
    CONSTRAINT information_request_subject_restriction_pkey PRIMARY KEY (id),
    CONSTRAINT fk_information_request_subject_restriction_subject FOREIGN KEY (subject_identity_ref_id)
        REFERENCES subject_identity_ref (id),
    CONSTRAINT fk_information_request_subject_restriction_privacy FOREIGN KEY (privacy_request_id)
        REFERENCES information_request_privacy_request (id),
    CONSTRAINT ck_information_request_subject_restriction_owner CHECK (owner_kind IN ('ORGANIZATION', 'USER')),
    CONSTRAINT ck_information_request_subject_restriction_lift CHECK (
        (lifted_at IS NULL AND lifted_by_principal_kind IS NULL AND lifted_by_principal_id IS NULL AND lift_reason_code IS NULL) OR
        (lifted_at IS NOT NULL AND lifted_by_principal_kind IS NOT NULL AND lifted_by_principal_id IS NOT NULL AND
         lift_reason_code IS NOT NULL)
        )
);

CREATE UNIQUE INDEX ux_information_request_subject_restriction_active
    ON information_request_subject_restriction (owner_kind, owner_id, subject_identity_ref_id)
    WHERE lifted_at IS NULL;

CREATE FUNCTION information_request_subject_restriction_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'a subject restriction is never deleted';
    END IF;
    IF OLD.lifted_at IS NOT NULL THEN
        RAISE EXCEPTION 'a lifted subject restriction is immutable';
    END IF;
    IF NEW.id <> OLD.id
        OR NEW.owner_kind <> OLD.owner_kind
        OR NEW.owner_id <> OLD.owner_id
        OR NEW.subject_identity_ref_id <> OLD.subject_identity_ref_id
        OR NEW.privacy_request_id <> OLD.privacy_request_id
        OR NEW.restricted_at <> OLD.restricted_at
    THEN
        RAISE EXCEPTION 'a subject restriction keeps the identity it was placed with';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_subject_restriction_guard_write
    BEFORE UPDATE OR DELETE
    ON information_request_subject_restriction
    FOR EACH ROW
EXECUTE FUNCTION information_request_subject_restriction_guard();

CREATE TABLE information_request_item_correction
(
    id                         uuid          NOT NULL,
    information_request_id     uuid          NOT NULL,
    package_id                 uuid          NOT NULL,
    submission_item_id         uuid          NOT NULL,
    privacy_request_id         uuid,
    corrected_value_json       TEXT,
    corrected_narrative        TEXT,
    reason_code                VARCHAR(128)  NOT NULL,
    recorded_by_principal_kind VARCHAR(32)   NOT NULL,
    recorded_by_principal_id   uuid          NOT NULL,
    recorded_at                TIMESTAMPTZ   NOT NULL,
    CONSTRAINT information_request_item_correction_pkey PRIMARY KEY (id),
    CONSTRAINT fk_information_request_item_correction_item FOREIGN KEY (submission_item_id, package_id)
        REFERENCES information_request_submission_item (id, package_id) ON DELETE CASCADE,
    CONSTRAINT fk_information_request_item_correction_request FOREIGN KEY (information_request_id)
        REFERENCES information_request (id) ON DELETE CASCADE,
    CONSTRAINT fk_information_request_item_correction_privacy FOREIGN KEY (privacy_request_id)
        REFERENCES information_request_privacy_request (id),
    CONSTRAINT ck_information_request_item_correction_content CHECK (
        corrected_value_json IS NOT NULL OR corrected_narrative IS NOT NULL
        ),
    CONSTRAINT ck_information_request_item_correction_reason CHECK (BTRIM(reason_code) <> ''),
    CONSTRAINT ck_information_request_item_correction_principal CHECK (
        recorded_by_principal_kind IN ('USER', 'PARTICIPANT', 'PRINCIPAL_GROUP', 'ORGANIZATION', 'APPLICATION',
                                       'SERVICE_ACCOUNT', 'PUBLIC_LINK')
        )
);

CREATE INDEX ix_information_request_item_correction_item
    ON information_request_item_correction (submission_item_id, recorded_at);

CREATE FUNCTION information_request_item_correction_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NOT EXISTS (SELECT 1
                   FROM information_request_submission_item item
                   WHERE item.id = NEW.submission_item_id
                     AND item.package_id = NEW.package_id
                     AND item.information_request_id = NEW.information_request_id)
    THEN
        RAISE EXCEPTION 'a correction names an item of a package of its request';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_item_correction_guard_write
    BEFORE INSERT
    ON information_request_item_correction
    FOR EACH ROW
EXECUTE FUNCTION information_request_item_correction_guard();

CREATE TRIGGER information_request_item_correction_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_item_correction
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER record_disposal_reference_item_correction
    BEFORE INSERT
    ON information_request_item_correction
    FOR EACH ROW
EXECUTE FUNCTION record_disposal_reference_guard('INFORMATION_REQUEST', 'information_request_id');
