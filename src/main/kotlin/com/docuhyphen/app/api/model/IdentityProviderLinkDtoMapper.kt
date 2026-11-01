package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.entity.IdentityProviderLink
import com.docuhyphen.app.api.resource.model.IdentityProviderLinkDto

object IdentityProviderLinkDtoMapper
{
    fun toDto(link: IdentityProviderLink): IdentityProviderLinkDto = IdentityProviderLinkDto(
        provider = link.provider.name,
        externalEmail = link.externalEmail,
        createdDate = link.createdDate.toString(),
    )
}
