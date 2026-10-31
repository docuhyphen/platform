package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeStatus
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.exchange.ExchangeLifecycleActor
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditOwnerScope
import com.docuhyphen.app.api.service.audit.AuditOwnerScopeResolver
import com.docuhyphen.app.api.service.audit.AuditRecorder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class ExchangeLifecycleAuditTest {
    private val exchangeId = UUID.randomUUID()
    private val recorder = mock<AuditRecorder>()
    private val owners = mock<AuditOwnerScopeResolver>().also {
        whenever(
            it.resolve(
                ResourceType.EXCHANGE,
                exchangeId
            )
        ).thenReturn(AuditOwnerScope.Organization(UUID.randomUUID()))
    }

    @Test
    fun `a status change whose audit record cannot be captured fails instead of committing unrecorded`() {
        whenever(recorder.record(any())).thenThrow(AuditCaptureFailedException("The audit outbox is unavailable", null))

        assertThrows<AuditCaptureFailedException> {
            ExchangeLifecycleAudit(recorder, owners).recordTransition(
                exchangeId,
                "Process collection",
                ExchangeStatus.ACCEPTED_STARTED,
                ExchangeStatus.ENDED,
                ExchangeLifecycleActor.WORKFLOW,
            )
        }
    }
}
