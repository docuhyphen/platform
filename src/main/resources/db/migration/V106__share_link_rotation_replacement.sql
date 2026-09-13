ALTER TABLE share_link
    ADD COLUMN rotated_at TIMESTAMP,
    ADD COLUMN rotation_count INT NOT NULL DEFAULT 0,
    ADD COLUMN replaces_share_link_id UUID REFERENCES share_link (id);

ALTER TABLE share_link
    ADD CONSTRAINT ck_share_link_rotation_count CHECK (rotation_count >= 0);
