package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.exception.OrganizationTrustException
import com.docuhyphen.app.api.model.entity.InformationRequest
import com.docuhyphen.app.api.model.entity.InformationRequestParty
import com.docuhyphen.app.api.model.entity.InformationRequestShareRoleKey
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.repository.informationrequest.party.InformationRequestPartyRepository
import com.docuhyphen.app.api.service.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.informationrequest.InformationRequestErrorCatalog
import com.docuhyphen.app.api.service.informationrequest.lifecycle.InformationRequestLifecycleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import java.util.*

class InformationRequestTrustedRecipientEligibilityServiceTest {
    private val exchangeRecipients = mock<ExchangeRecipientService>()
    private val parties = mock<InformationRequestPartyRepository>()
    private val service = InformationRequestTrustedRecipientEligibilityService(exchangeRecipients, parties)
    private val request = InformationRequest().apply {
        id = UUID.randomUUID()
        exchangeId = UUID.randomUUID()
    }

    @Test
    fun `a request cannot be issued while a party's trusted recipient relationship is suspended`() {
        val party = trustedParty()
        whenever(parties.findActiveForRequest(request.id)).thenReturn(listOf(party))
        whenever(exchangeRecipients.trustSuspended(party.exchangeRecipientId!!)).thenReturn(true)

        val refused =
            assertThrows(InformationRequestLifecycleException::class.java) { service.requireIssuable(request) }

        assertEquals(InformationRequestErrorCatalog.TRUST_SUSPENDED, refused.reasonCode)
    }

    @Test
    fun `a request cannot be issued while a party's trusted recipient can no longer be assigned`() {
        val party = trustedParty()
        val holder = PrincipalRef(party.principalKind!!, party.principalId!!)
        whenever(parties.findActiveForRequest(request.id)).thenReturn(listOf(party))
        whenever(
            exchangeRecipients.requireAssignablePartyRecipient(
                party.exchangeRecipientId!!,
                request.exchangeId,
                holder
            )
        )
            .thenThrow(OrganizationTrustException("The trusted group is no longer published"))

        val refused =
            assertThrows(InformationRequestLifecycleException::class.java) { service.requireIssuable(request) }

        assertEquals(InformationRequestErrorCatalog.TRUSTED_RECIPIENT_UNAVAILABLE, refused.reasonCode)
    }

    @Test
    fun `parties without a trusted recipient are not checked`() {
        val untrusted = trustedParty().apply { exchangeRecipientId = null }
        whenever(parties.findActiveForRequest(request.id)).thenReturn(listOf(untrusted))

        service.requireIssuable(request)

        verify(exchangeRecipients, never()).trustSuspended(any())
        verify(exchangeRecipients, never()).requireAssignablePartyRecipient(any(), any(), any())
    }

    @Test
    fun `a single assignment is checked the same way`() {
        val recipientId = UUID.randomUUID()
        whenever(exchangeRecipients.trustSuspended(recipientId)).thenReturn(true)

        val refused = assertThrows(InformationRequestLifecycleException::class.java) {
            service.requireAssignable(recipientId, request.exchangeId, PrincipalRef.user(UUID.randomUUID()))
        }

        assertEquals(InformationRequestErrorCatalog.TRUST_SUSPENDED, refused.reasonCode)
    }

    private fun trustedParty() = InformationRequestParty().apply {
        id = UUID.randomUUID()
        informationRequestId = request.id
        roleKey = InformationRequestShareRoleKey.CONTRIBUTOR
        principalKind = PrincipalKind.USER
        principalId = UUID.randomUUID()
        exchangeRecipientId = UUID.randomUUID()
        active = true
    }
}
