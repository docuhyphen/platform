package com.docuhyphen.app.api.service.informationrequest.access

import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.informationrequest.LockedInformationRequest
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestContinuationAction
import com.docuhyphen.app.api.model.informationrequest.execution.InformationRequestScheduledContinuation
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.execution.InformationRequestExecutionGrantService
import com.docuhyphen.app.api.service.informationrequest.execution.issuedExecutionGrant
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.sql.Timestamp
import java.time.Instant
import java.util.*

class InformationRequestContinuationGuardTest {
    private val grants = mock<InformationRequestExecutionGrantService>()
    private val entitlementGuard = mock<InformationRequestEntitlementGuard>()
    private val guard = InformationRequestContinuationGuard(grants, entitlementGuard)
    private val exchange = Exchange().apply { id = UUID.randomUUID() }
    private val request = InformationRequest().apply {
        id = UUID.randomUUID()
        exchangeId = exchange.id
    }
    private val locked = LockedInformationRequest(exchange, request)

    @Test
    fun `a request without a grant answers to the owner's live plan`() {
        guard.requireContinuation(locked, InformationRequestContinuationAction.RESPOND)

        verify(entitlementGuard).requireRequestMutation(exchange)
        verify(entitlementGuard, never()).requireNotOperationallySuspended(any())
    }

    @Test
    fun `a request holding a grant answers to an operational suspension rather than the live plan`() {
        whenever(grants.findForRequest(request.id)).thenReturn(grant())

        guard.requireContinuation(locked, InformationRequestContinuationAction.RESPOND)

        verify(entitlementGuard).requireNotOperationallySuspended(exchange)
        verify(entitlementGuard, never()).requireRequestMutation(any())
    }

    @Test
    fun `a revoked grant refuses continuation with a stable reason`() {
        whenever(grants.findForRequest(request.id)).thenReturn(grant().apply {
            revokedAt = Timestamp.from(Instant.now())
        })

        val refusal = assertThrows<InformationRequestLifecycleException> {
            guard.requireContinuation(locked, InformationRequestContinuationAction.RESPOND)
        }

        assertEquals(InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED, refusal.reasonCode)
        verify(entitlementGuard).requireNotOperationallySuspended(exchange)
    }

    @Test
    fun `an owner recovers a request whose grant was revoked but not one under an operational suspension`() {
        whenever(grants.findForRequest(request.id)).thenReturn(grant().apply {
            revokedAt = Timestamp.from(Instant.now())
        })

        guard.requireOwnerRecovery(locked, InformationRequestContinuationAction.CANCEL)

        verify(entitlementGuard).requireNotOperationallySuspended(exchange)
        verify(entitlementGuard, never()).requireRequestMutation(any())
    }

    @Test
    fun `issuance answers to the live plan until a grant exists and refuses a revoked grant`() {
        guard.requireIssuance(locked)
        verify(entitlementGuard).requireRequestMutation(exchange)

        whenever(grants.findForRequest(request.id)).thenReturn(grant().apply {
            revokedAt = Timestamp.from(Instant.now())
        })
        val refusal = assertThrows<InformationRequestLifecycleException> { guard.requireIssuance(locked) }

        assertEquals(InformationRequestErrorCatalog.EXECUTION_GRANT_REVOKED, refusal.reasonCode)
    }

    @Test
    fun `a grant refuses a continuation it does not list and allows every continuation it does list`() {
        val withoutSubmission =
            InformationRequestContinuationAction.entries - InformationRequestContinuationAction.SUBMIT
        whenever(grants.findForRequest(request.id)).thenReturn(
            grant().apply { continuationActions = withoutSubmission.map { it.name }.toTypedArray() },
        )

        val refusal = assertThrows<InformationRequestLifecycleException> {
            guard.requireContinuation(locked, InformationRequestContinuationAction.SUBMIT)
        }
        withoutSubmission.forEach { action -> guard.requireContinuation(locked, action) }

        assertEquals(InformationRequestErrorCatalog.CONTINUATION_NOT_GRANTED, refusal.reasonCode)
    }

    @Test
    fun `an owner recovery the grant does not list is refused even though a revocation would not stop it`() {
        whenever(grants.findForRequest(request.id)).thenReturn(
            grant().apply {
                revokedAt = Timestamp.from(Instant.now())
                continuationActions = arrayOf(InformationRequestContinuationAction.SUPERSEDE.name)
            },
        )

        val refusal = assertThrows<InformationRequestLifecycleException> {
            guard.requireOwnerRecovery(locked, InformationRequestContinuationAction.CANCEL)
        }
        guard.requireOwnerRecovery(locked, InformationRequestContinuationAction.SUPERSEDE)

        assertEquals(InformationRequestErrorCatalog.CONTINUATION_NOT_GRANTED, refusal.reasonCode)
    }

    @Test
    fun `scheduled work waits while the owner is operationally suspended whether or not the request holds a grant`() {
        whenever(entitlementGuard.isOperationallySuspended(exchange)).thenReturn(true)

        assertEquals(
            InformationRequestScheduledContinuation.OPERATIONALLY_SUSPENDED,
            guard.scheduledContinuationOf(locked, InformationRequestContinuationAction.SEND_REMINDER),
        )
        whenever(grants.findForRequest(request.id)).thenReturn(grant())
        assertEquals(
            InformationRequestScheduledContinuation.OPERATIONALLY_SUSPENDED,
            guard.scheduledContinuationOf(locked, InformationRequestContinuationAction.SEND_REMINDER),
        )
        assertEquals(InformationRequestScheduledContinuation.OPERATIONALLY_SUSPENDED, guard.scheduledStandingOf(locked))
    }

    @Test
    fun `scheduled work stops on a revoked grant for every action`() {
        whenever(grants.findForRequest(request.id)).thenReturn(grant().apply {
            revokedAt = Timestamp.from(Instant.now())
        })

        InformationRequestContinuationAction.entries.forEach { action ->
            assertEquals(
                InformationRequestScheduledContinuation.EXECUTION_GRANT_REVOKED,
                guard.scheduledContinuationOf(locked, action),
            )
        }
        assertEquals(InformationRequestScheduledContinuation.EXECUTION_GRANT_REVOKED, guard.scheduledStandingOf(locked))
    }

    @Test
    fun `scheduled work a grant does not list waits while the request itself keeps its standing`() {
        whenever(grants.findForRequest(request.id)).thenReturn(
            grant().apply {
                continuationActions =
                    (InformationRequestContinuationAction.entries - InformationRequestContinuationAction.SEND_REMINDER)
                        .map { it.name }
                        .toTypedArray()
            },
        )

        assertEquals(
            InformationRequestScheduledContinuation.CONTINUATION_NOT_GRANTED,
            guard.scheduledContinuationOf(locked, InformationRequestContinuationAction.SEND_REMINDER),
        )
        assertEquals(
            InformationRequestScheduledContinuation.PROCEEDS,
            guard.scheduledContinuationOf(locked, InformationRequestContinuationAction.RESPOND),
        )
        assertEquals(InformationRequestScheduledContinuation.PROCEEDS, guard.scheduledStandingOf(locked))
    }

    @Test
    fun `scheduled work on a request without a grant follows the owner's live plan`() {
        assertEquals(
            InformationRequestScheduledContinuation.OWNER_CHANGES_UNAVAILABLE,
            guard.scheduledContinuationOf(locked, InformationRequestContinuationAction.SEND_REMINDER),
        )

        whenever(entitlementGuard.permitsRequestMutation(exchange)).thenReturn(true)

        assertEquals(
            InformationRequestScheduledContinuation.PROCEEDS,
            guard.scheduledContinuationOf(locked, InformationRequestContinuationAction.SEND_REMINDER),
        )
        assertEquals(InformationRequestScheduledContinuation.PROCEEDS, guard.scheduledStandingOf(locked))
    }

    @Test
    fun `scheduled work under an issued grant continues after a commercial lapse`() {
        whenever(grants.findForRequest(request.id)).thenReturn(grant())

        assertEquals(
            InformationRequestScheduledContinuation.PROCEEDS,
            guard.scheduledContinuationOf(locked, InformationRequestContinuationAction.SEND_REMINDER),
        )
        verify(entitlementGuard, never()).permitsRequestMutation(any())
    }

    private fun grant() = issuedExecutionGrant().apply {
        requestId = request.id
        ownerType = "ORGANIZATION"
        planCode = "BUSINESS"
        subscriptionStatus = "ACTIVE"
        enforcementMode = "ENFORCE"
        issuedAt = Timestamp.from(Instant.now())
    }
}
