-- The root occurrence of an Information Request has one spelling. Rows stored under the earlier
-- spelling are carried to it, and the earlier spelling is refused from now on.
--
-- Requirements and their revisions are append-only, so their guards are held off for the length of
-- this one normalisation and restored before the migration commits.

ALTER TABLE information_request_requirement DISABLE TRIGGER information_request_requirement_append_only;
ALTER TABLE information_request_requirement_revision
    DISABLE TRIGGER information_request_requirement_revision_append_only;

UPDATE information_request_requirement
SET occurrence_path = 'root'
WHERE occurrence_path = '$';

UPDATE information_request_requirement_revision
SET occurrence_path = 'root'
WHERE occurrence_path = '$';

UPDATE information_request_response
SET occurrence_path = 'root'
WHERE occurrence_path = '$';

ALTER TABLE information_request_requirement ENABLE TRIGGER information_request_requirement_append_only;
ALTER TABLE information_request_requirement_revision
    ENABLE TRIGGER information_request_requirement_revision_append_only;

ALTER TABLE information_request_requirement
    ADD CONSTRAINT ck_information_request_requirement_root_path CHECK (occurrence_path <> '$');

ALTER TABLE information_request_requirement_revision
    ADD CONSTRAINT ck_information_request_requirement_revision_root_path CHECK (occurrence_path <> '$');

ALTER TABLE information_request_response
    ADD CONSTRAINT ck_information_request_response_root_path CHECK (occurrence_path <> '$');
