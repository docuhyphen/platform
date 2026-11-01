package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.service.auth.idp.IdentityProviderRegistry
import com.docuhyphen.app.api.service.config.ConfigurationService
import com.docuhyphen.app.api.service.identity.OrganizationIdpRuntimeCredentialService
import com.docuhyphen.app.api.service.security.SecurityIncidentService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.net.URI
import java.util.*

@ApplicationScoped
class OAuthAuthorizationService @Inject constructor(
    private val identityProviderRegistry: IdentityProviderRegistry,
    private val configurationService: ConfigurationService,
    private val oauthStateService: OAuthStateService,
    private val authAuditService: AuthAuditService,
    private val authRateLimitService: AuthRateLimitService,
    private val securityIncidentService: SecurityIncidentService,
    private val organizationIdpRuntimeCredentialService: OrganizationIdpRuntimeCredentialService,
    private val oauthRedirectUriResolver: OAuthRedirectUriResolver,
)
{
    fun authorize(
        providerName: String,
        flow: String?,
        orgIdpConfigId: String?,
        requestId: String?,
        clientIp: String,
    ): URI
    {
        if (authRateLimitService.isLimited(
                key = "auth:oauth:authorize:$clientIp",
                maxPerMinute = configurationService.getAuthRateLimitAuthorizePerMinute(),
            )
        )
        {
            securityIncidentService.record(
                incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_OAUTH_AUTHORIZE,
                severity = SecurityIncidentSeverity.MEDIUM,
                requestId = requestId,
                details = "ip=$clientIp;provider=$providerName",
            )
            authAuditService.emit(
                action = "OAUTH_AUTHORIZE",
                outcome = "DENY",
                reasonCode = RevocationReasonCode.SECURITY_POLICY,
                requestId = requestId,
            )
            throw AuthRequestRateLimitedException()
        }

        val providerType = IdentityProviderType.valueOf(providerName.uppercase())
        val provider = identityProviderRegistry.getProvider(providerType)

        val redirectUri = oauthRedirectUriResolver.resolve(providerType)
        val orgIdpConfigUuid = orgIdpConfigId
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val signedState = oauthStateService.createSignedState(flow ?: "signin", providerType, orgIdpConfigUuid)
        val runtimeCredentials = organizationIdpRuntimeCredentialService.resolve(providerType, orgIdpConfigUuid)
        val authUrl = provider.buildAuthorizationUrl(
            state = signedState.token,
            nonce = signedState.nonce,
            redirectUri = redirectUri,
            runtimeCredentials = runtimeCredentials,
            codeChallenge = signedState.codeChallenge,
        )

        authAuditService.emit(
            action = "OAUTH_AUTHORIZE",
            outcome = "SUCCESS",
            requestId = requestId,
        )

        return URI.create(authUrl)
    }

    fun recordAuthorizationFailure(requestId: String?)
    {
        authAuditService.emit(
            action = "OAUTH_AUTHORIZE",
            outcome = "DENY",
            reasonCode = RevocationReasonCode.SECURITY_POLICY,
            requestId = requestId,
        )
    }
}
