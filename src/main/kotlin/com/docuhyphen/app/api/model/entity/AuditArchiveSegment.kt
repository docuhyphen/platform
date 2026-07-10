package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

/**
 * Operational lifecycle record for one closed, signed, archived range of `audit_ledger_event`
 * rows for a single [streamId] (Phase 4 of `AUDIT-ARCHITECTURE-IMPLEMENTATION.md`).
 *
 * Not itself the WORM evidence - the evidence is the segment content object plus its signed
 * manifest object, uploaded via `AuditArchiveStorage` to [segmentObjectKey]/[manifestObjectKey].
 * This row is a normal mutable table (see `V44__audit_archive_segment.sql`), not append-only:
 * [status]/[lastVerificationAt]/[lastVerificationNote] are updated as
 * `com.docuhyphen.app.api.service.audit.archive.AuditArchiveVerifier` (re)checks the segment.
 */
@Entity
@Table(name = "audit_archive_segment")
class AuditArchiveSegment
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Column(name = "stream_id", nullable = false, length = 128)
    lateinit var streamId: String

    @Column(name = "first_sequence", nullable = false)
    var firstSequence: Long = 0

    @Column(name = "last_sequence", nullable = false)
    var lastSequence: Long = 0

    @Column(name = "event_count", nullable = false)
    var eventCount: Int = 0

    @Column(name = "merkle_root", nullable = false, length = 128)
    lateinit var merkleRoot: String

    @Column(name = "segment_digest", nullable = false, length = 128)
    lateinit var segmentDigest: String

    @Column(name = "prev_segment_digest", length = 128)
    var prevSegmentDigest: String? = null

    @Column(name = "schema_versions", nullable = false, length = 256)
    lateinit var schemaVersions: String

    @Column(name = "signing_key_id", nullable = false, length = 64)
    lateinit var signingKeyId: String

    @Column(name = "manifest_signature", nullable = false, length = 1024)
    lateinit var manifestSignature: String

    @Column(name = "segment_object_key", nullable = false, length = 512)
    lateinit var segmentObjectKey: String

    @Column(name = "manifest_object_key", nullable = false, length = 512)
    lateinit var manifestObjectKey: String

    /** One of: CLOSED, VERIFIED, VERIFICATION_FAILED. */
    @Column(name = "status", nullable = false, length = 32)
    var status: String = "CLOSED"

    @Column(name = "last_verification_at")
    var lastVerificationAt: Timestamp? = null

    @Column(name = "last_verification_note", length = 2048)
    var lastVerificationNote: String? = null

    /** True only for segments produced by the one-time legacy-row importer; see migration header. */
    @Column(name = "legacy_import", nullable = false)
    var legacyImport: Boolean = false

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())
}
