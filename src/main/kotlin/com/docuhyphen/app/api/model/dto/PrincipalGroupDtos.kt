package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.*

@Serializable
data class PrincipalGroupMemberDto(
    val user: AppUserPublicDto?,
    val groupRole: PrincipalGroupRoleName,
)

@Serializable
data class PrincipalGroupDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    @Serializable(with = TimestampSerializer::class)
    val createdDate: Timestamp,
    val isActive: Boolean,
    val name: String,
    val description: String? = null,
    val scope: String,
    val externallyPublished: Boolean,
    @Serializable(with = UUIDSerializer::class)
    val ownerAppUserId: UUID? = null,
    val iconUrl: String? = null,
    val members: List<PrincipalGroupMemberDto>,
)

@Serializable
data class PublishedExchangeGroupDto(
    @Serializable(with = UUIDSerializer::class)
    val id: UUID,
    val name: String,
    val description: String? = null,
    @Serializable(with = UUIDSerializer::class)
    val organizationId: UUID,
    val iconUrl: String? = null,
)
