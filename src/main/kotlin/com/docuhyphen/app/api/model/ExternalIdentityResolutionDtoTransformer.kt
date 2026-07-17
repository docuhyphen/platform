package com.docuhyphen.app.api.model

import com.docuhyphen.app.api.model.dto.ExternalIdentityResolutionDto
import com.docuhyphen.app.api.model.entity.ExternalIdentityResolution
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
class ExternalIdentityResolutionDtoTransformer
{
    fun toDto(
        resolution: ExternalIdentityResolution,
        organizationName: String,
    ): ExternalIdentityResolutionDto = ExternalIdentityResolutionDto(
        id = resolution.id,
        organizationId = resolution.targetOrganizationId,
        organizationName = organizationName,
        displayName = resolution.displayNameSnapshot,
        email = resolution.normalizedEmail,
        verifiedAt = resolution.createdAt,
        expiresAt = resolution.expiresAt,
    )
}
