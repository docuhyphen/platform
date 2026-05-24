package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.model.entity.UserSession
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import org.slf4j.LoggerFactory

enum class RiskLevel { NONE, LOW, MEDIUM, HIGH }

data class RiskAssessment(
    val level: RiskLevel,
    val reasons: List<String>,
)

/**
 * Lightweight, deterministic risk evaluation. Compares the request's IP /16 prefix and
 * User-Agent "family" against the session's stored context. Returns a level that the caller
 * uses to gate or block the request.
 *
 * Triggers:
 *   - IP /16 prefix change → LOW
 *   - UA family change → LOW
 *   - Both changes simultaneously → HIGH (treat as session takeover signal)
 */
@RequestScoped
class RiskSignalService @Inject constructor(
    private val securityIncidentService: SecurityIncidentService,
)
{
    companion object
    {
        private val logger = LoggerFactory.getLogger(RiskSignalService::class.java)
    }

    fun evaluate(session: UserSession, currentIp: String?, currentUserAgent: String?, requestId: String?): RiskAssessment
    {
        val reasons = mutableListOf<String>()

        val ipChanged = !sameIpv4Prefix16(session.ipAddress, currentIp)
        if (ipChanged) reasons += "ip_prefix_change"

        val uaChanged = !sameUserAgentFamily(session.userAgent, currentUserAgent)
        if (uaChanged) reasons += "ua_family_change"

        val level = when
        {
            ipChanged && uaChanged -> RiskLevel.HIGH
            ipChanged || uaChanged -> RiskLevel.LOW
            else -> RiskLevel.NONE
        }

        if (level >= RiskLevel.MEDIUM)
        {
            securityIncidentService.record(
                incidentType = SecurityIncidentType.AUTH_LOOKUP_SUSPICIOUS_PATTERN,
                severity = if (level == RiskLevel.HIGH) SecurityIncidentSeverity.HIGH else SecurityIncidentSeverity.MEDIUM,
                actorId = session.appUser?.id,
                requestId = requestId,
                details = "session=${session.sessionId};reasons=${reasons.joinToString(",")}",
            )
            logger.warn("Risk signal session={} level={} reasons={}", session.sessionId, level, reasons)
        }

        return RiskAssessment(level = level, reasons = reasons)
    }

    private fun sameIpv4Prefix16(a: String?, b: String?): Boolean
    {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return true
        val aParts = a.split('.')
        val bParts = b.split('.')
        if (aParts.size < 2 || bParts.size < 2) return a == b
        return aParts[0] == bParts[0] && aParts[1] == bParts[1]
    }

    private fun sameUserAgentFamily(a: String?, b: String?): Boolean
    {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return true
        return uaFamily(a) == uaFamily(b)
    }

    private fun uaFamily(ua: String): String = when
    {
        ua.contains("Edg/", ignoreCase = true) -> "edge"
        ua.contains("Chrome/", ignoreCase = true) -> "chrome"
        ua.contains("Firefox/", ignoreCase = true) -> "firefox"
        ua.contains("Safari/", ignoreCase = true) -> "safari"
        else -> "other"
    }
}
