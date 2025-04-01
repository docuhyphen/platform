package com.dochyphen.app.api.model.dto

import com.dochyphen.app.api.serializer.TimestampSerializer
import com.dochyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class DocumentCommentDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val text: String?,
    val commentedByFirstName: String?,
    val commentedByLastName: String?,
    val commentedByEmail: String?
)

@Serializable
data class DocumentDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    @Serializable(with = TimestampSerializer::class)
    val uploadDate: Timestamp?,
    val title: String?,
    val type: String?,
    val restrictedType: String?,
    val restrictType: Boolean?,
    val hash: String?,
    val comments: List<DocumentCommentDetailedDto>?
)

@Serializable
data class DocumentVersionDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    val documentId: String?,
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Timestamp?,
    val version: String?,
    val storagePath: String?,
    val createdByEmail: String?,
    val createdBy: String?,
)

@Serializable
data class SharingSessionDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val endDate: Timestamp?,
    val endNote: String?,
    @Serializable(with = TimestampSerializer::class)
    val lastActivity: Timestamp,
    val sessionName: String?,
    val initialShareMessage: String? = null,
    val description: String? = null,
    val initiator: AppUserDetailedDto? = null,
    val recipient: AppUserDetailedDto? = null,
    val status: String?,
    var requestRecipientSignIn: Boolean = false,
    var allowDocumentAddition: Boolean = false,
    var allowDocumentDeletion: Boolean = false,
    var allowDocumentDownload: Boolean = true,
    var allowDocumentUpdate: Boolean = false,
    var allowDocumentUpload: Boolean = false,
    val documents: List<DocumentDetailedDto?>,


//    val participantIds: List<UUID>
)


@Serializable
data class ContactDetailsDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val email: String?,
    val phoneNumber: String?
)

@Serializable
data class PersonDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val firstName: String?,
    val lastName: String?,
    val identificationNumber: String?,
    val personIDType: String?,
    val contactDetails: ContactDetailsDetailedDto? 
)

@Serializable
data class AppUserDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val isActive: Boolean,
    val email: String,
    val person: PersonDetailedDto?
)
@Serializable
data class DocumentAuditDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val timestamp: Timestamp?,
    val action: String?,
    val performedBy: AppUserDetailedDto?,
    val performedByEmail: String?
)

