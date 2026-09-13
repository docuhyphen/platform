-- A blueprint may name one exact published Information Request Template Version for the requests
-- its future instantiations create.
--
-- The reference is to a Version rather than to a Template because a request has to keep resolving
-- exactly the configuration it was created against, and a Template keeps changing. Replacing the
-- reference therefore changes only what is created next.
--
-- Nothing here says the named Version has to be published. A Version that is retired after being
-- named stays named, so an author can still read what the blueprint used to create and can still
-- edit everything else about it. Whether a new request may be created from a retired Version is a
-- question asked at instantiation, where the answer can change over the life of the Version, and
-- storage is never told when that happens.

ALTER TABLE blueprint_definition
    ADD COLUMN information_request_template_version_id uuid;

-- No ON DELETE action: forgetting the reference silently would change what the blueprint creates
-- without anybody deciding to change it, so a Version that something still names cannot be removed.
ALTER TABLE blueprint_definition
    ADD CONSTRAINT blueprint_definition_request_template_version_fkey
        FOREIGN KEY (information_request_template_version_id)
            REFERENCES information_request_template_version (id);

CREATE INDEX ix_blueprint_def_request_template_version
    ON blueprint_definition (information_request_template_version_id)
    WHERE information_request_template_version_id IS NOT NULL;

