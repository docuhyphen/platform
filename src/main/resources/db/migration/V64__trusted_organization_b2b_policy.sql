-- Retire the obsolete organization exchange link (pairing) model and its permissive B2B setting,
-- replacing them with the trust relationship model and a positive B2B policy setting.

-- The obsolete pairing table is no longer read by any application code. Cross-organization sharing
-- eligibility is now decided by the organization trust relationship aggregate.
DROP TABLE IF EXISTS organization_exchange_link CASCADE;

-- Replace the permissive "allow share without pairing" flag with a positive requirement flag.
-- A true value means the organization requires an active trusted-organization relationship before
-- any of its members may share an Exchange with a recipient belonging to another organization. The
-- default preserves the prior restrictive posture, in which a cross-organization share was blocked
-- unless the two organizations had an accepted relationship.
ALTER TABLE organization_settings
    DROP COLUMN IF EXISTS allow_share_without_pairing;

ALTER TABLE organization_settings
    ADD COLUMN require_trusted_organization_for_b2b boolean NOT NULL DEFAULT TRUE;
