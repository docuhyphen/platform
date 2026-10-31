package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.GroupShareExpansionPolicy
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import jakarta.inject.Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class ShareServiceRetainedOwnerReadTest {
    private val shareRepository = mock<ShareRepository>()
    private val service = ShareService(
        shareRepository = shareRepository,
        groupMemberRepository = mock<PrincipalGroupMemberRepository>(),
        auditRecorder = mock<AuditRecorder>(),
        resourceAuthorizationContextRegistry = mock<ResourceAuthorizationContextRegistry>(),
        groupShareExpansionPolicy = GroupShareExpansionPolicy.UNRESTRICTED,
        exchangeRecipientServiceProvider = mock<Provider<ExchangeRecipientService>>(),
    )
    private val exchangeId = UUID.randomUUID()
    private val requestId = UUID.randomUUID()

    @Test
    fun `each active Exchange owner keeps a read-only record owner Share on the request`() {
        val owner = exchangeShare(PrincipalKind.USER, ExchangeShareRoleName.OWNER)
        val viewer = exchangeShare(PrincipalKind.USER, ExchangeShareRoleName.VIEWER)
        val retainedAlready = exchangeShare(PrincipalKind.USER, ExchangeShareRoleName.OWNER)
        whenever(shareRepository.findActiveByResource(ResourceType.EXCHANGE, exchangeId)).thenReturn(
            listOf(
                owner,
                viewer,
                retainedAlready
            )
        )
        whenever(
            shareRepository.findActiveForPrincipalOnResource(
                PrincipalKind.USER,
                retainedAlready.principalId,
                ResourceType.INFORMATION_REQUEST,
                requestId
            ),
        ).thenReturn(listOf(Share().apply {
            resourceType = ResourceType.INFORMATION_REQUEST
            resourceId = requestId
            principalKind = PrincipalKind.USER
            principalId = retainedAlready.principalId
            roleName = InformationRequestShareRoleKey.RECORD_OWNER.name
            status = ShareStatus.ACTIVE
        }))
        whenever(shareRepository.save(any())).thenAnswer { it.getArgument(0) }

        service.retainInformationRequestOwnerReads(exchangeId, requestId)

        val saved = argumentCaptor<Share>()
        verify(shareRepository).save(saved.capture())
        assertEquals(ResourceType.INFORMATION_REQUEST, saved.firstValue.resourceType)
        assertEquals(requestId, saved.firstValue.resourceId)
        assertEquals(owner.principalId, saved.firstValue.principalId)
        assertEquals(InformationRequestShareRoleKey.RECORD_OWNER.name, saved.firstValue.roleName)
        verify(shareRepository, never()).findActiveForPrincipalOnResource(
            PrincipalKind.USER,
            viewer.principalId,
            ResourceType.INFORMATION_REQUEST,
            requestId
        )
    }

    private fun exchangeShare(kind: PrincipalKind, role: ExchangeShareRoleName) = Share().apply {
        id = UUID.randomUUID()
        resourceType = ResourceType.EXCHANGE
        resourceId = exchangeId
        principalKind = kind
        principalId = UUID.randomUUID()
        roleName = role.name
        source = ShareSource.DIRECT
        status = ShareStatus.ACTIVE
    }
}
