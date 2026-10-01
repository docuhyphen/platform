package com.docuhyphen.app.api.model.entity

import jakarta.persistence.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class InformationRequestRecordExportKind
{
    REQUEST_RECORD,
    SUBJECT_RECORD,
}

enum class RecordTransferDecision
{
    NOT_REQUESTED,
    PERMITTED,
}

@Entity
@Table(name = "information_request_record_export")
class InformationRequestRecordExport
{
    @Id
    var id: UUID = UUID.randomUUID()

    @Enumerated(EnumType.STRING)
    @Column(name = "export_kind", nullable = false, length = 32)
    var exportKind: InformationRequestRecordExportKind = InformationRequestRecordExportKind.REQUEST_RECORD

    @Column(name = "information_request_id")
    var informationRequestId: UUID? = null

    @Column(name = "subject_identity_ref_id")
    var subjectIdentityRefId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_kind", nullable = false, length = 32)
    var ownerKind: RecordOwnerKind = RecordOwnerKind.ORGANIZATION

    @Column(name = "owner_id", nullable = false)
    lateinit var ownerId: UUID

    @Column(name = "schema_version", nullable = false)
    var schemaVersion: Int = 1

    @Column(name = "content_json", nullable = false, columnDefinition = "text")
    var contentJson: String = "{}"

    @Column(name = "content_hash_algorithm", nullable = false, length = 16)
    var contentHashAlgorithm: String = SHA_256

    @Column(name = "content_hash", nullable = false, length = 64)
    lateinit var contentHash: String

    @Column(name = "content_length", nullable = false)
    var contentLength: Long = 0

    @Column(name = "storage_location", nullable = false, length = 64)
    lateinit var storageLocation: String

    @Column(name = "transfer_region", length = 64)
    var transferRegion: String? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_decision", nullable = false, length = 32)
    var transferDecision: RecordTransferDecision = RecordTransferDecision.NOT_REQUESTED

    @Enumerated(EnumType.STRING)
    @Column(name = "requested_by_principal_kind", nullable = false, length = 32)
    var requestedByPrincipalKind: PrincipalKind = PrincipalKind.USER

    @Column(name = "requested_by_principal_id", nullable = false)
    lateinit var requestedByPrincipalId: UUID

    @Column(name = "requested_at", nullable = false)
    var requestedAt: Timestamp = Timestamp.from(Instant.now())

    companion object
    {
        const val SHA_256 = "SHA_256"
    }
}
