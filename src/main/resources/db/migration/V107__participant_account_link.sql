CREATE TABLE participant_account_link
(
    id                                 UUID PRIMARY KEY,
    participant_id                     UUID NOT NULL UNIQUE REFERENCES external_participant (id),
    app_user_id                        UUID NOT NULL REFERENCES app_user (id),
    linked_via_information_request_id  UUID NOT NULL REFERENCES information_request (id),
    linked_via_share_link_id           UUID NOT NULL REFERENCES share_link (id),
    linked_at                          TIMESTAMP NOT NULL,
    created_at                         TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_participant_account_link_app_user
    ON participant_account_link (app_user_id);
