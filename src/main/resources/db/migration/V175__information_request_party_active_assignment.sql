CREATE UNIQUE INDEX ux_information_request_party_active_principal
    ON information_request_party (information_request_id, role_key, principal_kind, principal_id) WHERE active AND principal_id IS NOT NULL;

CREATE UNIQUE INDEX ux_information_request_party_active_subject
    ON information_request_party (information_request_id, role_key, subject_identity_ref_id) WHERE active AND subject_identity_ref_id IS NOT NULL;
