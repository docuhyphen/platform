package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.NotificationChannelType

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class OrganizationDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val isActive: Boolean?,
    val verificationComplete: Boolean?,
    val name: String?,
    val registrationNumber: String?,
    val contactDetails: ContactDetailsDetailedDto?,
    val settings: OrganizationSettingsDto
)


@Serializable
data class DocumentCommentDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val text: String?,
    val commentedByFirstName: String?,
    val commentedByLastName: String?,
    val commentedByEmail: String?,
    val isInternal: Boolean
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
    val comments: List<DocumentCommentDetailedDto>?,
    val fileSize: Long? = null,
    val lastUploadedByFirstName: String? = null,
    val lastUploadedByLastName: String? = null,
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
data class ExchangeDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val endDate: Timestamp?,
    val endNote: String?,
    @Serializable(with = TimestampSerializer::class)
    val lastActivity: Timestamp,
    val name: String?,
    val initialShareMessage: String? = null,
    val description: String? = null,
    val initiator: AppUserPublicDto? = null,
    val recipient: AppUserPublicDto? = null,
    val recipientGroupName: String? = null,
    val status: String?,
    var requestRecipientSignIn: Boolean = false,
    var noAuthAccessValidityDays: Int = 7,
    val documents: List<DocumentDetailedDto?>,
    // Document permissions are stored on the recipient's Share constraints_json.
    // They are populated at the resource layer from the primary recipient's share.
    var allowDocumentAddition: Boolean = false,
    var allowDocumentDeletion: Boolean = false,
    var allowDocumentDownload: Boolean = false,
    var allowDocumentUpdate: Boolean = false,
    var allowDocumentUpload: Boolean = false,
    var watermark: Boolean = false,
    var requireMfa: Boolean = false,
    var allowedDownloadFormats: List<String>? = null,
)

@Serializable
data class ContactDetailsDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val isVerified: Boolean?,
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
    val appRoles: List<String> = emptyList(),
    val organizationRoles: List<String> = emptyList(),
    val person: PersonDetailedDto?,
    val settings: AppUserSettingsDto,
    val avatarUrl: String? = null,
)

@Serializable
data class DocumentAuditDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val timestamp: Timestamp?,
    val action: String?,
    val performedBy: AppUserPublicDto?,
    val performedByEmail: String?
)

@Serializable
data class AppUserSettingsDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val notifyLogin: Boolean = true,
    val autoPreviewDocuments: Boolean = true,
    val notifyShareStart: Boolean = true,
    val notifyShareAccept: Boolean = true,
    val notifyShareDecline: Boolean = true,
    val notifyShareEnd: Boolean = true,
    val notifyDocComment: Boolean = true,
    val notifyDocDelete: Boolean = true,
    val notifyDocAdd: Boolean = true,
    val notifyDocUpload: Boolean = true,
    val notifyShareStartChannels: Set<NotificationChannelType>? = null,
    val notifyShareAcceptChannels: Set<NotificationChannelType>? = null,
    val notifyShareDeclineChannels: Set<NotificationChannelType>? = null,
    val notifyShareEndChannels: Set<NotificationChannelType>? = null,
    val notifyDocCommentChannels: Set<NotificationChannelType>? = null,
    val notifyDocDeleteChannels: Set<NotificationChannelType>? = null,
    val notifyDocAddChannels: Set<NotificationChannelType>? = null,
    val notifyDocUploadChannels: Set<NotificationChannelType>? = null,
    val theme: String = "light",
    val tourCompleted: Boolean = false,
    val documentLibraryView: String = "cards",
    val blueprintsView: String = "cards",
    val workflowsView: String = "cards",
    val sequencesView: String = "cards",
    val variablesView: String = "cards",
    val communicationsView: String = "cards",
)

@Serializable
data class OrganizationSettingsDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val allowShareWithoutPairing: Boolean = false,
    val allowProfileUpdate: Boolean = false,
    val allowEmailUpdate: Boolean = false
)
