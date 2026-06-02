-- Plan 05 — B2C external-customer sharing default.
-- Adds `allow_external_customer_sharing` to organization_settings. Sharing with an external
-- individual (a recipient with no org) is the headline B2C topology and should be allowed out of
-- the box, so the column defaults to TRUE. The B2B-unpaired case stays gated by the existing
-- allow_share_without_pairing column. Existing rows are backfilled to TRUE to preserve the
-- ergonomic default for orgs created before this change.

ALTER TABLE public.organization_settings
    ADD COLUMN allow_external_customer_sharing boolean NOT NULL DEFAULT true;

UPDATE public.organization_settings
    SET allow_external_customer_sharing = true
    WHERE allow_external_customer_sharing IS NULL;
