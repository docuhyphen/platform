package com.docuhyphen.app.api.service.audit

import com.docuhyphen.app.api.interceptor.AuthTokenContext
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.AuditOutboxEntry
import com.docuhyphen.app.api.model.entity.AuthToken
import com.docuhyphen.app.api.repository.audit.AuditOutboxRepository
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.audit.catalog.AuditOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

/**
 * Verifies [AuditRecorder]:
 *  - a valid draft is validated, derives context, and writes exactly one outbox row.
 *  - an invalid draft (unknown event type) always throws, regardless of failure policy.
 *  - a retried capture attempt (same idempotency key) does not insert a duplicate row.
 *  - FAIL_CLOSED: a forced outbox-write failure propagates out of [AuditRecorder.record] so the
 *    caller's `@Transactional` method rolls back.
 *  - DEGRADED: a forced outbox-write failure is swallowed and reported as [AuditCaptureResult.Degraded].
 */
class AuditRecorderTest
{
    private val userId: UUID = UUID.randomUUID()

    private fun makeTokenContext(): AuthTokenContext
    {
        val ctx = AuthTokenContext()
        ctx.authToken = AuthToken().apply {
            appUser = AppUser().apply { id = userId }
        }
        ctx.serverTraceId = "trace-1"
        ctx.correlationId = "corr-1"
        return ctx
    }

    private fun makeResolver(policy: AuditFailurePolicy): AuditFailurePolicyResolver
    {
        val resolver = mock<AuditFailurePolicyResolver>()
        whenever(resolver.resolve(any())).thenReturn(policy)
        return resolver
    }

    private fun validDraft(idempotencyKey: String? = null): AuditEventDraft = AuditEventDraft(
        owner = AuditOwnerScope.Platform,
        eventTypeKey = AuditEventType.EXCHANGE_RESCINDED.key,
        outcome = AuditOutcome.SUCCESS,
        actorId = userId,
        targetType = "EXCHANGE",
        targetId = UUID.randomUUID().toString(),
        payload = mapOf("previousStatus" to "INITIATED", "newStatus" to "RESCINDED"),
        idempotencyKey = idempotencyKey,
    )

    @Test
    fun `valid draft is captured exactly once with derived context`()
    {
        val repo = mock<AuditOutboxRepository>()
        whenever(repo.findByIdempotencyKey(any())).thenReturn(null)
        whenever(repo.insert(any())).thenAnswer { inv -> inv.getArgument<AuditOutboxEntry>(0).also { it.id = UUID.randomUUID() } }

        val recorder = AuditRecorder(repo, makeTokenContext(), makeResolver(AuditFailurePolicy.DEGRADED))

        val result = recorder.record(validDraft())

        assertTrue(result is AuditCaptureResult.Captured)
        verify(repo, org.mockito.kotlin.times(1)).insert(any())
    }

    @Test
    fun `organization owner is used even when active organization context is different`()
    {
        val ownerOrganizationId = UUID.randomUUID()
        val context = makeTokenContext().apply { activeOrganizationId = UUID.randomUUID() }
        val repo = mock<AuditOutboxRepository>()
        whenever(repo.findByIdempotencyKey(any())).thenReturn(null)
        whenever(repo.insert(any())).thenAnswer { invocation ->
            invocation.getArgument<AuditOutboxEntry>(0).also { it.id = UUID.randomUUID() }
        }
        val recorder = AuditRecorder(repo, context, makeResolver(AuditFailurePolicy.DEGRADED))

        recorder.record(validDraft().copy(owner = AuditOwnerScope.Organization(ownerOrganizationId)))

        val entry = org.mockito.kotlin.argumentCaptor<AuditOutboxEntry>()
        verify(repo).insert(entry.capture())
        assertEquals(ownerOrganizationId, entry.firstValue.organizationId)
    }

    @Test
    fun `platform owner stays platform scoped when active organization context is present`()
    {
        val context = makeTokenContext().apply { activeOrganizationId = UUID.randomUUID() }
        val repo = mock<AuditOutboxRepository>()
        whenever(repo.findByIdempotencyKey(any())).thenReturn(null)
        whenever(repo.insert(any())).thenAnswer { invocation ->
            invocation.getArgument<AuditOutboxEntry>(0).also { it.id = UUID.randomUUID() }
        }
        val recorder = AuditRecorder(repo, context, makeResolver(AuditFailurePolicy.DEGRADED))

        recorder.record(validDraft().copy(owner = AuditOwnerScope.Platform))

        val entry = org.mockito.kotlin.argumentCaptor<AuditOutboxEntry>()
        verify(repo).insert(entry.capture())
        assertEquals(null, entry.firstValue.organizationId)
    }

    @Test
    fun `personal owner is captured without filing under platform or organization scope`()
    {
        val ownerUserId = UUID.randomUUID()
        val repo = mock<AuditOutboxRepository>()
        whenever(repo.findByIdempotencyKey(any())).thenReturn(null)
        whenever(repo.insert(any())).thenAnswer { invocation ->
            invocation.getArgument<AuditOutboxEntry>(0).also { it.id = UUID.randomUUID() }
        }
        val recorder = AuditRecorder(repo, makeTokenContext(), makeResolver(AuditFailurePolicy.DEGRADED))

        recorder.record(validDraft().copy(owner = AuditOwnerScope.Personal(ownerUserId)))

        val entry = org.mockito.kotlin.argumentCaptor<AuditOutboxEntry>()
        verify(repo).insert(entry.capture())
        assertEquals("USER", entry.firstValue.ownerType)
        assertEquals(ownerUserId, entry.firstValue.ownerId)
        assertEquals(null, entry.firstValue.organizationId)
    }

    @Test
    fun `unknown event type throws AuditDraftInvalidException and never inserts`()
    {
        val repo = mock<AuditOutboxRepository>()
        val recorder = AuditRecorder(repo, makeTokenContext(), makeResolver(AuditFailurePolicy.FAIL_CLOSED))

        val draft = AuditEventDraft(
            owner = AuditOwnerScope.Platform,
            eventTypeKey = "does.not.exist",
            outcome = AuditOutcome.SUCCESS,
        )

        assertThrows<AuditDraftInvalidException> { recorder.record(draft) }
        verify(repo, never()).insert(any())
    }

    @Test
    fun `retried capture with same idempotency key does not insert a duplicate row`()
    {
        val existingId = UUID.randomUUID()
        val existingEventId = UUID.randomUUID()
        val existing = AuditOutboxEntry().apply { id = existingId; eventId = existingEventId }

        val repo = mock<AuditOutboxRepository>()
        whenever(repo.findByIdempotencyKey(eq("exchange.rescind:abc"))).thenReturn(existing)

        val recorder = AuditRecorder(repo, makeTokenContext(), makeResolver(AuditFailurePolicy.DEGRADED))

        val result = recorder.record(validDraft(idempotencyKey = "exchange.rescind:abc"))

        assertTrue(result is AuditCaptureResult.Captured)
        assertEquals(existingId, (result as AuditCaptureResult.Captured).outboxId)
        verify(repo, never()).insert(any())
    }

    @Test
    fun `FAIL_CLOSED policy propagates the capture failure`()
    {
        val repo = mock<AuditOutboxRepository>()
        whenever(repo.findByIdempotencyKey(any())).thenReturn(null)
        whenever(repo.insert(any())).thenThrow(RuntimeException("db unavailable"))

        val recorder = AuditRecorder(repo, makeTokenContext(), makeResolver(AuditFailurePolicy.FAIL_CLOSED))

        assertThrows<AuditCaptureFailedException> { recorder.record(validDraft()) }
    }

    @Test
    fun `DEGRADED policy swallows the capture failure and reports Degraded`()
    {
        val repo = mock<AuditOutboxRepository>()
        whenever(repo.findByIdempotencyKey(any())).thenReturn(null)
        whenever(repo.insert(any())).thenThrow(RuntimeException("db unavailable"))

        val recorder = AuditRecorder(repo, makeTokenContext(), makeResolver(AuditFailurePolicy.DEGRADED))

        val result = recorder.record(validDraft())

        assertTrue(result is AuditCaptureResult.Degraded)
    }
}
