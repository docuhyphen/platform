package com.docuhyphen.app.api.model.dto

import com.docuhyphen.app.api.serializer.TimestampSerializer
import com.docuhyphen.app.api.serializer.UUIDSerializer
import kotlinx.serialization.Serializable
import java.sql.Timestamp
import java.util.UUID

/** A member of a [com.docuhyphen.app.api.model.entity.PrincipalGroup], role-based (new model). */
@Serializable
data class PrincipalGroupMemberDto(
    val user: AppUserDetailedDto?,
    val groupRole: String,
)

/**
 * Group response in the new unified model. Replaces OrganizationGroupDetailedDto, members
 * carry a [groupRole] (OWNER/MANAGER/MEMBER/OBSERVER) instead of a per-member permission bag.
 */
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
