CREATE TABLE information_request_clock_policy
(
    id                        uuid         NOT NULL,
    owner_type                VARCHAR(32)  NOT NULL,
    owner_organization_id     uuid,
    owner_user_id             uuid,
    policy_key                VARCHAR(128) NOT NULL,
    display_name              VARCHAR(255) NOT NULL,
    created_by_principal_kind VARCHAR(32)  NOT NULL,
    created_by_principal_id   uuid         NOT NULL,
    created_at                TIMESTAMPTZ  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_clock_policy_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_clock_policy_organization_fkey
        FOREIGN KEY (owner_organization_id) REFERENCES organization (id),
    CONSTRAINT information_request_clock_policy_user_fkey
        FOREIGN KEY (owner_user_id) REFERENCES app_user (id),
    CONSTRAINT ck_information_request_clock_policy_owner CHECK (
        (owner_type = 'ORGANIZATION' AND owner_organization_id IS NOT NULL AND owner_user_id IS NULL) OR
        (owner_type = 'USER' AND owner_user_id IS NOT NULL AND owner_organization_id IS NULL)
        ),
    CONSTRAINT ck_information_request_clock_policy_key CHECK (policy_key ~ '^[a-z0-9][a-z0-9._-]*$'),
    CONSTRAINT ck_information_request_clock_policy_name CHECK (BTRIM(display_name) <> '')
);

CREATE UNIQUE INDEX ux_information_request_clock_policy_organization_key
    ON information_request_clock_policy (owner_organization_id, policy_key)
    WHERE owner_type = 'ORGANIZATION';

CREATE UNIQUE INDEX ux_information_request_clock_policy_user_key
    ON information_request_clock_policy (owner_user_id, policy_key)
    WHERE owner_type = 'USER';

CREATE TABLE information_request_clock_policy_version
(
    id                          uuid        NOT NULL,
    policy_id                   uuid        NOT NULL,
    version_number              INTEGER     NOT NULL,
    clock_type                  VARCHAR(16) NOT NULL,
    business_timezone           VARCHAR(64) NOT NULL,
    standard_duration_minutes   INTEGER     NOT NULL,
    urgent_duration_minutes     INTEGER     NOT NULL,
    escalation_after_minutes    INTEGER,
    due_effect                  VARCHAR(32) NOT NULL,
    published_by_principal_kind VARCHAR(32) NOT NULL,
    published_by_principal_id   uuid        NOT NULL,
    published_at                TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT information_request_clock_policy_version_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_clock_policy_version_policy_fkey
        FOREIGN KEY (policy_id) REFERENCES information_request_clock_policy (id),
    CONSTRAINT ux_information_request_clock_policy_version_number UNIQUE (policy_id, version_number),
    CONSTRAINT ck_information_request_clock_policy_version_number CHECK (version_number >= 1),
    CONSTRAINT ck_information_request_clock_policy_version_type CHECK (clock_type IN ('CALENDAR', 'BUSINESS')),
    CONSTRAINT ck_information_request_clock_policy_version_timezone CHECK (BTRIM(business_timezone) <> ''),
    CONSTRAINT ck_information_request_clock_policy_version_duration CHECK (
        urgent_duration_minutes > 0 AND standard_duration_minutes >= urgent_duration_minutes
        ),
    CONSTRAINT ck_information_request_clock_policy_version_escalation CHECK (
        escalation_after_minutes IS NULL OR escalation_after_minutes >= 0
        ),
    CONSTRAINT ck_information_request_clock_policy_version_effect CHECK (due_effect IN ('MARK_OVERDUE', 'EXPIRE_REQUEST'))
);

CREATE TABLE information_request_clock_policy_period
(
    id                uuid     NOT NULL,
    policy_version_id uuid     NOT NULL,
    day_of_week       SMALLINT NOT NULL,
    start_minute      INTEGER  NOT NULL,
    end_minute        INTEGER  NOT NULL,
    CONSTRAINT information_request_clock_policy_period_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_clock_policy_period_version_fkey
        FOREIGN KEY (policy_version_id) REFERENCES information_request_clock_policy_version (id),
    CONSTRAINT ck_information_request_clock_policy_period_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT ck_information_request_clock_policy_period_window CHECK (
        start_minute >= 0 AND end_minute <= 1440 AND start_minute < end_minute
        )
);

CREATE INDEX ix_information_request_clock_policy_period_version
    ON information_request_clock_policy_period (policy_version_id, day_of_week, start_minute);

CREATE TABLE information_request_clock_policy_holiday
(
    id                uuid NOT NULL,
    policy_version_id uuid NOT NULL,
    holiday_date      DATE NOT NULL,
    CONSTRAINT information_request_clock_policy_holiday_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_clock_policy_holiday_version_fkey
        FOREIGN KEY (policy_version_id) REFERENCES information_request_clock_policy_version (id),
    CONSTRAINT ux_information_request_clock_policy_holiday_date UNIQUE (policy_version_id, holiday_date)
);

CREATE TABLE information_request_clock_policy_reminder
(
    id                 uuid    NOT NULL,
    policy_version_id  uuid    NOT NULL,
    reminder_ordinal   INTEGER NOT NULL,
    minutes_before_due INTEGER NOT NULL,
    CONSTRAINT information_request_clock_policy_reminder_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_clock_policy_reminder_version_fkey
        FOREIGN KEY (policy_version_id) REFERENCES information_request_clock_policy_version (id),
    CONSTRAINT ux_information_request_clock_policy_reminder_ordinal UNIQUE (policy_version_id, reminder_ordinal),
    CONSTRAINT ux_information_request_clock_policy_reminder_minutes UNIQUE (policy_version_id, minutes_before_due),
    CONSTRAINT ck_information_request_clock_policy_reminder_ordinal CHECK (reminder_ordinal >= 1),
    CONSTRAINT ck_information_request_clock_policy_reminder_minutes CHECK (minutes_before_due > 0)
);

CREATE TRIGGER information_request_clock_policy_version_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_clock_policy_version
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_clock_policy_period_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_clock_policy_period
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_clock_policy_holiday_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_clock_policy_holiday
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TRIGGER information_request_clock_policy_reminder_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_clock_policy_reminder
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();

CREATE TABLE information_request_clock
(
    id                        uuid        NOT NULL,
    information_request_id    uuid        NOT NULL,
    clock_key                 VARCHAR(64) NOT NULL,
    policy_version_id         uuid        NOT NULL,
    urgency                   VARCHAR(16) NOT NULL,
    received_at               TIMESTAMPTZ NOT NULL,
    state                     VARCHAR(16) NOT NULL,
    due_at                    TIMESTAMPTZ NOT NULL,
    due_cycle                 INTEGER     NOT NULL,
    remaining_seconds         BIGINT,
    next_point_at             TIMESTAMPTZ,
    overdue_at                TIMESTAMPTZ,
    stopped_at                TIMESTAMPTZ,
    clock_revision            BIGINT      NOT NULL,
    started_by_principal_kind VARCHAR(32) NOT NULL,
    started_by_principal_id   uuid        NOT NULL,
    started_at                TIMESTAMPTZ NOT NULL,
    CONSTRAINT information_request_clock_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_clock_request_fkey
        FOREIGN KEY (information_request_id) REFERENCES information_request (id),
    CONSTRAINT information_request_clock_version_fkey
        FOREIGN KEY (policy_version_id) REFERENCES information_request_clock_policy_version (id),
    CONSTRAINT ux_information_request_clock_key UNIQUE (information_request_id, clock_key),
    CONSTRAINT uq_information_request_clock_request UNIQUE (id, information_request_id),
    CONSTRAINT ck_information_request_clock_key CHECK (clock_key ~ '^[a-z0-9][a-z0-9._-]*$'),
    CONSTRAINT ck_information_request_clock_urgency CHECK (urgency IN ('STANDARD', 'URGENT')),
    CONSTRAINT ck_information_request_clock_state CHECK (state IN ('RUNNING', 'PAUSED', 'STOPPED')),
    CONSTRAINT ck_information_request_clock_pause CHECK ((state = 'PAUSED') = (remaining_seconds IS NOT NULL)),
    CONSTRAINT ck_information_request_clock_remaining CHECK (remaining_seconds IS NULL OR remaining_seconds >= 0),
    CONSTRAINT ck_information_request_clock_stop CHECK ((state = 'STOPPED') = (stopped_at IS NOT NULL)),
    CONSTRAINT ck_information_request_clock_cycle CHECK (due_cycle >= 0),
    CONSTRAINT ck_information_request_clock_revision_floor CHECK (clock_revision >= 1)
);

CREATE INDEX ix_information_request_clock_next_point
    ON information_request_clock (next_point_at)
    WHERE state = 'RUNNING' AND next_point_at IS NOT NULL;

CREATE FUNCTION information_request_clock_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'information request history is append-only';
    END IF;
    IF NEW.information_request_id IS DISTINCT FROM OLD.information_request_id OR
       NEW.clock_key IS DISTINCT FROM OLD.clock_key OR
       NEW.policy_version_id IS DISTINCT FROM OLD.policy_version_id OR
       NEW.urgency IS DISTINCT FROM OLD.urgency OR
       NEW.received_at IS DISTINCT FROM OLD.received_at OR
       NEW.started_by_principal_kind IS DISTINCT FROM OLD.started_by_principal_kind OR
       NEW.started_by_principal_id IS DISTINCT FROM OLD.started_by_principal_id OR
       NEW.started_at IS DISTINCT FROM OLD.started_at THEN
        RAISE EXCEPTION 'a request clock keeps its frozen inputs';
    END IF;
    IF NEW.clock_revision < OLD.clock_revision THEN
        RAISE EXCEPTION 'a request clock revision never moves backwards';
    END IF;
    IF NEW.due_cycle < OLD.due_cycle OR (OLD.state = 'STOPPED' AND NEW.state <> 'STOPPED') THEN
        RAISE EXCEPTION 'a stopped request clock stays stopped and its due cycle only advances';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER information_request_clock_guard_write
    BEFORE UPDATE OR DELETE
    ON information_request_clock
    FOR EACH ROW
EXECUTE FUNCTION information_request_clock_guard();

CREATE TABLE information_request_clock_event
(
    id                         uuid        NOT NULL,
    clock_id                   uuid        NOT NULL,
    information_request_id     uuid        NOT NULL,
    event_number               INTEGER     NOT NULL,
    event_kind                 VARCHAR(16) NOT NULL,
    due_cycle                  INTEGER     NOT NULL,
    point_ordinal              INTEGER,
    reason_code                VARCHAR(128),
    inputs_json                TEXT        NOT NULL,
    due_at                     TIMESTAMPTZ,
    occurred_at                TIMESTAMPTZ NOT NULL,
    recorded_by_principal_kind VARCHAR(32) NOT NULL,
    recorded_by_principal_id   uuid        NOT NULL,
    CONSTRAINT information_request_clock_event_pkey PRIMARY KEY (id),
    CONSTRAINT information_request_clock_event_clock_fkey
        FOREIGN KEY (clock_id, information_request_id) REFERENCES information_request_clock (id, information_request_id),
    CONSTRAINT ux_information_request_clock_event_number UNIQUE (clock_id, event_number),
    CONSTRAINT ck_information_request_clock_event_number CHECK (event_number >= 1),
    CONSTRAINT ck_information_request_clock_event_kind CHECK (
        event_kind IN ('STARTED', 'PAUSED', 'RESUMED', 'EXTENDED', 'REMINDED', 'OVERDUE', 'ESCALATED', 'EXPIRED', 'STOPPED')
        ),
    CONSTRAINT ck_information_request_clock_event_cycle CHECK (due_cycle >= 0),
    CONSTRAINT ck_information_request_clock_event_ordinal CHECK ((event_kind = 'REMINDED') = (point_ordinal IS NOT NULL))
);

CREATE UNIQUE INDEX ux_information_request_clock_event_point
    ON information_request_clock_event (clock_id, event_kind, due_cycle, COALESCE(point_ordinal, 0))
    WHERE event_kind IN ('STARTED', 'REMINDED', 'OVERDUE', 'ESCALATED', 'EXPIRED', 'STOPPED');

CREATE INDEX ix_information_request_clock_event_request
    ON information_request_clock_event (information_request_id, occurred_at);

CREATE TRIGGER information_request_clock_event_append_only
    BEFORE UPDATE OR DELETE
    ON information_request_clock_event
    FOR EACH ROW
EXECUTE FUNCTION information_request_append_only_guard();
