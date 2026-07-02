-- Phase 5: Application capability grants.
-- owner_organization_id: the single organization this application is authorized to act for.
-- granted_capabilities:  JSON array of Capability enum names explicitly granted to this application.
--                        Resolved server-side; never trusted from the token claim.

ALTER TABLE application
    ADD COLUMN owner_organization_id uuid REFERENCES organization(id),
    ADD COLUMN granted_capabilities  text NOT NULL DEFAULT '[]';
