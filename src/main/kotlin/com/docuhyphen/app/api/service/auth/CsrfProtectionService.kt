package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

@RequestScoped
class CsrfProtectionService @Inject constructor(
    private val configurationService: ConfigurationService,
)
{
    companion object
    {
        private val secureRandom = SecureRandom()
        private const val TOKEN_BYTES = 32
    }

    fun generateCsrfToken(): String
    {
        val bytes = ByteArray(TOKEN_BYTES).also(secureRandom::nextBytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

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

        if (!MessageDigest.isEqual(csrfCookie.toByteArray(Charsets.UTF_8), csrfHeader.toByteArray(Charsets.UTF_8)))
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

        if (!refererHeader.isNullOrBlank())
        {
            return refererHeader.startsWith("$expectedBase/") || refererHeader.trimEnd('/') == expectedBase
        }

        return false
    }
}
