package com.dochyphen.app.api.model.dto

import com.dochyphen.app.api.serializer.TimestampSerializer
import com.dochyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class SharingSessionBasicDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val lastActivity: Timestamp,
    val sessionName: String?,
    val initialShareMessage: String? = null,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val initiator: UUID?,
    @Serializable(with = UUIDSerializer::class)
    val recipientId: UUID?,
    val status: String?,
    var recipientEmail: String? = null,
    var recipientFirstName: String? = null,
    var recipientLastName: String? = null,
    var recipientOrganizationName: String? = null,
//    val participantIds: List<UUID>
)

@Serializable
data class NoAuthSharingSessionBasicDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val lastActivity: Timestamp,
    val sessionName: String?,
    val initialShareMessage: String? = null,
    val status: String?,
    var recipientEmail: String? = null,
    var initiatorFirstName: String? = null,
    var initiatorLastName: String? = null,
    val documents: List<DocumentBasicDto?>,
)

@Serializable
data class ContactDetailsBasicDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val email: String?,
    val phoneNumber: String?
)

@Serializable
data class PersonBasicDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val firstName: String?,
    val lastName: String?,
    val identificationNumber: String?,
    val personIDType: String?,
    @Serializable(with = UUIDSerializer::class)
    val contactDetailsId: UUID?
)

@Serializable
data class AppUserBasicDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val isActive: Boolean,
    val email: String,
    @Serializable(with = UUIDSerializer::class)
    val personId: UUID?
)

@Serializable
data class DocumentBasicDto(
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
)