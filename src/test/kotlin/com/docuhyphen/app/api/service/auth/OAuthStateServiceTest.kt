package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.IdentityProviderType
import com.docuhyphen.app.api.service.config.ConfigurationService
import io.smallrye.mutiny.Uni
import io.vertx.mutiny.redis.client.Redis
import io.vertx.mutiny.redis.client.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

class OAuthStateServiceTest
{
    @Test
    fun `link flow binds the authenticated user into signed one-time state`()
    {
        val configuration = mock<ConfigurationService>()
        whenever(configuration.getJwtSecret()).thenReturn("a-secure-test-secret-that-is-longer-than-32-bytes")
        whenever(configuration.getOauthStateTtlSeconds()).thenReturn(300)
        val redis = mock<Redis>()
        val stored = mock<Response>()
        val deleted = mock<Response>()
        val verifier = mock<Response>()
        whenever(deleted.toLong()).thenReturn(1)
        whenever(verifier.toString()).thenReturn("pkce-verifier")
        whenever(redis.send(any()))
            .thenReturn(Uni.createFrom().item(stored))
            .thenReturn(Uni.createFrom().item(stored))
            .thenReturn(Uni.createFrom().item(deleted))
            .thenReturn(Uni.createFrom().item(verifier))
        val service = OAuthStateService(configuration, TokenSigningKeyProvider(configuration), redis)
        val appUserId = UUID.randomUUID()

        val state = service.createSignedState(
            flow = "link",
            provider = IdentityProviderType.GOOGLE,
            linkAppUserId = appUserId,
        )
        val verified = service.verifyAndConsumeState(state.token, IdentityProviderType.GOOGLE)

        assertNotNull(verified)
        assertEquals("link", verified?.flow)
        assertEquals(appUserId, verified?.linkAppUserId)
        assertEquals("pkce-verifier", verified?.codeVerifier)
    }

    @Test
    fun `state signed for OAuth cannot be verified with the session token key`()
    {
        val configuration = mock<ConfigurationService>()
        whenever(configuration.getJwtSecret()).thenReturn("a-secure-test-secret-that-is-longer-than-32-bytes")
        val keyProvider = TokenSigningKeyProvider(configuration)

        val stateKey = keyProvider.keyFor(TokenPurpose.OAUTH_STATE)
        val userKey = keyProvider.keyFor(TokenPurpose.USER_TOKEN)
        val linkKey = keyProvider.keyFor(TokenPurpose.LINK_TOKEN)

        assertNotEquals(stateKey.encoded.toList(), userKey.encoded.toList())
        assertNotEquals(stateKey.encoded.toList(), linkKey.encoded.toList())
        assertNotEquals(userKey.encoded.toList(), linkKey.encoded.toList())
    }
}
