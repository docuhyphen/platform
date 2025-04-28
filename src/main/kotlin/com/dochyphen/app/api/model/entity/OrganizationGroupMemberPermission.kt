package com.dochyphen.app.api.model.entity

import com.dochyphen.app.api.serializer.TimestampSerializer
import com.dochyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

@Entity
@Serializable
@Table(name = "organization_group_member_permissions")
class OrganizationGroupMemberPermission
{
    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "allow_session_accept", nullable = false)
    var allowSessionAccept: Boolean = false

    @Column(name = "allow_session_reject", nullable = false)
    var allowSessionReject: Boolean = false

    @Column(name = "allow_session_edit", nullable = false)
    var allowSessionEdit: Boolean = false

    @Column(name = "allow_session_delete", nullable = false)
    var allowSessionDelete: Boolean = false

    @Column(name = "allow_session_end", nullable = false)
    var allowSessionEnd: Boolean = false

    @Column(name = "allow_document_addition", nullable = false)
    var allowDocumentAddition: Boolean = false

    @Column(name = "allow_document_deletion", nullable = false)
    var allowDocumentDeletion: Boolean = false

    @Column(name = "allow_document_download", nullable = false)
    var allowDocumentDownload: Boolean = false

    @Column(name = "allow_document_update", nullable = false)
    var allowDocumentUpdate: Boolean = false

    @Column(name = "allow_document_upload", nullable = false)
    var allowDocumentUpload: Boolean = false

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_group_member_id")
    var organizationGroupMember: OrganizationGroupMember? = null

    constructor()
}