-- Stores immutable WORM archive segments and their signatures.
--
-- audit_archive_segment is operational lifecycle metadata for AuditArchiver/AuditArchiveVerifier
-- (com.docuhyphen.app.api.service.audit.archive), not itself the WORM evidence: the evidence is
-- the segment content + signed manifest object uploaded to the archive bucket (local directory in
-- dev/degraded mode). This table only tracks which stream_id + stream_sequence ranges have been
-- closed into a segment, their integrity digests, and verification status, so it is a normal
-- mutable table (status/verification columns are updated as a segment moves through its
-- lifecycle) - unlike audit_outbox/audit_ledger_event, it carries no append-only trigger.
--
-- Same denormalized-ID rule as every other audit table: stream_id references audit_ledger_event
-- logically, never via a foreign key.
CREATE TABLE audit_archive_segment
(
    id                    UUID PRIMARY KEY,
    stream_id             VARCHAR(128) NOT NULL,
    first_sequence        BIGINT       NOT NULL,
    last_sequence         BIGINT       NOT NULL,
    event_count           INTEGER      NOT NULL,

    -- Tamper-evidence: merkle_root over the segment's ledger event hashes (leaves in sequence
    -- order); segment_digest additionally folds in stream_id/first_sequence/last_sequence/
    -- prev_segment_digest so segments themselves form a hash chain per stream (detects deleted,
    -- reordered, or truncated segment ranges - the "boundary checkpoint" the architecture calls
    -- for), independent of the per-event chain already enforced by audit_ledger_event.
    merkle_root           VARCHAR(128) NOT NULL,
    segment_digest        VARCHAR(128) NOT NULL,
    prev_segment_digest    VARCHAR(128),

    schema_versions       VARCHAR(256) NOT NULL,

    -- Signing indirection: signing_key_id names a key managed by whichever
    -- AuditArchiveSigningKeyProvider is active (Secrets-Manager-held asymmetric key in
    -- production, and a local development keypair otherwise) - never KMS or HSM.
    -- cost constraint. manifest_signature is base64(sign(manifestJson)); the manifest is also
    -- archived alongside the segment so it can be independently re-verified offline.
    signing_key_id        VARCHAR(64)  NOT NULL,
    manifest_signature    VARCHAR(1024) NOT NULL,

    segment_object_key    VARCHAR(512) NOT NULL,
    manifest_object_key   VARCHAR(512) NOT NULL,

    -- OPEN is never persisted (a row is only inserted once a segment is fully built and
    -- uploaded); CLOSED means archived and not yet (re)verified; VERIFIED/VERIFICATION_FAILED
    -- are set by AuditArchiveVerifier.
    status                VARCHAR(32)  NOT NULL DEFAULT 'CLOSED',
    last_verification_at  TIMESTAMP,
    last_verification_note VARCHAR(2048),

    -- True only for segments produced by the one-time legacy-row importer: these carry
    -- pre-recorder history and must never be presented as having the same end-to-end provenance
    -- as a segment built entirely from recorder-captured ledger events.
    legacy_import         BOOLEAN      NOT NULL DEFAULT false,

    created_at            TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT audit_archive_segment_stream_range_unique UNIQUE (stream_id, first_sequence, last_sequence)
);

CREATE INDEX idx_audit_archive_segment_stream ON audit_archive_segment (stream_id, last_sequence);
CREATE INDEX idx_audit_archive_segment_status ON audit_archive_segment (status);

-- audit_ledger_event.signing_key_id / checkpoint_ref remain NULL for ledger rows because
-- rather than back-filled via UPDATE, because audit_ledger_event is append-only (its own trigger
-- unconditionally denies UPDATE - see V42's header note, "any future approved retention/
-- legal-hold operation must add an explicit, reviewed exception rather than remove this
-- trigger"). Adding such an exception for routine archive bookkeeping was judged out of proportion
-- to the benefit. Instead, audit_archive_segment (stream_id + [first_sequence, last_sequence])
-- is the authoritative lookup for "which segment/checkpoint covers this ledger event" - callers
-- resolve it via a range query, not a per-event FK/column. This is a documented scoping decision,
-- they describe archive-level metadata rather than individual ledger rows.
