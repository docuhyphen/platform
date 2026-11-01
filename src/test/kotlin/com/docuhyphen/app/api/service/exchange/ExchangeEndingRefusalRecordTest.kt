package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.audit.AuditCaptureResult
import com.docuhyphen.app.api.model.audit.AuditDraftValidationResult
import com.docuhyphen.app.api.model.audit.AuditEventDraft
import com.docuhyphen.app.api.model.audit.AuditOwnerScope
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.notification.InAppNotificationService
import com.docuhyphen.app.api.service.notification.UserNotificationPreference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class ExchangeEndingRefusalRecordTest {
    private val exchangeId = UUID.randomUUID()
    private val organizationId = UUID.randomUUID()
    private val instanceId = UUID.randomUUID()
    private val recorder = mock<AuditRecorder>()
    private val owners = mock<AuditOwnerScopeResolver>().also {
        whenever(it.resolve(ResourceType.EXCHANGE, exchangeId)).thenReturn(AuditOwnerScope.Organization(organizationId))
    }

    @Test
    fun `a refused ending is recorded as a failed ending with its reason and how many requests refused`() {
        whenever(recorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        ExchangeLifecycleAudit(recorder, owners).recordEndingRefused(
            exchangeId,
            "Process collection",
            "REMAINING_REQUESTS_REQUIRE_CANCELLATION",
            2,
            instanceId,
        )

        val draft = argumentCaptor<AuditEventDraft>().also { verify(recorder).record(it.capture()) }.firstValue
        assertEquals(AuditEventType.EXCHANGE_ENDED.key, draft.eventTypeKey)
        assertEquals(AuditOutcome.FAILURE, draft.outcome)
        assertEquals(AuditActorKind.WORKFLOW, draft.actorKind)
        assertEquals(exchangeId.toString(), draft.targetId)
        assertEquals("Process collection", draft.targetLabel)
        assertEquals(AuditOwnerScope.Organization(organizationId), draft.owner)
        assertEquals(
            mapOf("reasonCode" to "REMAINING_REQUESTS_REQUIRE_CANCELLATION", "refusedRequestCount" to "2"),
            draft.payload,
        )
        assertEquals("exchange.ending.refused:$instanceId", draft.idempotencyKey)
        assertInstanceOf(AuditDraftValidationResult.Valid::class.java, AuditEventDraftValidator.validate(draft))
    }

    @Test
    fun `a refused ending that cannot be recorded under a fail-closed policy is not swallowed`() {
        whenever(recorder.record(any())).thenThrow(AuditCaptureFailedException("The audit outbox is unavailable", null))

        assertThrows<AuditCaptureFailedException> {
            ExchangeLifecycleAudit(recorder, owners).recordEndingRefused(exchangeId, null, "REASON", 1, instanceId)
        }
    }

    @Test
    fun `each person told about a refused ending receives one notice that names the Exchange`() {
        val notifications = mock<InAppNotificationService>()
        val requesterId = UUID.randomUUID()
        val approverId = UUID.randomUUID()
        val exchange = Exchange().apply {
            id = exchangeId
            name = "Process collection"
        }

        ExchangeLifecycleNotificationService(mock(), notifications)
            .publishEndingRefused(exchange, setOf(requesterId, approverId))

        listOf(requesterId, approverId).forEach { recipientId ->
            verify(notifications, times(1)).publishIfEnabled(
                eq(recipientId),
                eq(UserNotificationPreference.EXCHANGE_ENDED),
                eq("exchange.ending_refused"),
                eq("Exchange not ended"),
                eq("Exchange not ended: Process collection. Finish or cancel its open Information Requests, then end it again."),
                eq(mapOf("exchangeId" to exchangeId.toString())),
            )
        }
    }
}
