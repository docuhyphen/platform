package com.docuhyphen.app.api.service.auth

import com.docuhyphen.app.api.repository.AuthAuditEventRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditDraftInvalidException
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import com.docuhyphen.app.api.service.config.ConfigurationService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.util.UUID

/**
 * Phase 3 gate (AUDIT-ARCHITECTURE-IMPLEMENTATION.md, "Route AuthAuditService.emit through
 * AuditRecorder"): [AuthAuditService.emit] dual-writes onto [AuditRecorder] in addition to the
 * legacy `auth_audit_event` table, and the hash-omission gap (event id, actor role, target type,
 * target id) is fixed.
 */
class AuthAuditServiceTest
{
    private fun service(
        auditImmutableEnabled: Boolean = false,
        auditRecorder: AuditRecorder = mock(),
    ): Triple<AuthAuditService, AuthAuditEventRepository, AuditRecorder>
    {
        val authAuditEventRepository = mock<AuthAuditEventRepository>()
        val configurationService = mock<ConfigurationService>()
        whenever(configurationService.isAuditImmutableEnabled()).thenReturn(auditImmutableEnabled)
        val wormSink = mock<AuthAuditWormSink>()

        val service = AuthAuditService(authAuditEventRepository, configurationService, wormSink, auditRecorder)
        return Triple(service, authAuditEventRepository, auditRecorder)
    }

    @Test
    fun `mapLegacyOutcome maps known and unknown outcomes`()
    {
        assertEquals(AuditOutcome.SUCCESS, AuthAuditService.mapLegacyOutcome("SUCCESS"))
        assertEquals(AuditOutcome.DENIED, AuthAuditService.mapLegacyOutcome("DENY"))
        assertEquals(AuditOutcome.FAILURE, AuthAuditService.mapLegacyOutcome("FAILURE"))
        assertEquals(AuditOutcome.ERROR, AuthAuditService.mapLegacyOutcome("ERROR"))
        // Descriptive, non-failure lookup outcomes fall back to SUCCESS rather than being lost.
        assertEquals(AuditOutcome.SUCCESS, AuthAuditService.mapLegacyOutcome("NO_ORG"))
        assertEquals(AuditOutcome.SUCCESS, AuthAuditService.mapLegacyOutcome("MULTIPLE_ORGS"))
    }

    @Test
    fun `emit dual-writes a recognized action onto AuditRecorder`()
    {
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        val (service, _, recorder) = service(auditRecorder = auditRecorder)

        service.emit(action = "SIGN_IN_LOOKUP", outcome = "DENY")

        val captor = org.mockito.kotlin.argumentCaptor<AuditEventDraft>()
        verify(recorder).record(captor.capture())
        assertEquals(AuditEventType.SIGN_IN_LOOKUP.key, captor.firstValue.eventTypeKey)
        assertEquals(AuditOutcome.DENIED, captor.firstValue.outcome)
    }

    @Test
    fun `emit skips the recorder for an action with no catalog entry, without throwing`()
    {
        val auditRecorder = mock<AuditRecorder>()
        val (service, _, recorder) = service(auditRecorder = auditRecorder)

        service.emit(action = "SOME_UNMAPPED_LEGACY_ACTION", outcome = "SUCCESS")

        verify(recorder, never()).record(any())
    }

    @Test
    fun `emit never propagates a recorder capture failure`()
    {
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenThrow(AuditCaptureFailedException("boom", null))
        val (service, _, _) = service(auditRecorder = auditRecorder)

        // Must not throw: the auth flow that called emit() must not fail due to the dual write.
        service.emit(action = "SIGN_OUT", outcome = "SUCCESS")
    }

    @Test
    fun `emit never propagates an invalid-draft rejection from the recorder`()
    {
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenThrow(AuditDraftInvalidException(listOf("bad payload")))
        val (service, _, _) = service(auditRecorder = auditRecorder)

        service.emit(action = "SIGN_OUT", outcome = "SUCCESS")
    }

    @Test
    fun `emit still dual-writes onto the recorder when the legacy immutable table is disabled`()
    {
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        val (service, authAuditEventRepository, recorder) = service(auditImmutableEnabled = false, auditRecorder = auditRecorder)

        service.emit(action = "SIGN_OUT", outcome = "SUCCESS")

        verify(recorder).record(any())
        verify(authAuditEventRepository, never()).save(any())
    }
}

/**
 * The hash chain must cover event id, actor role, target type, and target id (the Phase 3 fix for
 * the hash-omission gap flagged in AUDIT-ARCHITECTURE-IMPLEMENTATION.md). [AuthAuditService.hashEvent]
 * is private, so this test invokes it via reflection rather than duplicating its implementation.
 */
class AuthAuditServiceHashCoverageTest
{
    private fun invokeHashEvent(
        eventId: UUID,
        action: String = "SIGN_OUT",
        outcome: String = "SUCCESS",
        actorId: UUID? = null,
        actorRole: String? = null,
        targetType: String? = null,
        targetId: String? = null,
    ): String
    {
        val method = AuthAuditService::class.java.getDeclaredMethod(
            "hashEvent",
            UUID::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            UUID::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            UUID::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            String::class.java,
            Instant::class.java,
            String::class.java,
        )
        method.isAccessible = true

        val service = AuthAuditService(mock(), mock<ConfigurationService>().also {
            whenever(it.isAuditImmutableEnabled()).thenReturn(false)
        }, mock(), mock())

        return method.invoke(
            service,
            eventId, action, outcome, null, actorId, actorRole, targetType, targetId,
            null, null, null, null, null, null, Instant.EPOCH, null,
        ) as String
    }

    @Test
    fun `hash changes when eventId differs, all else equal`()
    {
        val h1 = invokeHashEvent(eventId = UUID.fromString("00000000-0000-0000-0000-000000000001"))
        val h2 = invokeHashEvent(eventId = UUID.fromString("00000000-0000-0000-0000-000000000002"))
        assertNotEquals(h1, h2)
    }

    @Test
    fun `hash changes when actorRole differs, all else equal`()
    {
        val id = UUID.randomUUID()
        val h1 = invokeHashEvent(eventId = id, actorRole = "ADMIN")
        val h2 = invokeHashEvent(eventId = id, actorRole = "MEMBER")
        assertNotEquals(h1, h2)
    }

    @Test
    fun `hash changes when targetType or targetId differs, all else equal`()
    {
        val id = UUID.randomUUID()
        val base = invokeHashEvent(eventId = id, targetType = "Organization", targetId = "org-1")
        val differentType = invokeHashEvent(eventId = id, targetType = "AppUser", targetId = "org-1")
        val differentId = invokeHashEvent(eventId = id, targetType = "Organization", targetId = "org-2")

        assertNotEquals(base, differentType)
        assertNotEquals(base, differentId)
    }

    @Test
    fun `hash is deterministic for identical inputs`()
    {
        val id = UUID.randomUUID()
        val h1 = invokeHashEvent(eventId = id, actorRole = "ADMIN", targetType = "Organization", targetId = "org-1")
        val h2 = invokeHashEvent(eventId = id, actorRole = "ADMIN", targetType = "Organization", targetId = "org-1")
        assertEquals(h1, h2)
    }
}
