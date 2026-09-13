package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
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
        attestationService,
        validationService,
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
        verify(repository, org.mockito.kotlin.times(3)).save(savedShares.capture())
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
