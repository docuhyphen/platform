-- Trusted-org: allow groups to be flagged for cross-org visibility
alter table organization_group
    add column if not exists externally_published boolean not null default false;

-- No-auth recipient OTP columns
alter table sharing_session
    add column if not exists recipient_otp_hash varchar(255);

alter table sharing_session
    add column if not exists recipient_otp_expiry timestamp(6);

-- Personal contacts: one row per (owner, contactEmail) pair
create table if not exists user_contact (
    id                  uuid         not null,
    owner_app_user_id   uuid         not null,
    contact_app_user_id uuid,
    contact_email       varchar(255) not null,
    contact_first_name  varchar(255),
    contact_last_name   varchar(255),
    first_shared_at     timestamp(6) not null,
    last_shared_at      timestamp(6) not null,
    share_count         integer      not null default 0,
    last_session_id     uuid,
    primary key (id)
);

alter table if exists user_contact
    add constraint uk_user_contact_owner_email unique (owner_app_user_id, contact_email);

create index if not exists ix_user_contact_owner_last_shared
    on user_contact (owner_app_user_id, last_shared_at);

alter table if exists user_contact
    add constraint fk_user_contact_owner foreign key (owner_app_user_id) references app_user;

alter table if exists user_contact
    add constraint fk_user_contact_contact foreign key (contact_app_user_id) references app_user;
