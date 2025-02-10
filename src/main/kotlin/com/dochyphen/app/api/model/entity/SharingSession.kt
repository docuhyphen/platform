package com.dochyphen.app.api.model.entity

import com.dochyphen.app.api.serializer.TimestampSerializer
import com.dochyphen.app.api.serializer.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class SharingSessionStatus
{
    INITIATED,
    ACCEPTED_STARTED,
    ENDED,
    REJECTED,
}

@Entity
@Table(name = "sharing_session")
@Serializable
class SharingSession
{

    @Id
    @Serializable(with = UUIDSerializer::class)
    var id: UUID = UUID.randomUUID()

    @Column(name = "created_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var createdDate: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "end_date", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var endDate: Timestamp? = Timestamp.from(Instant.now())

    @Column(name = "last_activity", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var lastActivity: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "session_name", nullable = false)
    var sessionName: String? = null

    @Column(name = "initial_share_message", nullable = false)
    var initialShareMessage: String? = null

    @Column(name = "description", nullable = false)
    var description: String? = null

    @ManyToOne(cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    @JoinColumn(name = "initiator_id", unique = false)
    var initiator: AppUser? = null

    @ManyToOne(cascade = [CascadeType.PERSIST], fetch = FetchType.EAGER)
    @JoinColumn(name = "recipient_id", unique = false)
    var recipient: AppUser? = null

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: SharingSessionStatus = SharingSessionStatus.INITIATED

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var documents: MutableList<Document> = mutableListOf()

    @Column(name = "require_recipient_sign_in", nullable = false)
    var requestRecipientSignIn: Boolean = false

    @Column(name = "allow_document_addition", nullable = false)
    var allowDocumentAddition: Boolean = false

    @Column(name = "allow_document_deletion", nullable = false)
    var allowDocumentDeletion: Boolean = false

    @Column(name = "allow_document_download", nullable = false)
    var allowDocumentDownload: Boolean = true

    @Column(name = "allow_document_update", nullable = false)
    var allowDocumentUpdate: Boolean = false

    @Column(name = "allow_document_upload", nullable = false)
    var allowDocumentUpload: Boolean = false

//    @Column(name = "allow_document_print", nullable = false)
//    var allowDocumentPrint: Boolean = false

    @OneToMany(cascade = [CascadeType.ALL], fetch = FetchType.EAGER)
    var participants: MutableList<SharingSessionParticipant> = mutableListOf()

    @Column(name="expire_date", nullable = true)
    @Serializable(with = TimestampSerializer::class)
    var expireDate: Timestamp? = null

    @Column(name="rejection_reason", nullable = true)
    var rejectionReason: String? = null

    constructor()
}