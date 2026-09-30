package com.docuhyphen.app.api.service.informationrequest

import com.docuhyphen.app.api.model.informationrequest.InformationRequestHealthIndicatorKey
import com.docuhyphen.app.api.repository.informationrequest.InformationRequestHealthRepository
import com.docuhyphen.app.api.service.auth.AuthAuditService
import com.docuhyphen.app.api.service.auth.UserRoleService
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import io.quarkus.security.ForbiddenException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import java.util.UUID

class InformationRequestHealthServiceTest
{
    private val now = Instant.parse("2026-09-30T10:00:00Z")
    private val repository = mock<InformationRequestHealthRepository>()
    private val userRoleService = mock<UserRoleService>()
    private val authAuditService = mock<AuthAuditService>()
    private val service = InformationRequestHealthService(
        repository, userRoleService, authAuditService, Clock.fixed(now, ZoneOffset.UTC),
        noticeIntentMinutes = 60, disposalClaimHours = 24, eventBacklogMinutes = 15, connectorFailureHours = 24,
    )

    @Test
    fun `every indicator is counted against its own window and any count above zero is a breach`()
    {
        whenever(repository.countIssuedWithoutExecutionGrant()).thenReturn(2)
        whenever(repository.countReservationsAboveCap()).thenReturn(0)
        whenever(repository.countNoticeIntentsWithoutNoticeBefore(now.minus(60, ChronoUnit.MINUTES))).thenReturn(1)
        whenever(repository.countConnectorExchangesFailedSince(eq(now.minus(24, ChronoUnit.HOURS)), any())).thenReturn(0)
        whenever(repository.countDisposalClaimsClaimedBefore(now.minus(24, ChronoUnit.HOURS))).thenReturn(0)
        whenever(repository.countPendingRequestEventsBefore(now.minus(15, ChronoUnit.MINUTES))).thenReturn(3)

        val report = service.report()

        assertEquals(now, report.checkedAt)
        assertEquals(InformationRequestHealthIndicatorKey.entries.toSet(), report.indicators.map { it.key }.toSet())
        assertEquals(
            setOf(
                InformationRequestHealthIndicatorKey.REQUESTS_WITHOUT_EXECUTION_GRANT,
                InformationRequestHealthIndicatorKey.NOTICE_INTENTS_OVERDUE,
                InformationRequestHealthIndicatorKey.EVENT_DELIVERY_BACKLOG,
            ),
            report.indicators.filter { it.breached }.map { it.key }.toSet(),
        )
        assertFalse(report.healthy)
        verify(repository).countConnectorExchangesFailedSince(
            now.minus(24, ChronoUnit.HOURS),
            setOf("attempts_exhausted", "connector_unavailable", "result_rejected", "contract_version_changed"),
        )
    }

    @Test
    fun `only a platform administrator reads the report and each reading is audited`()
    {
        val administrator = PrincipalRef.user(UUID.randomUUID())
        val member = PrincipalRef.user(UUID.randomUUID())
        whenever(userRoleService.isAppAdmin(administrator.id)).thenReturn(true)
        whenever(userRoleService.isAppAdmin(member.id)).thenReturn(false)

        assertTrue(service.reportFor(administrator, "request-1").indicators.isNotEmpty())
        assertThrows<ForbiddenException> { service.reportFor(member, "request-2") }

        verify(authAuditService).emit(
            action = eq("PLATFORM_INFORMATION_REQUEST_HEALTH_VIEW"),
            outcome = eq("SUCCESS"),
            reasonCode = anyOrNull(),
            actorId = eq(administrator.id),
            actorRole = anyOrNull(),
            sessionId = anyOrNull(),
            organizationId = anyOrNull(),
            requestId = eq("request-1"),
            reason = anyOrNull(),
            beforeSnapshot = anyOrNull(),
            afterSnapshot = anyOrNull(),
            targetType = anyOrNull(),
            targetId = anyOrNull(),
            structuredDetails = any(),
        )
        verify(authAuditService).emit(
            action = eq("PLATFORM_INFORMATION_REQUEST_HEALTH_VIEW"),
            outcome = eq("DENIED"),
            reasonCode = anyOrNull(),
            actorId = eq(member.id),
            actorRole = anyOrNull(),
            sessionId = anyOrNull(),
            organizationId = anyOrNull(),
            requestId = eq("request-2"),
            reason = anyOrNull(),
            beforeSnapshot = anyOrNull(),
            afterSnapshot = anyOrNull(),
            targetType = anyOrNull(),
            targetId = anyOrNull(),
            structuredDetails = any(),
        )
        verify(repository).countReservationsAboveCap()
    }
}
