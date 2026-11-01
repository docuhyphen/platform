package com.docuhyphen.app.api.model.organization

import com.docuhyphen.app.api.model.dto.PrincipalGroupDto
import com.docuhyphen.app.api.model.dto.PrincipalGroupMemberDto
import com.docuhyphen.app.api.model.entity.PrincipalGroup

object PersonalGroupDtoMapper
{
    fun toDto(group: PrincipalGroup, members: List<PrincipalGroupMemberDto>): PrincipalGroupDto
    {
        return PrincipalGroupDto(
            id = group.id,
            createdDate = group.createdDate,
            isActive = group.isActive,
            name = group.name,
            description = group.description,
            scope = group.scope.name,
            externallyPublished = group.externallyPublished,
            ownerAppUserId = group.ownerAppUserId,
            iconUrl = group.iconData,
            members = members,
        )
    }
}
