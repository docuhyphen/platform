-- Theme preference per user: 'light' | 'dark' | 'system'
alter table if exists app_user_settings
    add column if not exists theme varchar(16) not null default 'light';

alter table if exists app_user_settings
    add constraint app_user_settings_theme_chk
        check (theme in ('light', 'dark', 'system'));

