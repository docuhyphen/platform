package com.securedocsshare.app.api.model

import com.securedocsshare.app.api.hacks.TimestampSerializer
import com.securedocsshare.app.api.hacks.UUIDSerializer
import jakarta.persistence.*
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.time.Instant
import java.util.*

enum class SharingSessionStatus
{
    INITIATED,
    ACCEPTED_STARTED,
    COMPLETED,
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

    @Column(name = "last_activity", nullable = false)
    @Serializable(with = TimestampSerializer::class)
    var lastActivity: Timestamp = Timestamp.from(Instant.now())

    @Column(name = "session_name", nullable = false)
    var sessionName: String? = null

    @Column(name = "initial_share_message", nullable = false)
    var initialShareMessage: String? = null

    @Column(name = "description", nullable = false)
    var description: String? = null

    @ManyToOne(cascade = [CascadeType.PERSIST], fetch = FetchType.LAZY)
    @JoinColumn(name = "initiator_id", unique = false)
    var initiator: AppUser? = null

    @ManyToOne(cascade = [CascadeType.PERSIST], fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", unique = false)
    var receiver: AppUser? = null

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    var status: SharingSessionStatus = SharingSessionStatus.INITIATED

    @OneToMany(cascade = [CascadeType.ALL])
    var documents: MutableList<Document> = mutableListOf()

    @Column(name = "require_receiver_sign_in", nullable = false)
    var requestReceiverSignIn: Boolean = false

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

    @OneToMany(cascade = [CascadeType.ALL])
    var participants: MutableList<SharingSessionParticipant> = mutableListOf()

    constructor()
}