package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.config.ConfigurationService
import io.jsonwebtoken.Claims
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject

@RequestScoped
class ApplicationTokenBoundaryService @Inject constructor(
    private val configurationService: ConfigurationService,
)
{
    fun isApplicationPrincipal(claims: Claims): Boolean
    {
        val principalType = (claims["principal_type"] as? String)?.trim()?.uppercase().orEmpty()
        if (principalType == "APPLICATION")
        {
            return true
        }

        val legacyType = (claims["type"] as? String)?.trim()?.uppercase().orEmpty()
        return legacyType == "APPLICATION"
    }

    fun extractScopes(claims: Claims): Set<String>
    {
        val raw = (claims["scopes"] as? String)?.trim().orEmpty()
        if (raw.isBlank())
        {
            return emptySet()
        }

        return raw
            .split(',')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun isApplicationEndpoint(path: String): Boolean
    {
        val normalizedPath = normalizePath(path)
        val allowedPrefixes = configurationService.getApplicationTokenAllowedEndpointPrefixes()

        return allowedPrefixes.any { normalizedPath.startsWith(it) }
    }

    fun isApplicationTokenAllowedForPath(path: String, scopes: Set<String>): Boolean
    {
        if (scopes.isEmpty())
        {
            return false
        }

        if (!isApplicationEndpoint(path))
        {
            return false
        }

        val normalizedPath = normalizePath(path)
        val requiredScope = when
        {
            normalizedPath.startsWith("/auth/application/integration") -> configurationService.getApplicationTokenIntegrationScope()
            normalizedPath.startsWith("/auth/application/service") -> configurationService.getApplicationTokenServiceScope()
            else -> configurationService.getApplicationTokenRequiredScope()
        }

        return scopes.contains("application:*") || scopes.contains(requiredScope)
    }

    private fun normalizePath(path: String): String
    {
        val trimmed = path.trim()
        if (trimmed.isBlank())
        {
            return "/"
        }

        val withSlash = if (trimmed.startsWith('/')) trimmed else "/$trimmed"
        return withSlash.lowercase()
    }
}


