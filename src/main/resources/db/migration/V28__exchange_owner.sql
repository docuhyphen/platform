ALTER TABLE exchange
    ADD COLUMN owner_organization_id UUID REFERENCES organization(id),
    ADD COLUMN owner_user_id         UUID REFERENCES app_user(id);
