package com.docuhyphen.app.api.resource.auth

import com.docuhyphen.app.api.model.auth.OAuthTokenHandoff
import com.docuhyphen.app.api.resource.model.OAuthTokenExchangeRequest
import com.docuhyphen.app.api.resource.model.OAuthTokenExchangeResponse
import com.docuhyphen.app.api.service.auth.OAuthTokenHandoffService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OAuthResourceSecurityTest
{
    private val handoffService = mock<OAuthTokenHandoffService>()
    private val resource = OAuthResource(
        oauthAuthorizationService = mock(),
        oauthCallbackService = mock(),
        oauthLinkConfirmationService = mock(),
        oauthTokenHandoffService = handoffService,
        clientIpResolver = mock(),
    )

    @Test
    fun `token exchange consumes one-time handoff without tokens in the URL`()
    {
        whenever(handoffService.consume("one-time-code")).thenReturn(
            OAuthTokenHandoff("access-token", "id-token", false)
        )

        val response = resource.exchangeTokenHandoff(OAuthTokenExchangeRequest("one-time-code"))
        val payload = response.entity as OAuthTokenExchangeResponse

        assertEquals(200, response.status)
        assertEquals("access-token", payload.accessToken)
        assertEquals("id-token", payload.idToken)
    }

    @Test
    fun `token exchange rejects a blank code before consulting the handoff store`()
    {
        val response = resource.exchangeTokenHandoff(OAuthTokenExchangeRequest("  "))

        assertEquals(400, response.status)
    }

    @Test
    fun `token exchange answers unauthorized when the handoff code is unknown`()
    {
        whenever(handoffService.consume("stale-code")).thenReturn(null)

        val response = resource.exchangeTokenHandoff(OAuthTokenExchangeRequest("stale-code"))

        assertEquals(401, response.status)
    }
}
