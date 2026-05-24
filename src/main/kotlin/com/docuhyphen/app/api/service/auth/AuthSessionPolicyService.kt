package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.repository.OrganizationIdentityProviderConfigRepository
import com.docuhyphen.app.api.repository.OrganizationRepository
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject

data class AuthSessionPolicy(
    val accessTokenExpiryMinutes: Long,
    val refreshTokenExpiryDays: Long,
    val maxSessionDurationHours: Long,
)

@RequestScoped
class AuthSessionPolicyService @Inject constructor(
    private val organizationRepository: OrganizationRepository,
    private val organizationIdentityProviderConfigRepository: OrganizationIdentityProviderConfigRepository,
    private val configurationService: ConfigurationService,
)
{
    fun resolveForOrganization(organizationId: java.util.UUID): AuthSessionPolicy
    {
        val defaults = AuthSessionPolicy(
            accessTokenExpiryMinutes = configurationService.getAccessTokenExpiryMinutes(),
            refreshTokenExpiryDays = configurationService.getRefreshTokenExpiryDays(),
            maxSessionDurationHours = configurationService.getDefaultSessionMaxDurationHours(),
        )

        val activeConfigs = organizationIdentityProviderConfigRepository.findActiveByOrganizationId(organizationId)
        if (activeConfigs.isEmpty())
        {
            return applyGuardrails(defaults)
        }

        val policy = AuthSessionPolicy(
            accessTokenExpiryMinutes = activeConfigs.mapNotNull { it.accessTokenExpiryMinutes }.minOrNull()
                ?: defaults.accessTokenExpiryMinutes,
            refreshTokenExpiryDays = activeConfigs.mapNotNull { it.refreshTokenExpiryDays }.minOrNull()
                ?: defaults.refreshTokenExpiryDays,
            maxSessionDurationHours = activeConfigs.mapNotNull { it.maxSessionDurationHours }.minOrNull()
                ?: defaults.maxSessionDurationHours,
        )

        return applyGuardrails(policy)
    }

    fun resolveForAppUser(appUser: AppUser): AuthSessionPolicy
    {
        val personId = appUser.person?.id ?: return resolveForFallbackDefaults()
        val organization = organizationRepository.findByAppUserIdAndPersonId(appUser.id, personId) ?: return resolveForFallbackDefaults()
        return resolveForOrganization(organization.id)
    }

    private fun resolveForFallbackDefaults(): AuthSessionPolicy
    {
        return applyGuardrails(
            AuthSessionPolicy(
                accessTokenExpiryMinutes = configurationService.getAccessTokenExpiryMinutes(),
                refreshTokenExpiryDays = configurationService.getRefreshTokenExpiryDays(),
                maxSessionDurationHours = configurationService.getDefaultSessionMaxDurationHours(),
            )
        )
    }

    private fun applyGuardrails(policy: AuthSessionPolicy): AuthSessionPolicy
    {
        return AuthSessionPolicy(
            accessTokenExpiryMinutes = policy.accessTokenExpiryMinutes
                .coerceIn(
                    configurationService.getMinAccessTokenExpiryMinutes(),
                    configurationService.getMaxAccessTokenExpiryMinutes(),
                ),
            refreshTokenExpiryDays = policy.refreshTokenExpiryDays
                .coerceIn(
                    configurationService.getMinRefreshTokenExpiryDays(),
                    configurationService.getMaxRefreshTokenExpiryDays(),
                ),
            maxSessionDurationHours = policy.maxSessionDurationHours
                .coerceIn(
                    configurationService.getMinSessionMaxDurationHours(),
                    configurationService.getMaxSessionMaxDurationHours(),
                ),
        )
    }
}


