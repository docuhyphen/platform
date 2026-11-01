package com.docuhyphen.app.api.resource.model

import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import kotlinx.serialization.Serializable

@Serializable
data class CreatePersonalGroupRequest(
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class UpdatePersonalGroupRequest(
    val name: String? = null,
    val description: String? = null,
)

@Serializable
data class AddPersonalGroupMembersRequest(
    val members: List<PersonalGroupMemberEntry> = emptyList(),
)

@Serializable
data class PersonalGroupMemberEntry(
    val principalId: String? = null,
    val principalKind: String = "USER",
    val groupRole: PrincipalGroupRoleName = PrincipalGroupRoleName.MEMBER,
)
