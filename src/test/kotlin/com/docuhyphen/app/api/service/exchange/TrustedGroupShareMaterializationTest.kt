package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import jakarta.inject.Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.util.*

class TrustedGroupShareMaterializationTest
{
    private val repository = mock<ShareRepository>()
    private val memberRepository = mock<PrincipalGroupMemberRepository>()
    private val attestationService = mock<ExchangeRecipientAttestationService>()
    private val validationService = mock<TrustedRecipientValidationService>()
    private val service = ShareService(
        repository,
        memberRepository,
        mock<AuditRecorder>(),
        mock<ResourceAuthorizationContextRegistry>(),
        TrustedGroupShareExpansionPolicy(attestationService, validationService),
        mock<Provider<ExchangeRecipientService>>(),
    )

    @Test
    fun `pending trusted group Share cannot activate after trust becomes ineligible`()
    {
        val share = groupShare(ShareStatus.PENDING_APPROVAL)
        val attestation = ExchangeRecipientAttestation()
        whenever(repository.findById(share.id)).thenReturn(share)
        whenever(attestationService.findForDirectShare(share.id)).thenReturn(attestation)
        whenever(validationService.isGroupAttestationCurrentlyEligible(attestation)).thenReturn(false)

        assertThrows<IllegalArgumentException> { service.activate(share.id) }

        verify(repository, never()).update(any())
        verify(memberRepository, never()).findActiveMembers(any())
    }

    @Test
    fun `a request-scoped trusted group Share holds back a new member while its party's trust is ineligible`() {
        val groupId = UUID.randomUUID()
        val share = groupShare(ShareStatus.ACTIVE, groupId).apply { resourceType = ResourceType.INFORMATION_REQUEST }
        val attestation = ExchangeRecipientAttestation()
        whenever(repository.findActiveForPrincipal(PrincipalKind.PRINCIPAL_GROUP, groupId)).thenReturn(listOf(share))
        whenever(attestationService.findForScopedGroupShare(share)).thenReturn(attestation)
        whenever(validationService.isGroupAttestationCurrentlyEligible(attestation)).thenReturn(false)

        service.synchronizeGroupMemberAccess(groupId, PrincipalKind.USER, UUID.randomUUID(), true)

        verify(repository, never()).findBySourceShareId(share.id)
    }

    @Test
    fun `a request-scoped trusted group Share expands to a new member while its party's trust is eligible`() {
        val groupId = UUID.randomUUID()
        val share = groupShare(ShareStatus.ACTIVE, groupId).apply { resourceType = ResourceType.INFORMATION_REQUEST }
        val attestation = ExchangeRecipientAttestation()
        whenever(repository.findActiveForPrincipal(PrincipalKind.PRINCIPAL_GROUP, groupId)).thenReturn(listOf(share))
        whenever(attestationService.findForScopedGroupShare(share)).thenReturn(attestation)
        whenever(validationService.isGroupAttestationCurrentlyEligible(attestation)).thenReturn(true)
        whenever(repository.findBySourceShareId(share.id)).thenReturn(emptyList())

        service.synchronizeGroupMemberAccess(groupId, PrincipalKind.USER, UUID.randomUUID(), true)

        verify(repository).save(any())
    }

    @Test
    fun `new trusted group member is not materialized while trust is suspended`()
    {
        val groupId = UUID.randomUUID()
        val share = groupShare(ShareStatus.ACTIVE, groupId)
        val attestation = ExchangeRecipientAttestation()
        whenever(repository.findActiveForPrincipal(PrincipalKind.PRINCIPAL_GROUP, groupId)).thenReturn(listOf(share))
        whenever(attestationService.findForDirectShare(share.id)).thenReturn(attestation)
        whenever(validationService.isGroupAttestationCurrentlyEligible(attestation)).thenReturn(false)

        service.synchronizeGroupMemberAccess(groupId, PrincipalKind.USER, UUID.randomUUID(), true)

        verify(repository, never()).findBySourceShareId(share.id)
    }

    @Test
    fun `request party group Share materializes inherited request role Shares for active members`()
    {
        val requestId = UUID.randomUUID()
        val groupId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val participantId = UUID.randomUUID()
        whenever(
            repository.findActiveForPrincipalOnResource(
                PrincipalKind.PRINCIPAL_GROUP,
                groupId,
                ResourceType.INFORMATION_REQUEST,
                requestId,
            ),
        ).thenReturn(emptyList())
        whenever(
            repository.findDirectForPrincipalOnResource(
                PrincipalKind.PRINCIPAL_GROUP,
                groupId,
                ResourceType.INFORMATION_REQUEST,
                requestId,
            ),
        ).thenReturn(emptyList())
        whenever(memberRepository.findActiveMembers(groupId)).thenReturn(
            listOf(
                groupMember(groupId, PrincipalKind.USER, userId),
                groupMember(groupId, PrincipalKind.PARTICIPANT, participantId),
            ),
        )
        whenever(repository.findBySourceShareId(any())).thenReturn(emptyList())
        whenever(repository.save(any())).thenAnswer { it.getArgument(0) }

        service.grantRoleKeyWithPrincipalProvenance(
            resourceType = ResourceType.INFORMATION_REQUEST,
            resourceId = requestId,
            principalKind = PrincipalKind.PRINCIPAL_GROUP,
            principalId = groupId,
            roleName = InformationRequestShareRoleKey.CONTRIBUTOR.name,
            resourceLabel = "Information Request",
        )

        val savedShares = argumentCaptor<Share>()
        verify(repository, times(3)).save(savedShares.capture())
        val parentShare = savedShares.allValues[0]
        val inheritedShares = savedShares.allValues.drop(1)
        assertEquals(ResourceType.INFORMATION_REQUEST, parentShare.resourceType)
        assertEquals(InformationRequestShareRoleKey.CONTRIBUTOR.name, parentShare.roleName)
        assertEquals(setOf(userId, participantId), inheritedShares.map { it.principalId }.toSet())
        assertEquals(setOf(ShareSource.INHERITED_FROM_GROUP), inheritedShares.map { it.source }.toSet())
        assertEquals(setOf(parentShare.id), inheritedShares.map { it.sourceShareId }.toSet())
        assertEquals(
            setOf(InformationRequestShareRoleKey.CONTRIBUTOR.name),
            inheritedShares.map { it.roleName }.toSet()
        )
    }

    @Test
    fun `a trusted group Share holds back exactly the members it never materialized while its trust is ineligible`() {
        val share = groupShare(ShareStatus.ACTIVE).apply { resourceType = ResourceType.INFORMATION_REQUEST }
        val attestation = ExchangeRecipientAttestation()
        val materialized = PrincipalRef.user(UUID.randomUUID())
        val revoked = PrincipalRef.user(UUID.randomUUID())
        val joined = PrincipalRef.participant(UUID.randomUUID())
        whenever(repository.findById(share.id)).thenReturn(share)
        whenever(repository.findBySourceShareId(share.id)).thenReturn(
            listOf(
                inheritedShare(share, materialized, ShareStatus.ACTIVE),
                inheritedShare(share, revoked, ShareStatus.REVOKED),
            ),
        )
        whenever(attestationService.findForScopedGroupShare(share)).thenReturn(attestation)
        whenever(validationService.isGroupAttestationCurrentlyEligible(attestation)).thenReturn(false)

        assertEquals(
            setOf(revoked, joined),
            service.membersHeldBackFrom(share.id, listOf(materialized, revoked, joined)),
        )
    }

    @Test
    fun `a trusted group Share holds back no member while its trust is eligible`() {
        val share = groupShare(ShareStatus.ACTIVE)
        val attestation = ExchangeRecipientAttestation()
        whenever(repository.findById(share.id)).thenReturn(share)
        whenever(repository.findBySourceShareId(share.id)).thenReturn(emptyList())
        whenever(attestationService.findForDirectShare(share.id)).thenReturn(attestation)
        whenever(validationService.isGroupAttestationCurrentlyEligible(attestation)).thenReturn(true)

        assertEquals(
            emptySet<PrincipalRef>(),
            service.membersHeldBackFrom(share.id, listOf(PrincipalRef.user(UUID.randomUUID())))
        )
    }

    @Test
    fun `a group Share with no trusted attestation holds back no member`() {
        val share = groupShare(ShareStatus.ACTIVE)
        whenever(repository.findById(share.id)).thenReturn(share)
        whenever(repository.findBySourceShareId(share.id)).thenReturn(emptyList())

        assertEquals(
            emptySet<PrincipalRef>(),
            service.membersHeldBackFrom(share.id, listOf(PrincipalRef.user(UUID.randomUUID())))
        )
    }

    @Test
    fun `a Share that does not belong to a group holds back no member`() {
        val share = groupShare(ShareStatus.ACTIVE).apply { principalKind = PrincipalKind.USER }
        whenever(repository.findById(share.id)).thenReturn(share)

        assertEquals(
            emptySet<PrincipalRef>(),
            service.membersHeldBackFrom(share.id, listOf(PrincipalRef.user(UUID.randomUUID())))
        )
        verify(attestationService, never()).findForDirectShare(any())
    }

    private fun inheritedShare(parent: Share, member: PrincipalRef, status: ShareStatus): Share = Share().apply {
        resourceType = parent.resourceType
        resourceId = parent.resourceId
        principalKind = member.kind
        principalId = member.id
        roleName = parent.roleName
        source = ShareSource.INHERITED_FROM_GROUP
        sourceShareId = parent.id
        this.status = status
    }

    private fun groupShare(status: ShareStatus, groupId: UUID = UUID.randomUUID()): Share = Share().apply {
        resourceType = ResourceType.EXCHANGE
        resourceId = UUID.randomUUID()
        principalKind = PrincipalKind.PRINCIPAL_GROUP
        principalId = groupId
        roleName = ExchangeShareRoleName.VIEWER.name
        source = ShareSource.DIRECT
        this.status = status
    }

    private fun groupMember(
        groupId: UUID,
        principalKind: PrincipalKind,
        principalId: UUID,
    ): PrincipalGroupMember =
        PrincipalGroupMember().apply {
            principalGroupId = groupId
            this.principalKind = principalKind
            this.principalId = principalId
        }
}
