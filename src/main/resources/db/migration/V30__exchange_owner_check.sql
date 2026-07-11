-- Backfill owner for exchanges that pre-date the owner columns (V28).
-- Pre-hardening exchanges were always user-initiated; initiator_id is the correct owner.
UPDATE exchange
SET owner_user_id = initiator_id
WHERE owner_organization_id IS NULL
  AND owner_user_id IS NULL
  AND initiator_id IS NOT NULL;

-- Fallback for any orphan exchanges with no initiator (anomalous dev data only; production starts fresh).
UPDATE exchange
SET owner_user_id = (SELECT id FROM app_user WHERE is_active = true ORDER BY created_date LIMIT 1)
WHERE owner_organization_id IS NULL
  AND owner_user_id IS NULL;

-- Enforce that every Exchange has exactly one owner context.
-- An Exchange is either owned by an organization or by a user directly; never both, never neither.
ALTER TABLE exchange
    ADD CONSTRAINT exchange_owner_exactly_one CHECK (
        (owner_organization_id IS NOT NULL AND owner_user_id IS NULL)
        OR
        (owner_user_id IS NOT NULL AND owner_organization_id IS NULL)
    );
