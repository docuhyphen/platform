package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.service.audit.*
import com.docuhyphen.app.api.service.audit.catalog.AuditActorKind
import com.docuhyphen.app.api.service.audit.catalog.AuditEventType
import com.docuhyphen.app.api.service.auth.authz.*
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import jakarta.inject.Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

/**
 * Verifies that [ShareService.revoke] calls
 * [AuditRecorder.record] exactly once with SHARE_REVOKE/EXCHANGE/HUMAN (when an acting app user
 * id is known), targeting the resource being shared rather than the Share row itself, and must
 * never let an [AuditRecorder] failure break the actual revoke.
 */
class ShareServiceAuditTest
{
    private val exchangeId = UUID.randomUUID()
    private val shareId = UUID.randomUUID()
    private val sharePrincipalId = UUID.randomUUID()
    private val revokerId = UUID.randomUUID()

    private fun share() = Share().apply {
        id = shareId
        resourceType = ResourceType.EXCHANGE
        resourceId = exchangeId
        principalKind = PrincipalKind.USER
        principalId = sharePrincipalId
        roleName = ExchangeShareRoleName.VIEWER.name
        source = ShareSource.DIRECT
        status = ShareStatus.ACTIVE
    }

    private fun service(
        auditRecorder: AuditRecorder,
        shareRepository: ShareRepository,
        contextRegistry: ResourceAuthorizationContextRegistry = mock(),
    ): ShareService = ShareService(
        shareRepository = shareRepository,
        groupMemberRepository = mock<PrincipalGroupMemberRepository>(),
        auditRecorder = auditRecorder,
        resourceAuthorizationContextRegistry = contextRegistry,
        exchangeRecipientAttestationService = mock<ExchangeRecipientAttestationService>(),
        trustedRecipientValidationService = mock<TrustedRecipientValidationService>(),
        exchangeRecipientServiceProvider = mock<Provider<ExchangeRecipientService>>(),
    )

    @Test
    fun `revoke records the owning Exchange organization`()
    {
        val organizationId = UUID.randomUUID()
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))
        val shareRepository = mock<ShareRepository>()
        whenever(shareRepository.findById(shareId)).thenReturn(share())
        whenever(shareRepository.findBySourceShareId(shareId)).thenReturn(emptyList())
        whenever(shareRepository.update(any())).thenAnswer { it.getArgument(0) }
        val contextRegistry = mock<ResourceAuthorizationContextRegistry>()
        whenever(contextRegistry.resolve(ResourceRef.exchange(exchangeId))).thenReturn(
            ResourceAuthorizationContext(OwnerContext.Organization(organizationId))
        )

        service(auditRecorder, shareRepository, contextRegistry).revoke(shareId, PrincipalRef.user(revokerId))

        val captor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(captor.capture())
        assertEquals(AuditOwnerScope.Organization(organizationId), captor.firstValue.owner)
    }

    @Test
    fun `revoke records a non Exchange Share under its resolved organization owner`()
    {
        val organizationId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val groupShare = share().apply {
            resourceType = ResourceType.PRINCIPAL_GROUP
            resourceId = groupId
        }
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(
            AuditCaptureResult.Captured(
                UUID.randomUUID(),
                UUID.randomUUID()
            )
        )
        val shareRepository = mock<ShareRepository>()
        whenever(shareRepository.findById(shareId)).thenReturn(groupShare)
        whenever(shareRepository.findBySourceShareId(shareId)).thenReturn(emptyList())
        whenever(shareRepository.update(any())).thenAnswer { it.getArgument(0) }
        val contextRegistry = mock<ResourceAuthorizationContextRegistry>()
        whenever(contextRegistry.resolve(ResourceRef.group(groupId))).thenReturn(
            ResourceAuthorizationContext(OwnerContext.Organization(organizationId))
        )

        service(auditRecorder, shareRepository, contextRegistry).revoke(shareId, PrincipalRef.user(revokerId))

        val captor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(captor.capture())
        assertEquals(AuditOwnerScope.Organization(organizationId), captor.firstValue.owner)
    }

    @Test
    fun `grant records canonical non user provenance without legacy app user drift`()
    {
        val applicationId = UUID.randomUUID()
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(
            AuditCaptureResult.Captured(
                UUID.randomUUID(),
                UUID.randomUUID()
            )
        )
        val shareRepository = mock<ShareRepository>()
        whenever(
            shareRepository.findActiveForPrincipalOnResource(
                PrincipalKind.USER,
                sharePrincipalId,
                ResourceType.EXCHANGE,
                exchangeId,
            ),
        ).thenReturn(emptyList())
        whenever(
            shareRepository.findDirectForPrincipalOnResource(
                PrincipalKind.USER,
                sharePrincipalId,
                ResourceType.EXCHANGE,
                exchangeId,
            ),
        ).thenReturn(emptyList())
        whenever(shareRepository.save(any())).thenAnswer { it.getArgument(0) }
        val contextRegistry = mock<ResourceAuthorizationContextRegistry>()
        whenever(contextRegistry.resolve(ResourceRef.exchange(exchangeId))).thenReturn(
            ResourceAuthorizationContext(OwnerContext.Organization(UUID.randomUUID()))
        )

        val granted = service(auditRecorder, shareRepository, contextRegistry).grant(
            resourceType = ResourceType.EXCHANGE,
            resourceId = exchangeId,
            principalKind = PrincipalKind.USER,
            principalId = sharePrincipalId,
            roleName = ExchangeShareRoleName.VIEWER,
            grantedBy = PrincipalRef.application(applicationId),
        )

        assertEquals(PrincipalKind.APPLICATION, granted.grantedByPrincipalKind)
        assertEquals(applicationId, granted.grantedByPrincipalId)
    }

    @Test
    fun `revoke records exactly one SHARE_REVOKE event targeting the shared resource with HUMAN actor kind`()
    {
        val auditRecorder = mock<AuditRecorder>()
        whenever(auditRecorder.record(any())).thenReturn(AuditCaptureResult.Captured(UUID.randomUUID(), UUID.randomUUID()))

        val targetShare = share()
        val shareRepository = mock<ShareRepository>()
        whenever(shareRepository.findById(shareId)).thenReturn(targetShare)
        whenever(shareRepository.findBySourceShareId(shareId)).thenReturn(emptyList())
        whenever(shareRepository.update(any())).thenAnswer { it.getArgument(0) }

        val service = service(auditRecorder, shareRepository)

        service.revoke(shareId, PrincipalRef.user(revokerId))

        val captor = argumentCaptor<AuditEventDraft>()
        verify(auditRecorder).record(captor.capture())
        assertEquals(AuditEventType.SHARE_REVOKE.key, captor.firstValue.eventTypeKey)
        assertEquals(ResourceType.EXCHANGE.name, captor.firstValue.targetType)
        assertEquals(exchangeId.toString(), captor.firstValue.targetId)
        assertEquals(AuditActorKind.HUMAN, captor.firstValue.actorKind)
        assertEquals(revokerId, captor.firstValue.actorId)
        assertEquals(PrincipalKind.USER, targetShare.revokedByPrincipalKind)
        assertEquals(revokerId, targetShare.revokedByPrincipalId)
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

        service.revoke(shareId, PrincipalRef.user(revokerId))

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
        service.revoke(shareId, PrincipalRef.user(revokerId))

        verify(auditRecorder).record(any())
    }
}
