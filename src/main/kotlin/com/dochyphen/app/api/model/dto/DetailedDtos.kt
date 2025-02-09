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
//    val commentedBy: AppUserDetailedDto?
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
    val hash: String?,
    val comments: List<DocumentCommentDetailedDto>?
)

@Serializable
data class SharingSessionDetailedDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val lastActivity: Timestamp,
    val sessionName: String?,
    val initialShareMessage: String? = null,
    val description: String? = null,
    val initiator: AppUserDetailedDto? = null,
    val recipient: AppUserDetailedDto? = null,
    val status: String?,
    val documents: List<DocumentDetailedDto?>

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