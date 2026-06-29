ALTER TABLE app_user_settings
    ADD COLUMN document_library_view VARCHAR(10) NOT NULL DEFAULT 'cards',
    ADD COLUMN blueprints_view       VARCHAR(10) NOT NULL DEFAULT 'cards',
    ADD COLUMN workflows_view        VARCHAR(10) NOT NULL DEFAULT 'cards',
    ADD COLUMN sequences_view        VARCHAR(10) NOT NULL DEFAULT 'cards',
    ADD COLUMN variables_view        VARCHAR(10) NOT NULL DEFAULT 'cards',
    ADD COLUMN communications_view   VARCHAR(10) NOT NULL DEFAULT 'cards';
