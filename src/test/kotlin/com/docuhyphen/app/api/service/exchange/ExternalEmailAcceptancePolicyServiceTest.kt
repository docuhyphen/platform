package com.docuhyphen.app.api.service.exchange

import com.docuhyphen.app.api.exception.ExchangeRecipientEligibilityException
import com.docuhyphen.app.api.model.entity.AppUser
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.PrincipalKind
import com.docuhyphen.app.api.model.entity.Share
import com.docuhyphen.app.api.service.AppUserService
import com.docuhyphen.app.api.service.organization.OrganizationExchangePolicyService
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class ExternalEmailAcceptancePolicyServiceTest
{
    private val appUserService = mock<AppUserService>()
    private val policyService = mock<OrganizationExchangePolicyService>()
    private val service = ExternalEmailAcceptancePolicyService(appUserService, policyService)
    private val senderOrganizationId = UUID.randomUUID()
    private val initiator = user("sender@example.test")
    private val exchange = Exchange().apply {
        ownerOrganizationId = senderOrganizationId
        this.initiator = this@ExternalEmailAcceptancePolicyServiceTest.initiator
    }

    @Test
    fun `unresolved no-auth recipient is revalidated as an external customer`()
    {
        val invitedUser = user("customer@example.test", temporary = true, active = false)
        val share = userShare(invitedUser.id)
        whenever(appUserService.getById(invitedUser.id)).thenReturn(invitedUser)
        whenever(appUserService.findRegisteredByEmail("customer@example.test")).thenReturn(null)

        service.validate(exchange, share, authenticatedAppUserId = null)

        verify(policyService).assertCanShareWithUser(senderOrganizationId, initiator.id, null)
    }

    @Test
    fun `newly registered no-auth recipient is revalidated with current organization memberships`()
    {
        val invitedUser = user("member@example.test", temporary = true, active = false)
        val currentAccount = user("member@example.test")
        val share = userShare(invitedUser.id)
        whenever(appUserService.getById(invitedUser.id)).thenReturn(invitedUser)
        whenever(appUserService.findRegisteredByEmail("member@example.test")).thenReturn(currentAccount)

        service.validate(exchange, share, authenticatedAppUserId = null)

        verify(policyService).assertCanShareWithUser(senderOrganizationId, initiator.id, currentAccount.id)
    }

    @Test
    fun `authenticated recipient is revalidated against the exact current account`()
    {
        val currentAccount = user("member@example.test")
        val share = userShare(currentAccount.id)
        whenever(appUserService.getById(currentAccount.id)).thenReturn(currentAccount)
        whenever(appUserService.findRegisteredByEmail("member@example.test")).thenReturn(currentAccount)

        service.validate(exchange, share, authenticatedAppUserId = currentAccount.id)

        verify(policyService).assertCanShareWithUser(senderOrganizationId, initiator.id, currentAccount.id)
    }

    @Test
    fun `authenticated account substitution fails before policy evaluation`()
    {
        val invitedUser = user("member@example.test")
        val differentAccount = user("member@example.test")
        val share = userShare(invitedUser.id)
        whenever(appUserService.getById(invitedUser.id)).thenReturn(invitedUser)
        whenever(appUserService.findRegisteredByEmail("member@example.test")).thenReturn(differentAccount)

        assertThrows(ExchangeRecipientEligibilityException::class.java) {
            service.validate(exchange, share, authenticatedAppUserId = invitedUser.id)
        }

        verify(policyService, never()).assertCanShareWithUser(any(), any(), any())
    }

    @Test
    fun `inactive current account fails closed`()
    {
        val currentAccount = user("member@example.test", active = false)
        val share = userShare(currentAccount.id)
        whenever(appUserService.getById(currentAccount.id)).thenReturn(currentAccount)
        whenever(appUserService.findRegisteredByEmail("member@example.test")).thenReturn(currentAccount)

        assertThrows(ExchangeRecipientEligibilityException::class.java) {
            service.validate(exchange, share, authenticatedAppUserId = null)
        }

        verify(policyService, never()).assertCanShareWithUser(any(), any(), any())
    }

    @Test
    fun `deprovisioned current account fails closed`()
    {
        val currentAccount = user("member@example.test").apply {
            deprovisionedAt = Timestamp.from(Instant.now())
        }
        val share = userShare(currentAccount.id)
        whenever(appUserService.getById(currentAccount.id)).thenReturn(currentAccount)
        whenever(appUserService.findRegisteredByEmail("member@example.test")).thenReturn(currentAccount)

        assertThrows(ExchangeRecipientEligibilityException::class.java) {
            service.validate(exchange, share, authenticatedAppUserId = null)
        }

        verify(policyService, never()).assertCanShareWithUser(any(), any(), any())
    }

    @Test
    fun `stale B2B policy denial is exposed only as generic eligibility failure`()
    {
        val currentAccount = user("member@example.test")
        val share = userShare(currentAccount.id)
        whenever(appUserService.getById(currentAccount.id)).thenReturn(currentAccount)
        whenever(appUserService.findRegisteredByEmail("member@example.test")).thenReturn(currentAccount)
        whenever(policyService.assertCanShareWithUser(eq(senderOrganizationId), eq(initiator.id), eq(currentAccount.id)))
            .thenThrow(IllegalArgumentException("Directional receiver policy denied"))

        val exception = assertThrows(ExchangeRecipientEligibilityException::class.java) {
            service.validate(exchange, share, authenticatedAppUserId = null)
        }

        assertEquals("This Exchange can no longer be accepted", exception.message)
    }

    private fun user(
        email: String,
        temporary: Boolean = false,
        active: Boolean = true,
    ) = AppUser().apply {
        this.email = email
        isTemporary = temporary
        isActive = active
    }

    private fun userShare(appUserId: UUID) = Share().apply {
        principalKind = PrincipalKind.USER
        principalId = appUserId
    }
}
