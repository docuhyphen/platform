package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.AppUserRole
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
    val name: String?,
    val registrationNumber: String?,
    val contactDetails: ContactDetailsDetailedDto?,
    val settings: OrganizationSettingsDto
)

@Serializable
data class OrganizationGroupMemberPermissionDto(
    val allowSessionAccept: Boolean = false,
    val allowSessionReject: Boolean = false,
    val allowSessionEdit: Boolean = false,
    val allowSessionDelete: Boolean = false,
    val allowSessionEnd: Boolean = false,
    val allowDocumentAddition: Boolean = false,
    val allowDocumentDeletion: Boolean = false,
    val allowDocumentDownload: Boolean = false,
    val allowDocumentUpdate: Boolean = false,
    val allowDocumentUpload: Boolean = false
)

@Serializable
data class OrganizationGroupMemberDetailedDto(
    val user: AppUserDetailedDto?,
    val permissions: OrganizationGroupMemberPermissionDto?
)

@Serializable
data class OrganizationGroupDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val isActive: Boolean?,
    val name: String?,
    val members: List<OrganizationGroupMemberDetailedDto?>,
    val externallyPublished: Boolean = false,
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
    val comments: List<DocumentCommentDetailedDto>?,
    val fileSize: Long? = null,
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
    var noAuthAccessValidityDays: Int = 7,
    val participants: List<SharingSessionParticipantDetailedDto> = emptyList(),
    val documents: List<DocumentDetailedDto?>,
    //    val participantIds: List<UUID>
)

@Serializable
data class SharingSessionParticipantDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val participantType: String,
    @Serializable(with = TimestampSerializer::class)
    val addedDate: Timestamp,
    @Serializable(with = UUIDSerializer::class)
    val appUserId: UUID? = null,
    val appUserEmail: String? = null,
    val appUserFirstName: String? = null,
    val appUserLastName: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val organizationGroupId: UUID? = null,
    val organizationGroupName: String? = null,
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
    val role: AppUserRole,
    val person: PersonDetailedDto?,
    val settings: AppUserSettingsDto
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
    val notifyDocUpload: Boolean = true
)

@Serializable
data class OrganizationSettingsDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    val allowShareWithoutPairing: Boolean = false,
    val allowProfileUpdate: Boolean = false,
    val allowEmailUpdate: Boolean = false
)