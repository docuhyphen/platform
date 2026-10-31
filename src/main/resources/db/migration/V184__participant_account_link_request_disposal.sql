ALTER TABLE participant_account_link
    ALTER COLUMN linked_via_information_request_id DROP NOT NULL,
DROP
CONSTRAINT participant_account_link_linked_via_information_request_id_fkey,
    ADD CONSTRAINT participant_account_link_linked_via_information_request_id_fkey
        FOREIGN KEY (linked_via_information_request_id) REFERENCES information_request (id) ON DELETE
SET NULL;
