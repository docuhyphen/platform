package com.docuhyphen.app.api.service.auth

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class TokenIssuanceCookieSecurityTest
{
    @Test
    fun `HTTPS deployment marks refresh and CSRF cookies secure`()
    {
        assertTrue(TokenIssuanceService.shouldUseSecureCookies("https://app.example.com"))
    }

    @Test
    fun `local HTTP development keeps cookies usable without TLS`()
    {
        val service = service()

        assertFalse(TokenIssuanceService.shouldUseSecureCookies("http://localhost:5173"))
        assertFalse(service.buildRefreshTokenCookie("refresh").isSecure)
        assertFalse(service.buildCsrfTokenCookie("csrf").isSecure)
    }

    private fun service(): TokenIssuanceService = TokenIssuanceService(
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
    )
}
