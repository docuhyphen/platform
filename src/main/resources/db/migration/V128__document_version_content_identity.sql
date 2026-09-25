ALTER TABLE document_version
    ADD COLUMN content_length         BIGINT,
    ADD COLUMN content_hash_algorithm VARCHAR(16),
    ADD COLUMN content_hash           VARCHAR(128),
    ADD COLUMN content_verification   VARCHAR(16);

DELETE
FROM document_version
WHERE content_hash IS NULL;

ALTER TABLE document_version
    ALTER COLUMN content_length SET NOT NULL,
    ALTER COLUMN content_hash_algorithm SET NOT NULL,
    ALTER COLUMN content_hash SET NOT NULL,
    ALTER COLUMN content_verification SET NOT NULL,
    ADD CONSTRAINT ck_document_version_content_length CHECK (content_length >= 0),
    ADD CONSTRAINT ck_document_version_content_hash_algorithm CHECK (content_hash_algorithm = 'SHA_256'),
    ADD CONSTRAINT ck_document_version_content_hash_value CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    ADD CONSTRAINT ck_document_version_content_verification CHECK (
        content_verification IN ('VERIFIED', 'UNVERIFIED')
        );

CREATE FUNCTION document_version_content_identity_guard()
    RETURNS TRIGGER AS
$$
BEGIN
    IF NEW.storage_provider IS DISTINCT FROM OLD.storage_provider
        OR NEW.storage_locator_kind IS DISTINCT FROM OLD.storage_locator_kind
        OR NEW.storage_locator IS DISTINCT FROM OLD.storage_locator
        OR NEW.content_length IS DISTINCT FROM OLD.content_length
        OR NEW.content_hash_algorithm IS DISTINCT FROM OLD.content_hash_algorithm
        OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
        OR NEW.content_verification IS DISTINCT FROM OLD.content_verification THEN
        RAISE EXCEPTION 'document version content identity is immutable';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER document_version_content_identity_write
    BEFORE UPDATE
    ON document_version
    FOR EACH ROW
EXECUTE FUNCTION document_version_content_identity_guard();
