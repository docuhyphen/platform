package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.PrincipalGroupDto
import com.docuhyphen.app.api.model.dto.PrincipalGroupMemberDto
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.PrincipalGroup
import com.docuhyphen.app.api.model.entity.PrincipalGroupMember

object OrganizationGroupDtoMapper
{
    fun toDto(group: PrincipalGroup, members: List<PrincipalGroupMemberDto>): PrincipalGroupDto = PrincipalGroupDto(
        id = group.id,
        createdDate = group.createdDate,
        isActive = group.isActive,
        name = group.name,
        scope = group.scope.name,
        externallyPublished = group.externallyPublished,
        iconUrl = group.iconData,
        members = members,
    )

    fun toMemberDto(member: PrincipalGroupMember, user: AppUser?): PrincipalGroupMemberDto = PrincipalGroupMemberDto(
        user = user?.let { DetailedEntityToDtoTransformer.toPublicDto(it) },
        groupRole = member.groupRole,
    )
}
