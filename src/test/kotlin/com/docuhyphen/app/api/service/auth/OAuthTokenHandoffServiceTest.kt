package com.docuhyphen.app.api.service.auth

import io.smallrye.mutiny.Uni
import io.vertx.mutiny.redis.client.Redis
import io.vertx.mutiny.redis.client.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OAuthTokenHandoffServiceTest
{
    @Test
    fun `handoff code carries tokens through a single Redis value`()
    {
        val redis = mock<Redis>()
        val setResponse = mock<Response>()
        val getResponse = mock<Response>()
        whenever(getResponse.toString()).thenReturn("access-token\nid-token\nfalse")
        whenever(redis.send(any()))
            .thenReturn(Uni.createFrom().item(setResponse))
            .thenReturn(Uni.createFrom().item(getResponse))
        val service = OAuthTokenHandoffService(redis)

        val code = service.create("access-token", "id-token", false)
        val handoff = service.consume(code)

        assertFalse(code.contains("access-token"))
        assertNotNull(handoff)
        assertEquals("access-token", handoff?.accessToken)
        assertEquals("id-token", handoff?.idToken)
        assertEquals(false, handoff?.isNewUser)
    }
}
