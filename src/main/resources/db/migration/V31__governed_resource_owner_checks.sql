-- Phase 3: enforce ownership invariants on every governed resource.
-- Each constraint ensures the ownership columns are consistent with the declared scope.
-- NOT VALID skips the check against pre-existing rows so the migration succeeds on an
-- existing dev database.  New rows are still fully enforced.  Run
-- "ALTER TABLE t VALIDATE CONSTRAINT c" on each table when a clean-slate is desired.

-- blueprint_definition: APP = platform-owned (both null), ORG = org owned, PERSONAL = user owned
ALTER TABLE blueprint_definition
    ADD CONSTRAINT blueprint_owner_scope_consistent CHECK (
        (scope = 'APP'      AND organization_id IS NULL       AND created_by_app_user_id IS NULL) OR
        (scope = 'ORG'      AND organization_id IS NOT NULL) OR
        (scope = 'PERSONAL' AND created_by_app_user_id IS NOT NULL)
    ) NOT VALID;

-- document_library: same pattern (also uses BlueprintScope values APP/ORG/PERSONAL)
ALTER TABLE document_library
    ADD CONSTRAINT document_library_owner_scope_consistent CHECK (
        (scope = 'APP'      AND organization_id IS NULL       AND created_by_app_user_id IS NULL) OR
        (scope = 'ORG'      AND organization_id IS NOT NULL) OR
        (scope = 'PERSONAL' AND created_by_app_user_id IS NOT NULL)
    ) NOT VALID;

-- workflow_definition: APP = platform-owned, ORG = org owned, PERSONAL = user owned
ALTER TABLE workflow_definition
    ADD CONSTRAINT workflow_owner_scope_consistent CHECK (
        (scope = 'APP'      AND organization_id IS NULL       AND created_by_app_user_id IS NULL) OR
        (scope = 'ORG'      AND organization_id IS NOT NULL) OR
        (scope = 'PERSONAL' AND created_by_app_user_id IS NOT NULL)
    ) NOT VALID;

-- variable_definition: ORG or PERSONAL only; created_by_app_user_id is always non-null (existing NOT NULL column)
ALTER TABLE variable_definition
    ADD CONSTRAINT variable_owner_scope_consistent CHECK (
        (scope = 'ORG'      AND organization_id IS NOT NULL) OR
        (scope = 'PERSONAL' AND organization_id IS NULL)
    ) NOT VALID;

-- communication: PLATFORM = platform-owned, ORG = org owned, PERSONAL = user owned
ALTER TABLE communication
    ADD CONSTRAINT communication_owner_scope_consistent CHECK (
        (scope = 'PLATFORM' AND organization_id IS NULL       AND created_by_app_user_id IS NULL) OR
        (scope = 'ORG'      AND organization_id IS NOT NULL) OR
        (scope = 'PERSONAL' AND created_by_app_user_id IS NOT NULL)
    ) NOT VALID;

-- principal_group:
--   ORG          = owned by one organization (owner_organization_id set, owner_app_user_id null)
--   PERSONAL     = owned by one user (owner_app_user_id set, owner_organization_id null)
--   SHARED_PROJECT = jointly owned via principal_group_co_owner_org; neither direct owner column is set
ALTER TABLE principal_group
    ADD CONSTRAINT principal_group_owner_scope_consistent CHECK (
        (scope = 'ORG'            AND owner_organization_id IS NOT NULL AND owner_app_user_id IS NULL) OR
        (scope = 'PERSONAL'       AND owner_app_user_id IS NOT NULL     AND owner_organization_id IS NULL) OR
        (scope = 'SHARED_PROJECT' AND owner_organization_id IS NULL     AND owner_app_user_id IS NULL)
    ) NOT VALID;
