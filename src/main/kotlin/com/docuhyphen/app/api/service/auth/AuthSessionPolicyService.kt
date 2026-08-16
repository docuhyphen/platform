package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.repository.identity.OrganizationIdentityProviderConfigRepository
import com.docuhyphen.app.api.repository.organization.OrganizationRepository
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject

data class AuthSessionPolicy(
    val accessTokenExpiryMinutes: Long,
    val refreshTokenExpiryMinutes: Long,
    val maxSessionDurationHours: Long,
    val idleTimeoutMinutes: Long,
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
            refreshTokenExpiryMinutes = configurationService.getRefreshTokenExpiryMinutes(),
            maxSessionDurationHours = configurationService.getDefaultSessionMaxDurationHours(),
            idleTimeoutMinutes = configurationService.getIdleTimeoutMinutes(),
        )

        val activeConfigs = organizationIdentityProviderConfigRepository.findActiveByOrganizationId(organizationId)
        if (activeConfigs.isEmpty())
        {
            return applyGuardrails(defaults)
        }

        val policy = AuthSessionPolicy(
            accessTokenExpiryMinutes = activeConfigs.mapNotNull { it.accessTokenExpiryMinutes }.minOrNull()
                ?: defaults.accessTokenExpiryMinutes,
            refreshTokenExpiryMinutes = activeConfigs.mapNotNull { it.refreshTokenExpiryMinutes }.minOrNull()
                ?: defaults.refreshTokenExpiryMinutes,
            maxSessionDurationHours = activeConfigs.mapNotNull { it.maxSessionDurationHours }.minOrNull()
                ?: defaults.maxSessionDurationHours,
            idleTimeoutMinutes = activeConfigs.mapNotNull { it.idleTimeoutMinutes }.minOrNull()
                ?: defaults.idleTimeoutMinutes,
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
                refreshTokenExpiryMinutes = configurationService.getRefreshTokenExpiryMinutes(),
                maxSessionDurationHours = configurationService.getDefaultSessionMaxDurationHours(),
                idleTimeoutMinutes = configurationService.getIdleTimeoutMinutes(),
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
            refreshTokenExpiryMinutes = policy.refreshTokenExpiryMinutes
                .coerceIn(
                    configurationService.getMinRefreshTokenExpiryMinutes(),
                    configurationService.getMaxRefreshTokenExpiryMinutes(),
                ),
            maxSessionDurationHours = policy.maxSessionDurationHours
                .coerceIn(
                    configurationService.getMinSessionMaxDurationHours(),
                    configurationService.getMaxSessionMaxDurationHours(),
                ),
            idleTimeoutMinutes = policy.idleTimeoutMinutes
                .coerceIn(
                    configurationService.getMinIdleTimeoutMinutes(),
                    configurationService.getMaxIdleTimeoutMinutes(),
                ),
        )
    }
}


