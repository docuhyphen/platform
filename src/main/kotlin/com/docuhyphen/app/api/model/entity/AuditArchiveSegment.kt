package com.docuhyphen.app.api.model.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Table(name = "audit_archive_segment")
class AuditArchiveSegment
{
    companion object
    {
        const val CURRENT_FORMAT_VERSION = 2
    }

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

    @Column(name = "format_version", nullable = false)
    var formatVersion: Int = CURRENT_FORMAT_VERSION

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

    @Column(name = "status", nullable = false, length = 32)
    var status: String = "CLOSED"

    @Column(name = "last_verification_at")
    var lastVerificationAt: Timestamp? = null

    @Column(name = "last_verification_note", length = 2048)
    var lastVerificationNote: String? = null

    @Column(name = "created_at", nullable = false)
    var createdAt: Timestamp = Timestamp.from(Instant.now())
}
