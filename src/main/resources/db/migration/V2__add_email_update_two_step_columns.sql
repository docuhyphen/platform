-- Two-step email update: verify OLD email before sending code to NEW email.
alter table app_user
    add column if not exists pending_email_old_verification_code varchar(255);

alter table app_user
    add column if not exists pending_email_old_verified boolean;
