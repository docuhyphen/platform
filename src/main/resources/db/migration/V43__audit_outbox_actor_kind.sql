-- Adds an explicit actor-kind column to audit_outbox so call sites that know the real nature of
-- the actor (HUMAN / APP / PUBLIC_LINK / WORKFLOW / SYSTEM) can pass it through AuditEventDraft
-- instead of LedgerProcessor.resolveActorKind guessing HUMAN/SYSTEM from actor_id presence alone.
-- Nullable and additive only: older rows (and older call sites that have not been migrated to
-- pass actorKind) keep working, LedgerProcessor falls back to its existing guess when this is
-- null. No FK, no change to the append-only trigger installed by V41.
ALTER TABLE audit_outbox
    ADD COLUMN actor_kind VARCHAR(32);
