package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class AuthAuditServiceTest
{
    @Test
    fun `emit records a recognized authentication action on the canonical ledger path`()
    {
        val recorder = mock<AuditRecorder>()
        whenever(recorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        val service = AuthAuditService(recorder, AuthTokenContext())

        service.emit(action = "SIGN_IN_LOOKUP", outcome = "DENY")

        val draft = argumentCaptor<AuditEventDraft>()
        verify(recorder).record(draft.capture())
        assertEquals(AuditEventType.SIGN_IN_LOOKUP.key, draft.firstValue.eventTypeKey)
        assertEquals(AuditOutcome.DENIED, draft.firstValue.outcome)
    }

    @Test
    fun `emit does not record an action missing from the event catalog`()
    {
        val recorder = mock<AuditRecorder>()

        AuthAuditService(recorder, AuthTokenContext()).emit(action = "UNREGISTERED_ACTION", outcome = "SUCCESS")

        verify(recorder, never()).record(any())
    }

    @Test
    fun `authentication flow is not interrupted by audit validation or capture failures`()
    {
        val invalidRecorder = mock<AuditRecorder>()
        whenever(invalidRecorder.record(any())).thenThrow(AuditDraftInvalidException(listOf("invalid")))
        AuthAuditService(invalidRecorder, AuthTokenContext()).emit(action = "SIGN_OUT", outcome = "SUCCESS")

        val failedRecorder = mock<AuditRecorder>()
        whenever(failedRecorder.record(any())).thenThrow(AuditCaptureFailedException("failed", null))
        AuthAuditService(failedRecorder, AuthTokenContext()).emit(action = "SIGN_OUT", outcome = "SUCCESS")
    }

    @Test
    fun `required audit capture fails when the recorder degrades`()
    {
        val recorder = mock<AuditRecorder>()
        whenever(recorder.record(any())).thenReturn(AuditCaptureResult.Degraded(UUID.randomUUID(), "outbox unavailable"))

        assertThrows(AuditCaptureFailedException::class.java) {
            AuthAuditService(recorder, AuthTokenContext()).emitRequired(
                action = "APP_ADMIN_GRANT",
                outcome = "SUCCESS",
            )
        }
    }

    @Test
    fun `audit draft includes available request session and source context`()
    {
        val recorder = mock<AuditRecorder>()
        whenever(recorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        val context = AuthTokenContext().apply {
            authToken = AuthToken().apply { jti = "session-id" }
            clientRequestIdHint = "request-id"
            clientIp = "203.0.113.9"
        }

        AuthAuditService(recorder, context).emitRequired(
            action = "APP_ADMIN_LIST",
            outcome = "SUCCESS",
            structuredDetails = mapOf("result_count" to "2"),
        )

        val draft = argumentCaptor<AuditEventDraft>()
        verify(recorder).record(draft.capture())
        assertEquals("session-id", draft.firstValue.sessionId)
        assertEquals("request-id", draft.firstValue.payload["request_id"])
        assertEquals("203.0.113.9", draft.firstValue.payload["source_ip"])
        assertEquals("2", draft.firstValue.payload["result_count"])
    }

    @Test
    fun `outcome mapping remains bounded`()
    {
        assertEquals(AuditOutcome.SUCCESS, AuthAuditService.mapOutcome("SUCCESS"))
        assertEquals(AuditOutcome.DENIED, AuthAuditService.mapOutcome("DENY"))
        assertEquals(AuditOutcome.FAILURE, AuthAuditService.mapOutcome("FAILURE"))
        assertEquals(AuditOutcome.ERROR, AuthAuditService.mapOutcome("ERROR"))
        assertEquals(AuditOutcome.SUCCESS, AuthAuditService.mapOutcome("NO_ORG"))
    }
}
