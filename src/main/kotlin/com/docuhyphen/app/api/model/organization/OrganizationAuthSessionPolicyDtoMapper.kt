package com.docuhyphen.app.api.model.organization

import com.docuhyphen.app.api.model.auth.AuthSessionPolicy
import com.docuhyphen.app.api.model.entity.OrganizationIdentityProviderConfig
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyEffectiveDto
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyGuardrailsDto
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyIdpDto
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicyResponse
import com.docuhyphen.app.api.resource.model.OrganizationAuthSessionPolicySettingsResponse
import com.docuhyphen.app.api.service.config.ConfigurationService
import java.util.*

object OrganizationAuthSessionPolicyDtoMapper
{
    fun toEffectiveResponse(
        organizationId: UUID,
        appUserId: String?,
        policy: AuthSessionPolicy,
    ): OrganizationAuthSessionPolicyResponse
    {
        return OrganizationAuthSessionPolicyResponse(
            organizationId = organizationId.toString(),
            appUserId = appUserId,
            accessTokenExpiryMinutes = policy.accessTokenExpiryMinutes,
            refreshTokenExpiryMinutes = policy.refreshTokenExpiryMinutes,
            maxSessionDurationHours = policy.maxSessionDurationHours,
            idleTimeoutMinutes = policy.idleTimeoutMinutes,
        )
    }

    fun toSettingsResponse(
        organizationId: UUID,
        policy: AuthSessionPolicy,
        configuration: ConfigurationService,
        configs: List<OrganizationIdentityProviderConfig>,
    ): OrganizationAuthSessionPolicySettingsResponse
    {
        return OrganizationAuthSessionPolicySettingsResponse(
            organizationId = organizationId.toString(),
            effective = OrganizationAuthSessionPolicyEffectiveDto(
                accessTokenExpiryMinutes = policy.accessTokenExpiryMinutes,
                refreshTokenExpiryMinutes = policy.refreshTokenExpiryMinutes,
                maxSessionDurationHours = policy.maxSessionDurationHours,
                idleTimeoutMinutes = policy.idleTimeoutMinutes,
            ),
            guardrails = toGuardrails(configuration),
            idpConfigs = configs.map(::toIdpDto),
            hasActiveIdpConfig = configs.any { it.isActive },
        )
    }

    private fun toGuardrails(configuration: ConfigurationService): OrganizationAuthSessionPolicyGuardrailsDto
    {
        return OrganizationAuthSessionPolicyGuardrailsDto(
            minAccessTokenExpiryMinutes = configuration.getMinAccessTokenExpiryMinutes(),
            maxAccessTokenExpiryMinutes = configuration.getMaxAccessTokenExpiryMinutes(),
            minRefreshTokenExpiryMinutes = configuration.getMinRefreshTokenExpiryMinutes(),
            maxRefreshTokenExpiryMinutes = configuration.getMaxRefreshTokenExpiryMinutes(),
            minSessionMaxDurationHours = configuration.getMinSessionMaxDurationHours(),
            maxSessionMaxDurationHours = configuration.getMaxSessionMaxDurationHours(),
            minIdleTimeoutMinutes = configuration.getMinIdleTimeoutMinutes(),
            maxIdleTimeoutMinutes = configuration.getMaxIdleTimeoutMinutes(),
        )
    }

    fun policySnapshot(organizationId: String, appUserId: String?, policy: AuthSessionPolicy): String
    {
        return "organizationId=$organizationId;appUserId=${appUserId ?: ""};" +
                "accessTokenExpiryMinutes=${policy.accessTokenExpiryMinutes};" +
                "refreshTokenExpiryMinutes=${policy.refreshTokenExpiryMinutes};" +
                "maxSessionDurationHours=${policy.maxSessionDurationHours};" +
                "idleTimeoutMinutes=${policy.idleTimeoutMinutes}"
    }

    private fun toIdpDto(config: OrganizationIdentityProviderConfig): OrganizationAuthSessionPolicyIdpDto
    {
        return OrganizationAuthSessionPolicyIdpDto(
            configId = config.id.toString(),
            provider = config.provider,
            isActive = config.isActive,
            accessTokenExpiryMinutes = config.accessTokenExpiryMinutes,
            refreshTokenExpiryMinutes = config.refreshTokenExpiryMinutes,
            maxSessionDurationHours = config.maxSessionDurationHours,
            idleTimeoutMinutes = config.idleTimeoutMinutes,
        )
    }
}
