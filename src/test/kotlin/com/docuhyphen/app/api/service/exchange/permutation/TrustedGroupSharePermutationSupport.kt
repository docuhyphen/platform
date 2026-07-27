package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.ExchangeRecipientAttestation
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroupMember
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.ResourceType
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.model.entity.ShareSource
import com.docuhyphen.app.api.model.entity.ShareStatus
import com.docuhyphen.app.api.repository.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.repository.ShareRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.exchange.ExchangeAuthorizationContextProvider
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientAttestationService
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import jakarta.inject.Provider
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.UUID

internal class TrustedGroupShareFixture(
    status: ShareStatus,
    private val eligible: Boolean,
    withAttestation: Boolean = true,
)
{
    val groupId: UUID = UUID.randomUUID()
    val userId: UUID = UUID.randomUUID()
    val parentShare: Share = Share().apply {
        resourceType = ResourceType.EXCHANGE
        resourceId = UUID.randomUUID()
        principalKind = PrincipalKind.PRINCIPAL_GROUP
        principalId = groupId
        roleName = ExchangeShareRoleName.VIEWER
        source = ShareSource.DIRECT
        this.status = status
    }
    val member: PrincipalGroupMember = PrincipalGroupMember().apply {
        principalGroupId = groupId
        principalKind = PrincipalKind.USER
        principalId = userId
        groupRole = PrincipalGroupRoleName.MEMBER
    }
    val repository: ShareRepository = mock()
    val memberRepository: PrincipalGroupMemberRepository = mock()
    val attestationService: ExchangeRecipientAttestationService = mock()
    val validationService: TrustedRecipientValidationService = mock()
    val service: ShareService = ShareService(
        repository,
        memberRepository,
        mock<AuditRecorder>(),
        mock<ExchangeAuthorizationContextProvider>(),
        attestationService,
        validationService,
        mock<Provider<ExchangeRecipientService>>(),
    )

    init
    {
        whenever(repository.findById(parentShare.id)).thenReturn(parentShare)
        whenever(repository.update(any())).thenAnswer { it.getArgument(0) }
        whenever(repository.save(any())).thenAnswer { it.getArgument(0) }
        whenever(repository.findBySourceShareId(parentShare.id)).thenReturn(emptyList())
        whenever(repository.findActiveForPrincipal(PrincipalKind.PRINCIPAL_GROUP, groupId))
            .thenReturn(listOf(parentShare))
        whenever(memberRepository.findActiveMembers(groupId)).thenReturn(listOf(member))
        if (withAttestation)
        {
            val attestation = mock<ExchangeRecipientAttestation>()
            whenever(attestationService.findForDirectShare(parentShare.id)).thenReturn(attestation)
            whenever(validationService.isGroupAttestationCurrentlyEligible(attestation)).thenReturn(eligible)
        }
    }

    fun inheritedShare(status: ShareStatus = ShareStatus.ACTIVE): Share = Share().apply {
        resourceType = parentShare.resourceType
        resourceId = parentShare.resourceId
        principalKind = PrincipalKind.USER
        principalId = userId
        roleName = parentShare.roleName
        source = ShareSource.INHERITED_FROM_GROUP
        sourceShareId = parentShare.id
        this.status = status
    }
}
