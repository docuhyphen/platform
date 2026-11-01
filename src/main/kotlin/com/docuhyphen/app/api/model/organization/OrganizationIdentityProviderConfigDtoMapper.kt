package com.docuhyphen.app.api.model.organization

import com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig
import com.docuhyphen.app.api.resource.model.OrganizationIdpConfigResponse

object OrganizationIdentityProviderConfigDtoMapper
{
    fun toResponse(config: OrganizationIdentityProviderConfig): OrganizationIdpConfigResponse
    {
        return OrganizationIdpConfigResponse(
            id = config.id.toString(),
            organizationId = config.organization?.id?.toString().orEmpty(),
            provider = config.provider,
            clientId = config.clientId,
            clientSecretRef = config.clientSecretRef,
            tenantId = config.tenantId,
            scopes = splitCsv(config.scopes),
            isActive = config.isActive,
            accessTokenExpiryMinutes = config.accessTokenExpiryMinutes,
            refreshTokenExpiryMinutes = config.refreshTokenExpiryMinutes,
            maxSessionDurationHours = config.maxSessionDurationHours,
            idleTimeoutMinutes = config.idleTimeoutMinutes,
            oidcIssuer = config.oidcIssuer,
            allowedAudiences = splitCsv(config.allowedAudiences),
            allowedAlgs = splitCsv(config.allowedAlgs),
            requiredClaims = splitCsv(config.requiredClaims),
            createdDate = config.createdDate.toString(),
            updatedDate = config.updatedDate.toString(),
        )
    }

    private fun splitCsv(value: String?): List<String>
    {
        return value
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?: emptyList()
    }
}
