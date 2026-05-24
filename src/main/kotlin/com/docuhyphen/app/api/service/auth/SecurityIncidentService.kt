package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.model.entity.SecurityIncident
import com.docuhyphen.app.api.model.entity.SecurityIncidentSeverity
import com.docuhyphen.app.api.model.entity.SecurityIncidentType
import com.docuhyphen.app.api.repository.SecurityIncidentRepository
import jakarta.enterprise.context.RequestScoped
import jakarta.inject.Inject
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@RequestScoped
class SecurityIncidentService @Inject constructor(
    private val securityIncidentRepository: SecurityIncidentRepository,
    private val authAuditService: AuthAuditService,
)
{
    fun formatDetails(vararg fields: Pair<String, Any?>): String
    {
        return fields
            .filter { !it.first.isBlank() }
            .map { it.first.trim() to it.second?.toString()?.trim() }
            .filter { !it.second.isNullOrBlank() }
            .joinToString(";") { "${it.first}=${it.second}" }
    }

    fun record(
        incidentType: SecurityIncidentType,
        severity: SecurityIncidentSeverity,
        actorId: UUID? = null,
        requestId: String? = null,
        details: String? = null,
        reasonCode: RevocationReasonCode = RevocationReasonCode.SECURITY_POLICY,
    )
    {
        val sanitizedDetails = sanitize(details)
        securityIncidentRepository.save(
            SecurityIncident().apply {
                this.incidentType = incidentType.name
                this.severity = severity.name
                this.actorId = actorId
                this.requestId = requestId
                this.details = sanitizedDetails
                this.createdDate = Timestamp.from(Instant.now())
            }
        )

        authAuditService.emit(
            action = "SECURITY_INCIDENT",
            outcome = "SUCCESS",
            reasonCode = reasonCode,
            actorId = actorId,
            requestId = requestId,
            reason = incidentType.name,
            afterSnapshot = sanitizedDetails,
        )
    }

    fun findRecent(limit: Int, incidentType: String?): List<SecurityIncident>
    {
        return securityIncidentRepository.findRecent(limit = limit, incidentType = incidentType)
    }

    private fun sanitize(details: String?): String?
    {
        if (details.isNullOrBlank())
        {
            return null
        }

        var sanitized = details.take(2048)
        sanitized = sanitized.replace(Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"), "[REDACTED_EMAIL]")
        sanitized = sanitized.replace(Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"), "[REDACTED_JWT]")
        sanitized = sanitized.replace(Regex("(?i)(token|secret|password)=[^;\\s]+"), "$1=[REDACTED]")
        return sanitized
    }
}




