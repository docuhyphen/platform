package com.docuhyphen.app.api.service.config

import io.quarkus.runtime.StartupEvent
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import org.eclipse.microprofile.config.ConfigProvider
import org.slf4j.LoggerFactory

/**
 * Fail-fast guards for authentication configuration that is safe in development but unsafe
 * anywhere else. Misconfiguration here is silent at runtime and catastrophic in effect, so the
 * application refuses to start rather than serving traffic with a known-weak setup.
 */
@ApplicationScoped
class AuthSecurityStartupValidator @Inject constructor(
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(AuthSecurityStartupValidator::class.java)
        private val DEVELOPMENT_PROFILES = setOf("dev", "local", "test")
    }

    fun onStart(@Observes event: StartupEvent)
    {
        val profiles = activeProfiles()
        val isDevelopment = profiles.any { it in DEVELOPMENT_PROFILES }

        validateJwtSigningKey(isDevelopment, profiles)
        validateCsrfProtection(isDevelopment)
        validateMicrosoftTenantPinning(isDevelopment)
        validateTrustedProxies()
    }

    private fun validateJwtSigningKey(isDevelopment: Boolean, profiles: Set<String>)
    {
        if (!configurationService.isUsingDevelopmentJwtSecret())
        {
            return
        }

        if (!isDevelopment)
        {
            throw IllegalStateException(
                "The built-in development JWT signing key is active under profile(s) $profiles. " +
                    "Set JWT_SECRET, or set app.security.jwt.secret-provider=aws with a valid " +
                    "app.security.jwt.aws-secret-id, before starting this deployment."
            )
        }

        logger.warn(
            "Using the built-in development JWT signing key. This is acceptable only for local " +
                "development and must never reach a shared environment."
        )
    }

    private fun validateCsrfProtection(isDevelopment: Boolean)
    {
        if (configurationService.isCsrfEnabled() || isDevelopment)
        {
            return
        }

        throw IllegalStateException(
            "CSRF protection is disabled (app.auth.csrf.enabled=false) outside development. " +
                "Cookie-authenticated endpoints such as token refresh and sign-out would be " +
                "unprotected."
        )
    }

    private fun validateMicrosoftTenantPinning(isDevelopment: Boolean)
    {
        val tenantId = configurationService.microsoftOAuthTenantId.trim()
        val isMultiTenantPlaceholder = tenantId.lowercase() in setOf("common", "organizations", "consumers")

        if (!isMultiTenantPlaceholder || configurationService.isMicrosoftMultiTenantAllowed())
        {
            return
        }

        if (configurationService.microsoftOAuthClientId.isBlank())
        {
            // Microsoft sign-in is not configured for this deployment, nothing to pin.
            return
        }

        if (!isDevelopment)
        {
            throw IllegalStateException(
                "app.oauth.microsoft.tenant-id is '$tenantId', which accepts tokens from any " +
                    "Microsoft tenant. Pin MICROSOFT_TENANT_ID to your directory, or set " +
                    "app.oidc.microsoft.allow-multi-tenant=true if that is genuinely intended."
            )
        }

        logger.warn("Microsoft tenant is '{}' (multi-tenant). Tokens from any directory will be accepted.", tenantId)
    }

    private fun validateTrustedProxies()
    {
        if (!configurationService.isForwardedHeadersEnabled())
        {
            logger.info("Forwarded header processing is disabled; the TCP peer address is used as the client IP.")
            return
        }

        if (configurationService.getTrustedProxyCidrs().isEmpty())
        {
            logger.warn(
                "Forwarded header processing is enabled but app.auth.proxy.trusted-proxies is " +
                    "empty, so X-Forwarded-For will always be ignored."
            )
        }
    }

    private fun activeProfiles(): Set<String>
    {
        return ConfigProvider.getConfig()
            .getOptionalValue("quarkus.profile", String::class.java)
            .orElse("prod")
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
            .toSet()
    }
}

