-- No-auth access window controls.
alter table sharing_session
    add column if not exists no_auth_access_verified_at timestamp(6);

alter table sharing_session
    add column if not exists no_auth_access_validity_days integer not null default 7;

