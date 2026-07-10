package com.docuhyphen.app.api.interceptor

import jakarta.annotation.Priority
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.ext.Provider
import java.util.UUID

/**
 * Establishes server-trusted correlation context for every request, before authentication and
 * before the excluded-endpoint short-circuit in [EndpointVerificationFilter].
 *
 * Three identifiers are set on [AuthTokenContext] for the lifetime of the request:
 *  - [AuthTokenContext.serverTraceId]: always freshly server-generated, never client-derived.
 *  - [AuthTokenContext.correlationId]: reused from a well-formed incoming `X-Correlation-Id`
 *    header (to allow grouping a multi-service flow), otherwise freshly generated. Grouping only;
 *    it grants no authorization.
 *  - [AuthTokenContext.causationId]: taken from a well-formed incoming `X-Causation-Id` header
 *    when present, otherwise null.
 *
 * The client-supplied `X-Request-Id` header, if any, is preserved separately as
 * [AuthTokenContext.clientRequestIdHint] and is explicitly documented as an untrusted hint: it is
 * never used as the trace ID or the correlation ID.
 *
 * Runs at an early priority so correlation context exists even for excluded/no-auth endpoints and
 * for requests that are ultimately rejected by later filters.
 */
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
