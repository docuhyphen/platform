package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.extension.normalizeEmailOrNull
import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.Organization
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.resource.model.SignInLookupOrganizationOption
import com.docuhyphen.app.api.resource.model.SignInLookupRequest
import com.docuhyphen.app.api.resource.model.SignInLookupResponse
import com.docuhyphen.app.api.service.user.AppUserService
import com.docuhyphen.app.api.service.identity.OrganizationIdentityPolicyService
import com.docuhyphen.app.api.service.security.SecurityIncidentService
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.organization.OrganizationMembershipService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID

class InvalidSignInLookupException(message: String) : RuntimeException(message)

class SignInLookupRateLimitedException(message: String) : RuntimeException(message)

@ApplicationScoped
class SignInLookupService @Inject constructor(
    private val appUserService: AppUserService,
    private val organizationMembershipService: OrganizationMembershipService,
    private val organizationIdentityPolicyService: OrganizationIdentityPolicyService,
    private val identityProviderRegistry: IdentityProviderRegistry,
    private val configurationService: ConfigurationService,
    private val authAuditService: AuthAuditService,
    private val authRateLimitService: AuthRateLimitService,
    private val securityIncidentService: SecurityIncidentService,
)
{
    fun lookup(
        payload: SignInLookupRequest,
        clientIp: String,
        requestId: String?,
    ): SignInLookupResponse
    {
        enforceRateLimit(clientIp, requestId)
        val email = requireNormalizedEmail(payload.email, clientIp, requestId)
        enforceEmailRateLimit(email, requestId)
        enforceDistinctEmailProbeLimit(clientIp, email, requestId)
        val organizations = activeMembershipOrganizations(email)
        val selectedOrganizationId = parseSelectedOrganizationId(payload.orgId, clientIp, requestId)

        val response = when
        {
            selectedOrganizationId != null ->
            {
                val selectedOrganization = organizations.firstOrNull { it.id == selectedOrganizationId }
                    ?: rejectSelection(clientIp, requestId)
                responseForMemberOrganization(selectedOrganization)
            }

            organizations.size > 1 -> SignInLookupResponse(
                authMethod = IdentityProviderType.INTERNAL.name,
                outcome = "MULTIPLE_ORGS",
                organizations = organizations.map(::toOption),
                availableProviders = listOf(IdentityProviderType.INTERNAL.name),
            )

            organizations.size == 1 -> responseForMemberOrganization(organizations.first())
            else -> responseForConfiguredDomain(email) ?: platformResponse()
        }

        authAuditService.emit(
            action = "SIGN_IN_LOOKUP",
            outcome = "SUCCESS",
            requestId = requestId,
        )
        return response
    }

    private fun enforceRateLimit(clientIp: String, requestId: String?)
    {
        if (!authRateLimitService.isLimited(
                key = "auth:lookup:$clientIp",
                maxPerMinute = configurationService.getAuthRateLimitLookupPerMinute(),
            ))
        {
            return
        }

        securityIncidentService.record(
            incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_LOOKUP,
            severity = SecurityIncidentSeverity.MEDIUM,
            requestId = requestId,
            details = "ip=$clientIp",
        )
        recordDenied(requestId)
        throw SignInLookupRateLimitedException("Too many requests. Please try again later.")
    }

    /**
     * Caps how often a single address can be probed, independently of where the probe came from.
     *
     * A per-IP budget alone is defeated by a distributed prober, and this endpoint reports which
     * organization and identity provider an address belongs to. The key is a hash so the throttle
     * store never holds a list of addresses that were looked up.
     */
    private fun enforceEmailRateLimit(email: String, requestId: String?)
    {
        if (!authRateLimitService.isLimited(
                key = "auth:lookup:email:${hashForThrottleKey(email)}",
                maxPerMinute = configurationService.getAuthRateLimitLookupPerMinute(),
            ))
        {
            return
        }

        securityIncidentService.record(
            incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_LOOKUP,
            severity = SecurityIncidentSeverity.MEDIUM,
            requestId = requestId,
            details = "reason=per_email_budget_exhausted",
        )
        recordDenied(requestId)
        throw SignInLookupRateLimitedException("Too many requests. Please try again later.")
    }

    /**
     * Flags directory harvesting: many different addresses probed from one origin in a short
     * window looks nothing like a person signing in, even when each individual budget is intact.
     */
    private fun enforceDistinctEmailProbeLimit(clientIp: String, email: String, requestId: String?)
    {
        val distinctProbes = authRateLimitService.countDistinct(
            key = "auth:lookup:distinct:$clientIp",
            member = hashForThrottleKey(email),
            windowSeconds = DISTINCT_PROBE_WINDOW_SECONDS,
        )

        if (distinctProbes <= configurationService.getAuthRateLimitLookupPerMinute())
        {
            return
        }

        securityIncidentService.record(
            incidentType = SecurityIncidentType.AUTH_LOOKUP_SUSPICIOUS_PATTERN,
            severity = SecurityIncidentSeverity.HIGH,
            requestId = requestId,
            details = "ip=$clientIp;reason=distinct_email_enumeration;distinctEmails=$distinctProbes",
        )
        recordDenied(requestId, "Directory enumeration pattern")
        throw SignInLookupRateLimitedException("Too many requests. Please try again later.")
    }

    private fun hashForThrottleKey(value: String): String
    {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    private fun requireNormalizedEmail(
        rawEmail: String?,
        clientIp: String,
        requestId: String?,
    ): String
    {
        val normalizedEmail = rawEmail.normalizeEmailOrNull()
        if (normalizedEmail != null && EMAIL_PATTERN.matches(normalizedEmail))
        {
            return normalizedEmail
        }

        securityIncidentService.record(
            incidentType = SecurityIncidentType.AUTH_LOOKUP_SUSPICIOUS_PATTERN,
            severity = SecurityIncidentSeverity.LOW,
            requestId = requestId,
            details = "ip=$clientIp;reason=invalid_email_shape;len=${normalizedEmail?.length ?: 0}",
        )
        recordDenied(requestId, "Invalid lookup input")
        throw InvalidSignInLookupException("Email is required")
    }

    private fun activeMembershipOrganizations(email: String): List<Organization>
    {
        val appUser = appUserService.findRegisteredByEmail(email)
            ?.takeIf { it.isActive && it.deprovisionedAt == null }
            ?: return emptyList()

        return organizationMembershipService.activeOrganizationIds(appUser.id)
            .mapNotNull { organizationId ->
                runCatching { organizationIdentityPolicyService.findOrganizationById(organizationId) }
                    .getOrNull()
            }
            .filter { it.isActive }
            .sortedWith(compareBy<Organization> { it.name.lowercase() }.thenBy { it.id })
    }

    private fun parseSelectedOrganizationId(
        rawOrganizationId: String?,
        clientIp: String,
        requestId: String?,
    ): UUID?
    {
        val value = rawOrganizationId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { UUID.fromString(value) }.getOrElse {
            rejectSelection(clientIp, requestId)
        }
    }

    private fun rejectSelection(clientIp: String, requestId: String?): Nothing
    {
        securityIncidentService.record(
            incidentType = SecurityIncidentType.AUTH_LOOKUP_SUSPICIOUS_PATTERN,
            severity = SecurityIncidentSeverity.MEDIUM,
            requestId = requestId,
            details = "ip=$clientIp;reason=invalid_organization_selection",
        )
        recordDenied(requestId, "Invalid organization selection")
        throw InvalidSignInLookupException("Selected organization is not available for this sign-in")
    }

    private fun responseForMemberOrganization(organization: Organization): SignInLookupResponse
    {
        val option = toOption(organization)
        return responseForOrganization(organization, listOf(option), "ORG_FOUND")
            ?: SignInLookupResponse(
                authMethod = IdentityProviderType.INTERNAL.name,
                outcome = "ORG_FOUND",
                fallbackAuthMethod = IdentityProviderType.INTERNAL.name,
                organizations = listOf(option),
                availableProviders = listOf(IdentityProviderType.INTERNAL.name),
            )
    }

    private fun responseForConfiguredDomain(email: String): SignInLookupResponse?
    {
        val organizations = organizationIdentityPolicyService.resolveOrganizationsForEmail(email)
            .filter { it.isActive }
            .distinctBy { it.id }
        if (organizations.size != 1)
        {
            return null
        }

        return responseForOrganization(
            organization = organizations.first(),
            organizationOptions = emptyList(),
            outcome = "NO_ORG",
        )
    }

    private fun responseForOrganization(
        organization: Organization,
        organizationOptions: List<SignInLookupOrganizationOption>,
        outcome: String,
    ): SignInLookupResponse?
    {
        val configurations = organizationIdentityPolicyService
            .findActiveProviderConfigsForOrganization(organization.id)
        val internalAvailable = configurations.isEmpty() || configurations.any {
            it.provider.equals(IdentityProviderType.INTERNAL.name, ignoreCase = true)
        }
        val externalConfiguration = configurations.firstNotNullOfOrNull { configuration ->
            val providerType = runCatching {
                IdentityProviderType.valueOf(configuration.provider.uppercase())
            }.getOrNull()
            providerType
                ?.takeIf { it != IdentityProviderType.INTERNAL }
                ?.let { it to configuration }
        } ?: return null

        val providerType = externalConfiguration.first
        val configuration = externalConfiguration.second
        return SignInLookupResponse(
            authMethod = providerType.name,
            redirectUrl = buildAuthorizeUrl(providerType, configuration.id),
            outcome = outcome,
            fallbackAuthMethod = IdentityProviderType.INTERNAL.name.takeIf { internalAvailable },
            organizations = organizationOptions,
            availableProviders = listOf(providerType.name) +
                if (internalAvailable) listOf(IdentityProviderType.INTERNAL.name) else emptyList(),
        )
    }

    private fun platformResponse(): SignInLookupResponse
    {
        val platformProviders = identityProviderRegistry.getAllProviders()
            .map { it.getProviderType() }
            .filter { it != IdentityProviderType.INTERNAL }
            .map { it.name }
            .sorted()

        return SignInLookupResponse(
            authMethod = IdentityProviderType.INTERNAL.name,
            outcome = "NO_ORG",
            availableProviders = listOf(IdentityProviderType.INTERNAL.name) + platformProviders,
        )
    }

    private fun buildAuthorizeUrl(
        providerType: IdentityProviderType,
        organizationIdentityProviderConfigId: UUID,
    ): String
    {
        val base = configurationService.baseUrl.trimEnd('/')
        val provider = providerType.name.lowercase()
        val flow = URLEncoder.encode("signin", StandardCharsets.UTF_8)
        val configId = URLEncoder.encode(
            organizationIdentityProviderConfigId.toString(),
            StandardCharsets.UTF_8,
        )
        return "$base/auth/oauth/$provider/authorize?flow=$flow&orgIdpConfigId=$configId"
    }

    private fun toOption(organization: Organization): SignInLookupOrganizationOption =
        SignInLookupOrganizationOption(
            id = organization.id.toString(),
            name = organization.name,
        )

    private fun recordDenied(requestId: String?, reason: String? = null)
    {
        authAuditService.emit(
            action = "SIGN_IN_LOOKUP",
            outcome = "DENY",
            reasonCode = RevocationReasonCode.SECURITY_POLICY,
            requestId = requestId,
            reason = reason,
        )
    }

    private companion object
    {
        val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
        const val DISTINCT_PROBE_WINDOW_SECONDS = 600L
    }
}
