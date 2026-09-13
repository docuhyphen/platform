-- A date-time answer must name one moment.
--
-- The released schema stores a date-time in a naive TIMESTAMP and the writer dropped the submitted
-- offset while keeping the wall clock, so the same moment written at two offsets stored as two
-- different values and a reading entered at +02:00 came back as if it had been UTC. Neither the
-- stored value nor the column said which reading was meant.
--
-- The column becomes an explicit instant, and the offset the responder submitted moves into a
-- column of its own so the answer can still be presented the way it was given.

-- Existing rows carry no offset information anywhere, so the only honest reading of the naive
-- timestamp is UTC. The offset column stays null for them, which is exactly what it means: the
-- submitted offset was never recorded.
ALTER TABLE field_value
    ALTER COLUMN datetime_value TYPE TIMESTAMP(6) WITH TIME ZONE
        USING datetime_value AT TIME ZONE 'UTC';

ALTER TABLE field_value
    ADD COLUMN datetime_offset_minutes INTEGER;

-- An offset belongs to a moment, and only offsets a reading can actually carry are accepted.
ALTER TABLE field_value
    ADD CONSTRAINT ck_field_value_datetime_offset
        CHECK (datetime_offset_minutes IS NULL
               OR (datetime_value IS NOT NULL AND datetime_offset_minutes BETWEEN -1080 AND 1080));
