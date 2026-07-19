package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class ExchangeBasicDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val lastActivity: Timestamp,
    val name: String?,
    val initialShareMessage: String? = null,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val initiator: UUID?,
    val status: String?,
    // Recipients/participants are exposed via the unified access view
    // (GET /exchanges/{id}/access), not embedded recipient columns.
)

@Serializable
data class NoAuthExchangeBasicDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp,
    @Serializable(with = TimestampSerializer::class)
    val lastActivity: Timestamp,
    val name: String?,
    val initialShareMessage: String? = null,
    val status: String?,
    var recipientEmail: String? = null,
    var initiatorFirstName: String? = null,
    var initiatorLastName: String? = null,
    var noAuthAccessValidityDays: Int = 7,
    val documents: List<DocumentBasicDto?>,
    var allowDocumentDownload: Boolean = false,
)

@Serializable
data class ContactDetailsBasicDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val isVerified: Boolean?,
    val email: String?,
    val phoneNumber: String?
)

@Serializable
data class OrganizationBasicDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID?,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp?,
    val name: String?,
    val registrationNumber: String?,
//    val isActive: Boolean?,
//    val verificationComplete: Boolean?,
)

@Serializable
data class PersonBasicDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID? = null,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val identificationNumber: String? = null,
    val personIDType: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val contactDetailsId: UUID? = null
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

/**
 * Safe public view of another user. Never includes private settings, notification preferences,
 * identification numbers, or contact details. Used anywhere one user can see another user's data.
 */
@Serializable
data class PersonPublicDto(
    val firstName: String? = null,
    val lastName: String? = null,
)

@Serializable
data class AppUserPublicDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val email: String,
    val person: PersonPublicDto? = null,
    val avatarUrl: String? = null,
    val isActive: Boolean = true,
    val appRoles: List<String> = emptyList(),
    val organizationRoles: List<String> = emptyList(),
)
