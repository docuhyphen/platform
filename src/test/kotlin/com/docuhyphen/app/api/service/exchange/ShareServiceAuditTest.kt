package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.service.audit.AuditCaptureFailedException
import com.docuhyphen.app.api.service.audit.AuditCaptureResult
import com.docuhyphen.app.api.service.audit.AuditEventDraft
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

/**
 * Phase 3 gate (AUDIT-ARCHITECTURE-IMPLEMENTATION.md, "... and a Share revoke each produce
 * exactly one ledger event with correct actor kind"): [ShareService.revoke] must call
 * [AuditRecorder.record] exactly once with SHARE_REVOKE/EXCHANGE/HUMAN (when an acting app user
 * id is known), targeting the resource being shared rather than the Share row itself, and must
 * never let an [AuditRecorder] failure break the actual revoke.
 */
class ShareServiceAuditTest
{
    private val exchangeId = UUID.randomUUID()
    private val shareId = UUID.randomUUID()
    private val sharePrincipalId = UUID.randomUUID()
    private val revokedByAppUserId = UUID.randomUUID()

    private fun share() = Share().apply {
        id = shareId
        resourceType = ResourceType.EXCHANGE
        resourceId = exchangeId
        principalKind = PrincipalKind.USER
        principalId = sharePrincipalId
        roleName = ExchangeShareRoleName.VIEWER
        source = ShareSource.DIRECT
        status = ShareStatus.ACTIVE
    }

    private fun service(
        auditRecorder: AuditRecorder,
        shareRepository: ShareRepository,
    ): ShareService = ShareService(
        shareRepository = shareRepository,
        groupMemberRepository = mock<PrincipalGroupMemberRepository>(),
        auditRecorder = auditRecorder,
    )

    @Test
    fun `revoke records exactly one SHARE_REVOKE event targeting the shared resource with HUMAN actor kind`()
    {
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        val shareRepository = mock<ShareRepository>()
        whenever(shareRepository.findById(shareId)).thenReturn(share())
        whenever(shareRepository.findBySourceShareId(shareId)).thenReturn(emptyList())
        whenever(shareRepository.update(any())).thenAnswer { it.getArgument(0) }

        val service = service(auditRecorder, shareRepository)

        service.revoke(shareId, revokedByAppUserId)

        val captor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(captor.capture())
        assertEquals(AuditEventType.SHARE_REVOKE.key, captor.firstValue.eventTypeKey)
        assertEquals(ResourceType.EXCHANGE.name, captor.firstValue.targetType)
        assertEquals(exchangeId.toString(), captor.firstValue.targetId)
        assertEquals(AuditActorKind.HUMAN, captor.firstValue.actorKind)
        assertEquals(revokedByAppUserId, captor.firstValue.actorId)
    }

    @Test
    fun `revoke on an already-revoked share does not record a duplicate event`()
    {
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        val alreadyRevoked = share().apply { status = ShareStatus.REVOKED }
        val shareRepository = mock<ShareRepository>()
        whenever(shareRepository.findById(shareId)).thenReturn(alreadyRevoked)
        whenever(shareRepository.findBySourceShareId(shareId)).thenReturn(emptyList())

        val service = service(auditRecorder, shareRepository)

        service.revoke(shareId, revokedByAppUserId)

        verify(auditRecorder, org.mockito.kotlin.never()).record(any())
    }

    @Test
    fun `revoke does not propagate AuditRecorder failures`()
    {
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenThrow(AuditCaptureFailedException("boom", null))

        val shareRepository = mock<ShareRepository>()
        whenever(shareRepository.findById(shareId)).thenReturn(share())
        whenever(shareRepository.findBySourceShareId(shareId)).thenReturn(emptyList())
        whenever(shareRepository.update(any())).thenAnswer { it.getArgument(0) }

        val service = service(auditRecorder, shareRepository)

        // Must not throw despite the AuditRecorder failure.
        service.revoke(shareId, revokedByAppUserId)

        verify(auditRecorder).record(any())
    }
}
