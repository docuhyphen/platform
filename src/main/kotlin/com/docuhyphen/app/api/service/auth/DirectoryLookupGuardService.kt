package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.resource.model.ResponseError
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import jakarta.ws.rs.core.Response
import java.util.UUID

@RequestScoped
class DirectoryLookupGuardService @Inject constructor(
    private val authRateLimitService: AuthRateLimitService,
    private val securityIncidentService: SecurityIncidentService,
    private val authAuditService: AuthAuditService,
    private val authTokenContext: AuthTokenContext,
    private val configurationService: ConfigurationService,
)
{
    fun enforce(endpointKey: String, targetOrganizationId: String?, requestId: String?, query: String? = null): Response?
    {
        val actorId = authTokenContext.authToken.appUser?.id
        val scopedOrg = targetOrganizationId.orEmpty().ifBlank { "unknown" }

        // Anti-enumeration: enforce minimum query length when caller passes a search term
        if (query != null)
        {
            val minLen = configurationService.getDirectoryLookupMinQueryLength()
            if (query.trim().length < minLen)
            {
                securityIncidentService.record(
                    incidentType = SecurityIncidentType.AUTH_LOOKUP_SUSPICIOUS_PATTERN,
                    severity = SecurityIncidentSeverity.LOW,
                    actorId = actorId,
                    requestId = requestId,
                    details = "endpoint=$endpointKey;reason=short_query;len=${query.trim().length}",
                )
                authAuditService.emit(
                    action = "DIRECTORY_LOOKUP",
                    outcome = "DENY",
                    reasonCode = RevocationReasonCode.SECURITY_POLICY,
                    actorId = actorId,
                    organizationId = runCatching { UUID.fromString(scopedOrg) }.getOrNull(),
                    requestId = requestId,
                    reason = "Query below minimum length",
                )
                return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResponseError("Search query must be at least $minLen characters."))
                    .build()
            }
        }

        val limitKey = "auth:directory:$endpointKey:actor:${actorId ?: "anonymous"}:org:$scopedOrg"

        val limited = authRateLimitService.isLimited(
            key = limitKey,
            maxPerMinute = configurationService.getAuthRateLimitDirectoryPerMinute(),
        )

        if (!limited)
        {
            return null
        }

        securityIncidentService.record(
            incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_DIRECTORY_LOOKUP,
            severity = SecurityIncidentSeverity.MEDIUM,
            actorId = actorId,
            requestId = requestId,
            details = "endpoint=$endpointKey;targetOrganizationId=$scopedOrg",
        )

        authAuditService.emit(
            action = "DIRECTORY_LOOKUP",
            outcome = "DENY",
            reasonCode = RevocationReasonCode.SECURITY_POLICY,
            actorId = actorId,
            organizationId = runCatching { UUID.fromString(scopedOrg) }.getOrNull(),
            requestId = requestId,
            reason = "Directory lookup throttled",
            afterSnapshot = "endpoint=$endpointKey;targetOrganizationId=$scopedOrg",
        )

        return Response.status(Response.Status.TOO_MANY_REQUESTS)
            .entity(ResponseError("Too many directory lookup requests. Please try again later."))
            .build()
    }

    /** Cap a directory lookup result list to the configured maximum to prevent enumeration. */
    fun <T> capResults(results: List<T>): List<T>
    {
        val max = configurationService.getDirectoryLookupMaxResults()
        return if (results.size > max) results.take(max) else results
    }
}

