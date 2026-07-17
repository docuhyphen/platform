package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.PublishedExchangeGroupDto
import com.docuhyphen.app.api.model.entity.PrincipalGroup

object PublishedExchangeGroupDtoTransformer
{
    fun toDto(group: PrincipalGroup): PublishedExchangeGroupDto
    {
        val organizationId = requireNotNull(group.ownerOrganizationId) {
            "Published organization group must have an owning organization"
        }

        return PublishedExchangeGroupDto(
            id = group.id,
            name = group.name,
            description = group.description,
            organizationId = organizationId,
            iconUrl = group.iconData,
        )
    }
}
