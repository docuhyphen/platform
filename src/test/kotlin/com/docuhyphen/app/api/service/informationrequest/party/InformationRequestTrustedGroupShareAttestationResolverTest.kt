package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientAttestationService
import jakarta.inject.Provider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestTrustedGroupShareAttestationResolverTest {
    private val partyRepository = mock<InformationRequestPartyRepository>()
    private val attestationService = mock<ExchangeRecipientAttestationService>()
    private val resolver = InformationRequestTrustedGroupShareAttestationResolver(
        partyRepository,
        Provider { attestationService },
    )

    @Test
    fun `a request party Share is attested by the trusted Exchange recipient its party is bound to`() {
        val recipientId = UUID.randomUUID()
        val share = Share().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST
        }
        val attestation = ExchangeRecipientAttestation()
        whenever(partyRepository.findByShareId(share.id)).thenReturn(InformationRequestParty().apply {
            exchangeRecipientId = recipientId
        })
        whenever(attestationService.findForRecipient(recipientId)).thenReturn(attestation)

        assertEquals(attestation, resolver.attestationFor(share))
    }

    @Test
    fun `a Share on another kind of resource or an unbound party has no request attestation`() {
        val exchangeShare = Share().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.EXCHANGE
        }
        val unbound = Share().apply {
            id = UUID.randomUUID()
            resourceType = ResourceType.INFORMATION_REQUEST
        }
        whenever(partyRepository.findByShareId(unbound.id)).thenReturn(InformationRequestParty())

        assertNull(resolver.attestationFor(exchangeShare))
        assertNull(resolver.attestationFor(unbound))
        verify(partyRepository, never()).findByShareId(exchangeShare.id)
        verify(attestationService, never()).findForRecipient(any())
    }

    @Test
    fun `only active group parties bound to the recipient are reconciled`() {
        val recipientId = UUID.randomUUID()
        val groupShareId = UUID.randomUUID()
        whenever(partyRepository.findActiveForExchangeRecipient(recipientId)).thenReturn(
            listOf(
                InformationRequestParty().apply {
                    principalKind = PrincipalKind.PRINCIPAL_GROUP
                    shareId = groupShareId
                },
                InformationRequestParty().apply {
                    principalKind = PrincipalKind.USER
                    shareId = UUID.randomUUID()
                },
            )
        )

        assertEquals(listOf(groupShareId), resolver.activeGroupShareIdsFor(recipientId))
    }
}
