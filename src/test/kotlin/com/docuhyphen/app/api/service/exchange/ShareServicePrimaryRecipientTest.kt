package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.exchange.ShareRepository
import com.docuhyphen.app.api.repository.organization.PrincipalGroupMemberRepository
import com.docuhyphen.app.api.service.audit.AuditRecorder
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.auth.authz.ResourceAuthorizationContextRegistry
import com.docuhyphen.app.api.service.organization.TrustedRecipientValidationService
import jakarta.inject.Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class ShareServicePrimaryRecipientTest
{
    private val shareRepository = mock<ShareRepository>()
    private val recipientService = mock<ExchangeRecipientService>()
    private val recipientServiceProvider = mock<Provider<ExchangeRecipientService>>()
    private val service = ShareService(
        shareRepository = shareRepository,
        groupMemberRepository = mock<PrincipalGroupMemberRepository>(),
        auditRecorder = mock<AuditRecorder>(),
        resourceAuthorizationContextRegistry = mock<ResourceAuthorizationContextRegistry>(),
        exchangeRecipientAttestationService = mock<ExchangeRecipientAttestationService>(),
        trustedRecipientValidationService = mock<TrustedRecipientValidationService>(),
        exchangeRecipientServiceProvider = recipientServiceProvider,
    )

    init
    {
        whenever(recipientServiceProvider.get()).thenReturn(recipientService)
    }

    @Test
    fun `primary user lookup follows the recipient binding instead of another direct Share`()
    {
        val exchangeId = UUID.randomUUID()
        val primaryShare = recipientShare(exchangeId, PrincipalKind.USER, ShareStatus.PENDING_APPROVAL)
        val participantShare = recipientShare(exchangeId, PrincipalKind.USER, ShareStatus.ACTIVE)
        whenever(recipientService.findPrimary(exchangeId)).thenReturn(primaryBinding(exchangeId, primaryShare.id))
        whenever(shareRepository.findById(primaryShare.id)).thenReturn(primaryShare)
        whenever(shareRepository.findAllByResource(ResourceType.EXCHANGE, exchangeId))
            .thenReturn(listOf(participantShare, primaryShare))

        assertEquals(primaryShare.principalId, service.primaryRecipientUserIdForDisplay(exchangeId))
        assertNull(service.primaryRecipientUserId(exchangeId))
    }

    @Test
    fun `primary group lookup follows the recipient binding`()
    {
        val exchangeId = UUID.randomUUID()
        val primaryShare = recipientShare(exchangeId, PrincipalKind.PRINCIPAL_GROUP, ShareStatus.ACTIVE)
        whenever(recipientService.findPrimary(exchangeId)).thenReturn(primaryBinding(exchangeId, primaryShare.id))
        whenever(shareRepository.findById(primaryShare.id)).thenReturn(primaryShare)

        assertEquals(primaryShare.principalId, service.primaryRecipientGroupIdForDisplay(exchangeId))
        assertEquals(primaryShare.principalId, service.primaryRecipientGroupId(exchangeId))
    }

    @Test
    fun `the primary recipient principal is the active primary Share's principal of either kind`()
    {
        val exchangeId = UUID.randomUUID()
        val groupExchangeId = UUID.randomUUID()
        val userShare = recipientShare(exchangeId, PrincipalKind.USER, ShareStatus.ACTIVE)
        val groupShare = recipientShare(groupExchangeId, PrincipalKind.PRINCIPAL_GROUP, ShareStatus.ACTIVE)
        whenever(recipientService.findPrimary(exchangeId)).thenReturn(primaryBinding(exchangeId, userShare.id))
        whenever(recipientService.findPrimary(groupExchangeId)).thenReturn(primaryBinding(groupExchangeId, groupShare.id))
        whenever(shareRepository.findById(userShare.id)).thenReturn(userShare)
        whenever(shareRepository.findById(groupShare.id)).thenReturn(groupShare)

        assertEquals(PrincipalRef.user(userShare.principalId), service.primaryRecipientPrincipal(exchangeId))
        assertEquals(PrincipalRef.group(groupShare.principalId), service.primaryRecipientPrincipal(groupExchangeId))
    }

    @Test
    fun `an inactive primary Share names no primary recipient principal`()
    {
        val exchangeId = UUID.randomUUID()
        val primaryShare = recipientShare(exchangeId, PrincipalKind.USER, ShareStatus.PENDING_APPROVAL)
        whenever(recipientService.findPrimary(exchangeId)).thenReturn(primaryBinding(exchangeId, primaryShare.id))
        whenever(shareRepository.findById(primaryShare.id)).thenReturn(primaryShare)

        assertNull(service.primaryRecipientPrincipal(exchangeId))
    }

    private fun primaryBinding(exchangeId: UUID, shareId: UUID): ExchangeRecipient = ExchangeRecipient().apply {
        this.exchangeId = exchangeId
        directShareId = shareId
        purpose = ExchangeRecipientPurpose.PRIMARY
    }

    private fun recipientShare(
        exchangeId: UUID,
        principalKind: PrincipalKind,
        status: ShareStatus,
    ): Share = Share().apply {
        resourceId = exchangeId
        this.principalKind = principalKind
        principalId = UUID.randomUUID()
        this.status = status
    }
}
