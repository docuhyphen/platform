ALTER TABLE document
    ADD COLUMN restrict_type BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN required      BOOLEAN NOT NULL DEFAULT false;
