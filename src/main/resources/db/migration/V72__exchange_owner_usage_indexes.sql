-- Supports the owner-scoped Exchange counts used by commercial allowance checks.
--
-- Both counts run on the Exchange creation path, so they must stay index-backed as the table
-- grows. Deleted Exchanges never count towards an allowance, which is why the indexes are
-- partial: they stay small and skip rows the counts always exclude.

CREATE INDEX IF NOT EXISTS ix_exchange_owner_user_created
    ON exchange (owner_user_id, created_date)
    WHERE owner_user_id IS NOT NULL
      AND owner_organization_id IS NULL
      AND is_deleted = false;

CREATE INDEX IF NOT EXISTS ix_exchange_owner_user_status
    ON exchange (owner_user_id, status)
    WHERE owner_user_id IS NOT NULL
      AND owner_organization_id IS NULL
      AND is_deleted = false;

CREATE INDEX IF NOT EXISTS ix_exchange_owner_organization_created
    ON exchange (owner_organization_id, created_date)
    WHERE owner_organization_id IS NOT NULL
      AND is_deleted = false;

CREATE INDEX IF NOT EXISTS ix_exchange_owner_organization_status
    ON exchange (owner_organization_id, status)
    WHERE owner_organization_id IS NOT NULL
      AND is_deleted = false;

