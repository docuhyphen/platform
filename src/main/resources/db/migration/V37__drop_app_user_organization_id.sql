-- Remove the dead legacy app_user.organization_id column and its FK. Organization membership
-- is resolved exclusively through organization_membership; AppUser never declared this field.
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS fk_app_user_org;
ALTER TABLE app_user DROP COLUMN IF EXISTS organization_id;
