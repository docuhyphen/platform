-- V7: Convert refresh-token expiry to minutes (sensitive-data security tightening),
--     add per-IdP idle-timeout, and allow INTERNAL provider rows where the
--     external client_id / client_secret_ref are not applicable.

-- 1) Rename the column and convert existing values: days * 24 * 60 = minutes.
alter table organization_identity_provider_config
    add column refresh_token_expiry_minutes bigint;

update organization_identity_provider_config
    set refresh_token_expiry_minutes = refresh_token_expiry_days * 24 * 60
    where refresh_token_expiry_days is not null;

alter table organization_identity_provider_config
    drop column refresh_token_expiry_days;

-- 2) Idle timeout (sliding inactivity window) per IdP config.
alter table organization_identity_provider_config
    add column idle_timeout_minutes bigint;

-- 3) INTERNAL provider rows do not require external OAuth client credentials.
alter table organization_identity_provider_config
    alter column client_id drop not null;
alter table organization_identity_provider_config
    alter column client_secret_ref drop not null;

