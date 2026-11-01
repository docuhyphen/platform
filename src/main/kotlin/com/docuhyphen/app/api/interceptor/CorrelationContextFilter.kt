package com.docuhyphen.app.api.interceptor

import jakarta.annotation.Priority
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.ext.Provider
import java.util.*

@Provider
@Priority(1000)
@RequestScoped
class CorrelationContextFilter @Inject constructor(
    private val authTokenContext: AuthTokenContext,
) : ContainerRequestFilter
{
    override fun filter(requestContext: ContainerRequestContext)
    {
        authTokenContext.serverTraceId = UUID.randomUUID().toString()

        val incomingCorrelationId = requestContext.getHeaderString("X-Correlation-Id")?.trim()
        authTokenContext.correlationId = parseUuidOrNull(incomingCorrelationId)?.toString()
            ?: UUID.randomUUID().toString()

        val incomingCausationId = requestContext.getHeaderString("X-Causation-Id")?.trim()
        authTokenContext.causationId = parseUuidOrNull(incomingCausationId)?.toString()

        val incomingRequestId = requestContext.getHeaderString("X-Request-Id")?.trim()
        authTokenContext.clientRequestIdHint = if (incomingRequestId.isNullOrBlank()) null else incomingRequestId
    }

    private fun parseUuidOrNull(value: String?): UUID?
    {
        if (value.isNullOrBlank())
        {
            return null
        }

        return runCatching { UUID.fromString(value) }.getOrNull()
    }
}
