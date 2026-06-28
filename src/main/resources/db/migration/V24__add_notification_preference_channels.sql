ALTER TABLE app_user_settings
    ADD COLUMN notify_share_start_channels VARCHAR(128) NOT NULL DEFAULT 'EMAIL,IN_APP',
    ADD COLUMN notify_share_accept_channels VARCHAR(128) NOT NULL DEFAULT 'EMAIL,IN_APP',
    ADD COLUMN notify_share_decline_channels VARCHAR(128) NOT NULL DEFAULT 'EMAIL,IN_APP',
    ADD COLUMN notify_share_end_channels VARCHAR(128) NOT NULL DEFAULT '',
    ADD COLUMN notify_doc_comment_channels VARCHAR(128) NOT NULL DEFAULT 'EMAIL,IN_APP',
    ADD COLUMN notify_doc_delete_channels VARCHAR(128) NOT NULL DEFAULT 'EMAIL,IN_APP',
    ADD COLUMN notify_doc_add_channels VARCHAR(128) NOT NULL DEFAULT 'EMAIL,IN_APP',
    ADD COLUMN notify_doc_upload_channels VARCHAR(128) NOT NULL DEFAULT 'EMAIL,IN_APP';

UPDATE app_user_settings
SET notify_share_start_channels = CASE WHEN notify_share_start THEN 'EMAIL,IN_APP' ELSE '' END,
    notify_share_accept_channels = CASE WHEN notify_share_accept THEN 'EMAIL,IN_APP' ELSE '' END,
    notify_share_decline_channels = CASE WHEN notify_share_decline THEN 'EMAIL,IN_APP' ELSE '' END,
    notify_share_end_channels = CASE WHEN notify_share_end THEN 'EMAIL,IN_APP' ELSE '' END,
    notify_doc_comment_channels = CASE WHEN notify_doc_comment THEN 'EMAIL,IN_APP' ELSE '' END,
    notify_doc_delete_channels = CASE WHEN notify_doc_delete THEN 'EMAIL,IN_APP' ELSE '' END,
    notify_doc_add_channels = CASE WHEN notify_doc_add THEN 'EMAIL,IN_APP' ELSE '' END,
    notify_doc_upload_channels = CASE WHEN notify_doc_upload THEN 'EMAIL,IN_APP' ELSE '' END;
