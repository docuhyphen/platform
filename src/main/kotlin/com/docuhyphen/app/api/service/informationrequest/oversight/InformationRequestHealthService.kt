package com.docuhyphen.app.api.service.informationrequest.oversight

import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestHealthIndicator
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestHealthIndicatorKey
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestHealthReport
import com.docuhyphen.app.api.model.informationrequest.oversight.InformationRequestHealthWindows
import com.docuhyphen.app.api.repository.informationrequest.oversight.InformationRequestHealthRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.UserRoleService
import io.quarkus.security.ForbiddenException
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import org.eclipse.microprofile.config.inject.ConfigProperty
import java.time.Clock
import java.time.temporal.ChronoUnit

@ApplicationScoped
class InformationRequestHealthService @Inject constructor(
    private val healthRepository: InformationRequestHealthRepository,
    private val userRoleService: UserRoleService,
    private val authAuditService: AuthAuditService,
    private val clock: Clock,
    @ConfigProperty(name = "app.information-request.health.notice-intent-minutes", defaultValue = "60")
    private val noticeIntentMinutes: Long,
    @ConfigProperty(name = "app.information-request.health.disposal-claim-hours", defaultValue = "24")
    private val disposalClaimHours: Long,
    @ConfigProperty(name = "app.information-request.health.event-backlog-minutes", defaultValue = "15")
    private val eventBacklogMinutes: Long,
    @ConfigProperty(name = "app.information-request.health.connector-failure-hours", defaultValue = "24")
    private val connectorFailureHours: Long,
)
{
    val windows: InformationRequestHealthWindows
        get() = InformationRequestHealthWindows(noticeIntentMinutes, disposalClaimHours, eventBacklogMinutes, connectorFailureHours)

    fun report(): InformationRequestHealthReport
    {
        val now = clock.instant()
        val counts = mapOf(
            InformationRequestHealthIndicatorKey.REQUESTS_WITHOUT_EXECUTION_GRANT to
                healthRepository.countIssuedWithoutExecutionGrant(),
            InformationRequestHealthIndicatorKey.RESERVATIONS_ABOVE_CAP to healthRepository.countReservationsAboveCap(),
            InformationRequestHealthIndicatorKey.NOTICE_INTENTS_OVERDUE to
                healthRepository.countNoticeIntentsWithoutNoticeBefore(now.minus(noticeIntentMinutes, ChronoUnit.MINUTES)),
            InformationRequestHealthIndicatorKey.CONNECTOR_EXCHANGES_FAILED to
                healthRepository.countConnectorExchangesFailedSince(
                    now.minus(connectorFailureHours, ChronoUnit.HOURS),
                    OPERATIONAL_CONNECTOR_FAILURES,
                ),
            InformationRequestHealthIndicatorKey.DISPOSAL_CLAIMS_STALLED to
                healthRepository.countDisposalClaimsClaimedBefore(now.minus(disposalClaimHours, ChronoUnit.HOURS)),
            InformationRequestHealthIndicatorKey.EVENT_DELIVERY_BACKLOG to
                healthRepository.countPendingRequestEventsBefore(now.minus(eventBacklogMinutes, ChronoUnit.MINUTES)),
        )
        return InformationRequestHealthReport(
            checkedAt = now,
            indicators = counts.map { (key, count) -> InformationRequestHealthIndicator(key, count, threshold = 0) },
        )
    }

    fun reportFor(principal: PrincipalRef, requestId: String?): InformationRequestHealthReport
    {
        if (principal.kind != PrincipalKind.USER || !userRoleService.isAppAdmin(principal.id))
        {
            audit("DENIED", principal, requestId, "Caller lacks effective App Administrator privilege")
            throw ForbiddenException("Only platform administrators can read the Information Request health report")
        }
        val report = report()
        audit("SUCCESS", principal, requestId, "Platform administrator read the Information Request health report")
        return report
    }

    private fun audit(outcome: String, principal: PrincipalRef, requestId: String?, reason: String)
    {
        authAuditService.emit(
            action = AuditEventType.PLATFORM_INFORMATION_REQUEST_HEALTH_VIEW.name,
            outcome = outcome,
            actorId = principal.id,
            requestId = requestId,
            reason = reason,
            targetType = "PLATFORM",
        )
    }

    private companion object
    {
        val OPERATIONAL_CONNECTOR_FAILURES =
            setOf("attempts_exhausted", "connector_unavailable", "result_rejected", "contract_version_changed")
    }
}
