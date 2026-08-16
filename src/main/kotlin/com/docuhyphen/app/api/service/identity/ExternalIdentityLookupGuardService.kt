package com.docuhyphen.app.api.service.identity

import com.docuhyphen.app.api.exception.OrganizationTrustRateLimitException
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.service.auth.AuthRateLimitService
import com.docuhyphen.app.api.service.security.SecurityIncidentService
import com.docuhyphen.app.api.service.config.ConfigurationService
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import java.nio.charset.StandardCharsets
import java.util.HexFormat
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@ApplicationScoped
class ExternalIdentityLookupGuardService @Inject constructor(
    private val authRateLimitService: AuthRateLimitService,
    private val securityIncidentService: SecurityIncidentService,
    private val configurationService: ConfigurationService,
)
{
    fun enforce(
        actorAppUserId: UUID,
        callerOrganizationId: UUID,
        targetOrganizationId: UUID,
        relationshipId: UUID,
        normalizedEmail: String,
        requestId: String?,
    )
    {
        val emailHash = keyedEmailHash(normalizedEmail)
        val dimensions = linkedMapOf(
            "actor" to actorAppUserId.toString(),
            "caller" to callerOrganizationId.toString(),
            "target" to targetOrganizationId.toString(),
            "relationship" to relationshipId.toString(),
            "email" to emailHash,
        )
        val limitedDimension = dimensions.entries.firstOrNull { (dimension, value) ->
            authRateLimitService.isLimited(
                key = "auth:external-identity-resolution:$dimension:$value",
                maxPerMinute = configurationService.getAuthRateLimitDirectoryPerMinute(),
            )
        } ?: return

        securityIncidentService.record(
            incidentType = SecurityIncidentType.AUTH_RATE_LIMIT_EXTERNAL_IDENTITY_RESOLUTION,
            severity = SecurityIncidentSeverity.MEDIUM,
            actorId = actorAppUserId,
            requestId = requestId,
            details = securityIncidentService.formatDetails(
                "dimension" to limitedDimension.key,
                "callerOrganizationId" to callerOrganizationId,
                "targetOrganizationId" to targetOrganizationId,
                "relationshipId" to relationshipId,
                "emailHash" to emailHash,
            ),
        )
        throw OrganizationTrustRateLimitException("Too many trusted member verification requests")
    }

    internal fun keyedEmailHash(normalizedEmail: String): String
    {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(configurationService.getJwtSecret().toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val digest = mac.doFinal("external-identity-resolution:$normalizedEmail".toByteArray(StandardCharsets.UTF_8))
        return HexFormat.of().formatHex(digest)
    }
}
