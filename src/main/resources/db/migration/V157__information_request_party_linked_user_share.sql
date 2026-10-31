ALTER TABLE information_request_party
    ADD COLUMN linked_user_share_id uuid;

ALTER TABLE information_request_party
    ADD CONSTRAINT information_request_party_linked_user_share_fkey
        FOREIGN KEY (linked_user_share_id) REFERENCES share (id);

ALTER TABLE information_request_party
    ADD CONSTRAINT ck_information_request_party_linked_user_share CHECK (
        linked_user_share_id IS NULL OR principal_kind = 'PARTICIPANT'
        );

CREATE INDEX ix_information_request_party_linked_user_share
    ON information_request_party (information_request_id, linked_user_share_id) WHERE linked_user_share_id IS NOT NULL;
