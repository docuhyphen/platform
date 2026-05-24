package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.util.UUID

@RequestScoped
class CsrfProtectionService @Inject constructor(
    private val configurationService: ConfigurationService,
)
{
    fun generateCsrfToken(): String = UUID.randomUUID().toString()

    fun verify(
        csrfCookie: String?,
        csrfHeader: String?,
        originHeader: String?,
        refererHeader: String? = null,
    ): Boolean
    {
        if (!configurationService.isCsrfEnabled())
        {
            return true
        }

        if (csrfCookie.isNullOrBlank() || csrfHeader.isNullOrBlank())
        {
            return false
        }

        if (csrfCookie != csrfHeader)
        {
            return false
        }

        if (!configurationService.isCsrfRequireOriginCheckEnabled())
        {
            return true
        }

        val expectedBase = configurationService.baseUrl.trimEnd('/')

        if (!originHeader.isNullOrBlank())
        {
            return originHeader.trimEnd('/') == expectedBase
        }

        // Fall back to Referer when Origin is missing (some browsers/clients omit Origin on same-origin requests)
        if (!refererHeader.isNullOrBlank())
        {
            return refererHeader.startsWith("$expectedBase/") || refererHeader.trimEnd('/') == expectedBase
        }

        return false
    }
}

