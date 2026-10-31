ALTER TABLE information_request
DROP
CONSTRAINT information_request_superseded_by_fkey,
    ADD CONSTRAINT information_request_superseded_by_fkey
        FOREIGN KEY (superseded_by_request_id) REFERENCES information_request (id) ON DELETE
SET NULL;
