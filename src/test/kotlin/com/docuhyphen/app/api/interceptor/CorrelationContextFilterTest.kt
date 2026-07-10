package com.docuhyphen.app.api.interceptor

import jakarta.ws.rs.container.ContainerRequestContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Phase 0 gate: the correlation filter always produces a non-null server trace ID even with no
 * client-supplied header, and treats `X-Request-Id` as an untrusted hint only.
 */
class CorrelationContextFilterTest
{
    private fun requestContextWithHeaders(headers: Map<String, String?>): ContainerRequestContext
    {
        val requestContext = mock<ContainerRequestContext>()
        headers.forEach { (name, value) ->
            whenever(requestContext.getHeaderString(name)).thenReturn(value)
        }
        return requestContext
    }

    @Test
    fun `always sets a non-null server trace id when no client headers are present`()
    {
        val authTokenContext = AuthTokenContext()
        val filter = CorrelationContextFilter(authTokenContext)

        filter.filter(requestContextWithHeaders(emptyMap()))

        assertNotNull(authTokenContext.serverTraceId)
        assertNotNull(authTokenContext.correlationId)
        assertNull(authTokenContext.causationId)
        assertNull(authTokenContext.clientRequestIdHint)
    }

    @Test
    fun `generates a fresh server trace id per request, never reused from a client header`()
    {
        val authTokenContext = AuthTokenContext()
        val filter = CorrelationContextFilter(authTokenContext)
        val spoofedTraceId = "not-a-real-trace-id"

        filter.filter(requestContextWithHeaders(mapOf("X-Trace-Id" to spoofedTraceId)))

        assertNotNull(authTokenContext.serverTraceId)
        assertNotEquals(spoofedTraceId, authTokenContext.serverTraceId)
    }

    @Test
    fun `reuses a well-formed incoming correlation id`()
    {
        val authTokenContext = AuthTokenContext()
        val filter = CorrelationContextFilter(authTokenContext)
        val incomingCorrelationId = UUID.randomUUID().toString()

        filter.filter(requestContextWithHeaders(mapOf("X-Correlation-Id" to incomingCorrelationId)))

        assertEquals(incomingCorrelationId, authTokenContext.correlationId)
    }

    @Test
    fun `ignores a malformed incoming correlation id and generates a fresh one`()
    {
        val authTokenContext = AuthTokenContext()
        val filter = CorrelationContextFilter(authTokenContext)

        filter.filter(requestContextWithHeaders(mapOf("X-Correlation-Id" to "not-a-uuid")))

        assertNotNull(authTokenContext.correlationId)
        assertNotEquals("not-a-uuid", authTokenContext.correlationId)
    }

    @Test
    fun `keeps client-supplied X-Request-Id as an untrusted hint, never as trace or correlation id`()
    {
        val authTokenContext = AuthTokenContext()
        val filter = CorrelationContextFilter(authTokenContext)
        val clientRequestId = "client-supplied-value"

        filter.filter(requestContextWithHeaders(mapOf("X-Request-Id" to clientRequestId)))

        assertEquals(clientRequestId, authTokenContext.clientRequestIdHint)
        assertNotEquals(clientRequestId, authTokenContext.serverTraceId)
        assertNotEquals(clientRequestId, authTokenContext.correlationId)
    }

    @Test
    fun `captures a well-formed incoming causation id`()
    {
        val authTokenContext = AuthTokenContext()
        val filter = CorrelationContextFilter(authTokenContext)
        val incomingCausationId = UUID.randomUUID().toString()

        filter.filter(requestContextWithHeaders(mapOf("X-Causation-Id" to incomingCausationId)))

        assertEquals(incomingCausationId, authTokenContext.causationId)
    }
}
