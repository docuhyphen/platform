package com.docuhyphen.app.api.service.informationrequest.party

import com.docuhyphen.app.api.model.entity.*
import com.docuhyphen.app.api.model.auth.authz.PrincipalRef
import com.docuhyphen.app.api.service.exchange.ExchangeRecipientService
import com.docuhyphen.app.api.service.exchange.ShareService
import com.docuhyphen.app.api.service.user.AppUserService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.*

class InformationRequestRecipientAddressResolverTest {
    private val shareService = mock<ShareService>()
    private val exchangeRecipientService = mock<ExchangeRecipientService>()
    private val appUserService = mock<AppUserService>()
    private val resolver =
        InformationRequestRecipientAddressResolver(shareService, exchangeRecipientService, appUserService)
    private val exchangeId = UUID.randomUUID()

    private fun user(email: String) = AppUser().apply {
        id = UUID.randomUUID()
        this.email = email
    }.also { whenever(appUserService.getById(it.id)).thenReturn(it) }

    private fun share(user: AppUser, status: ShareStatus = ShareStatus.ACTIVE) = Share().apply {
        id = UUID.randomUUID()
        resourceType = ResourceType.EXCHANGE
        resourceId = exchangeId
        principalKind = PrincipalKind.USER
        principalId = user.id
        roleName = ExchangeShareRoleName.VIEWER.name
        this.status = status
    }.also { whenever(shareService.getById(it.id)).thenReturn(it) }

    private fun recipient(share: Share, selection: ExchangeRecipientSelectionType, accepted: Boolean = true) =
        ExchangeRecipient().apply {
            id = UUID.randomUUID()
            this.exchangeId = this@InformationRequestRecipientAddressResolverTest.exchangeId
            directShareId = share.id
            selectionType = selection
            acceptanceStatus =
                if (accepted) ExchangeRecipientAcceptanceStatus.ACCEPTED else ExchangeRecipientAcceptanceStatus.PENDING
        }

    @Test
    fun `an address resolves to the account of a current recipient of the Exchange only`() {
        val recipient = user("Recipient@Process.test")
        val outsider = user("outsider@process.test")
        whenever(
            shareService.activeUserPrincipalIds(
                ResourceType.EXCHANGE,
                exchangeId
            )
        ).thenReturn(listOf(recipient.id))

        assertEquals(
            PrincipalRef.user(recipient.id),
            resolver.recipientAccountOf(exchangeId, " recipient@process.test ")
        )
        assertNull(resolver.recipientAccountOf(exchangeId, outsider.email))
        assertNull(resolver.recipientAccountOf(exchangeId, "nobody@process.test"))
    }

    @Test
    fun `only an accepted trusted person recipient of the Exchange counts as a trusted address`() {
        val trusted = user("trusted@partner.test")
        val grouped = user("grouped@partner.test")
        val pending = user("pending@partner.test")
        val recipients = listOf(
            recipient(share(trusted), ExchangeRecipientSelectionType.TRUSTED_PERSON),
            recipient(share(grouped), ExchangeRecipientSelectionType.TRUSTED_GROUP),
            recipient(share(pending), ExchangeRecipientSelectionType.TRUSTED_PERSON, accepted = false),
        )
        whenever(exchangeRecipientService.findByExchangeId(exchangeId)).thenReturn(recipients)

        assertTrue(resolver.isTrustedPersonAddress(exchangeId, " TRUSTED@partner.test "))
        assertFalse(resolver.isTrustedPersonAddress(exchangeId, grouped.email))
        assertFalse(resolver.isTrustedPersonAddress(exchangeId, pending.email))
        assertFalse(resolver.isTrustedPersonAddress(exchangeId, "member@partner.test"))
    }

    @Test
    fun `a trusted person whose Exchange access ended is not a trusted address`() {
        val former = user("former@partner.test")
        val recipients =
            listOf(recipient(share(former, ShareStatus.REVOKED), ExchangeRecipientSelectionType.TRUSTED_PERSON))
        whenever(exchangeRecipientService.findByExchangeId(exchangeId)).thenReturn(recipients)

        assertFalse(resolver.isTrustedPersonAddress(exchangeId, former.email))
    }
}
