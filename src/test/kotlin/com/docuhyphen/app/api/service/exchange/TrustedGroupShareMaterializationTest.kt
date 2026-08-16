package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestation
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import jakarta.inject.Provider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

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
        mock<ExchangeAuthorizationContextProvider>(),
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

    private fun groupShare(status: ShareStatus, groupId: UUID = UUID.randomUUID()): Share = Share().apply {
        resourceType = ResourceType.EXCHANGE
        resourceId = UUID.randomUUID()
        principalKind = PrincipalKind.PRINCIPAL_GROUP
        principalId = groupId
        roleName = ExchangeShareRoleName.VIEWER
        source = ShareSource.DIRECT
        this.status = status
    }
}
