package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.repository.OrganizationIdentityProviderConfigRepository
import com.docuhyphen.app.api.service.auth.idp.OAuthJsonParser
import com.docuhyphen.app.api.service.auth.idp.RuntimeIdpCredentials
import com.docuhyphen.app.api.service.config.AwsSecretsManagerService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import java.time.Instant
import java.util.UUID

@RequestScoped
class OrganizationIdpRuntimeCredentialService @Inject constructor(
    private val organizationIdentityProviderConfigRepository: OrganizationIdentityProviderConfigRepository,
    private val awsSecretsManagerService: AwsSecretsManagerService,
    private val configurationService: ConfigurationService,
    private val securityIncidentService: SecurityIncidentService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(OrganizationIdpRuntimeCredentialService::class.java)
    }

    fun resolve(provider: IdentityProviderType, orgIdpConfigId: UUID?): RuntimeIdpCredentials?
    {
        if (orgIdpConfigId == null)
        {
            return null
        }

        val config = organizationIdentityProviderConfigRepository.findById(orgIdpConfigId)
            ?: throw IllegalArgumentException("Organization IdP configuration was not found")

        if (!config.isActive || !config.provider.equals(provider.name, ignoreCase = true))
        {
            throw IllegalArgumentException("Organization IdP configuration is inactive or does not match the provider")
        }

        // INTERNAL auth does not use external OAuth client credentials.
        if (provider == IdentityProviderType.INTERNAL)
        {
            return null
        }

        val clientId = config.clientId?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Organization IdP client ID is required")
        val clientSecretRef = config.clientSecretRef?.trim()?.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Organization IdP client secret reference is required")
        val clientSecret = resolveClientSecret(clientSecretRef)
        val scopes = config.scopes?.takeIf { it.isNotBlank() }
        val allowedAudiences = config.allowedAudiences
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        val allowedAlgs = config.allowedAlgs
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        val requiredClaims = config.requiredClaims
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()

        return RuntimeIdpCredentials(
            clientId = clientId,
            clientSecret = clientSecret,
            tenantId = config.tenantId,
            scopes = scopes,
            oidcIssuer = config.oidcIssuer,
            allowedAudiences = allowedAudiences,
            allowedAlgs = allowedAlgs,
            requiredClaims = requiredClaims,
        )
    }

    private fun resolveClientSecret(clientSecretRef: String): String
    {
        if (clientSecretRef.isBlank())
        {
            throw IllegalArgumentException("Organization IdP client secret reference is required")
        }

        val region = configurationService.getOrgIdpSecretsRegion()
        val lifecycleStatus = awsSecretsManagerService.getSecretLifecycleStatus(clientSecretRef, region)
        if (lifecycleStatus.disabled)
        {
            securityIncidentService.record(
                incidentType = SecurityIncidentType.ORG_IDP_SECRET_RUNTIME_POLICY_DENY,
                severity = SecurityIncidentSeverity.MEDIUM,
                details = securityIncidentService.formatDetails(
                    "reason" to "secret_disabled",
                    "secretRef" to clientSecretRef,
                ),
                reasonCode = RevocationReasonCode.ORG_IDP_SECRET_DISABLED,
            )
            throw IllegalArgumentException("Organization IdP client secret is disabled")
        }

        if (lifecycleStatus.rotationPhase.equals("RETIRE", ignoreCase = true))
        {
            securityIncidentService.record(
                incidentType = SecurityIncidentType.ORG_IDP_SECRET_RUNTIME_POLICY_DENY,
                severity = SecurityIncidentSeverity.MEDIUM,
                details = securityIncidentService.formatDetails(
                    "reason" to "retire_phase",
                    "secretRef" to clientSecretRef,
                    "phase" to lifecycleStatus.rotationPhase,
                ),
                reasonCode = RevocationReasonCode.ORG_IDP_SECRET_RETIRE_PENDING,
            )
            throw IllegalArgumentException("Organization IdP client secret is scheduled for retirement")
        }

        val configuredAllowedPhases = configurationService.getSecretsRotationRuntimeAllowedPhases()
        val runtimeAllowedPhases = if (configuredAllowedPhases.isEmpty())
        {
            setOf("MONITOR", "ACTIVATE")
        }
        else
        {
            configuredAllowedPhases
        }

        val currentPhase = lifecycleStatus.rotationPhase?.trim()?.uppercase()
        if (!currentPhase.isNullOrBlank() && currentPhase !in runtimeAllowedPhases)
        {
            securityIncidentService.record(
                incidentType = SecurityIncidentType.ORG_IDP_SECRET_RUNTIME_POLICY_DENY,
                severity = SecurityIncidentSeverity.MEDIUM,
                details = securityIncidentService.formatDetails(
                    "reason" to "phase_not_allowed",
                    "secretRef" to clientSecretRef,
                    "phase" to currentPhase,
                    "allowedPhases" to runtimeAllowedPhases.joinToString(","),
                ),
                reasonCode = RevocationReasonCode.ORG_IDP_SECRET_RUNTIME_PHASE_DENY,
            )
            throw IllegalArgumentException("Organization IdP client secret is not available during rotation phase $currentPhase")
        }

        val currentValue = runCatching { awsSecretsManagerService.getSecretString(clientSecretRef, region, "AWSCURRENT") }.getOrNull()
        val currentSecret = extractSecretValue(currentValue)
        if (!currentSecret.isNullOrBlank())
        {
            return currentSecret
        }

        val overlapActive = lifecycleStatus.overlapUntilEpochMillis?.let { it > Instant.now().toEpochMilli() } ?: false
        val allowPreviousDuringOverlap = configurationService.isSecretsRotationAllowPreviousDuringOverlapEnabled()
        if (allowPreviousDuringOverlap && overlapActive)
        {
            val previousValue = runCatching { awsSecretsManagerService.getSecretString(clientSecretRef, region, "AWSPREVIOUS") }.getOrNull()
            val previousSecret = extractSecretValue(previousValue)
            if (!previousSecret.isNullOrBlank())
            {
                logger.warn("Using AWSPREVIOUS secret during active overlap window for secretRef={}", clientSecretRef)
                return previousSecret
            }
        }

        throw IllegalArgumentException("Organization IdP client secret could not be resolved")
    }

    private fun extractSecretValue(secretValue: String?): String?
    {
        val value = secretValue?.trim().orEmpty()
        if (value.isBlank())
        {
            return null
        }

        if (value.startsWith("{"))
        {
            val map = OAuthJsonParser.parseJsonToMap(value)
            val candidate = (map["clientSecret"] as? String)
                ?: (map["client_secret"] as? String)
                ?: (map["secret"] as? String)
            if (!candidate.isNullOrBlank())
            {
                return candidate
            }
        }

        return value
    }
}









