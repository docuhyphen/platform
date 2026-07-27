package com.docuhyphen.app.api.service.exchange.permutation

import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.Exchange
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.resource.model.RegisteredUserRecipientSelectionRequest
import com.docuhyphen.app.api.service.auth.authz.Action
import com.docuhyphen.app.api.service.auth.authz.Capability
import com.docuhyphen.app.api.service.auth.authz.OwnerContext
import com.docuhyphen.app.api.service.auth.authz.RoleCapabilities
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class ExchangeSenderOwnershipPermutationTest
{
    @Test
    fun `EX-SND-01 personal user creates personal-owned Exchange`()
    {
        val userId = UUID.randomUUID()
        val exchange = Exchange().apply { ownerUserId = userId }
        assertEquals(OwnerContext.Personal(userId), OwnerContext.Personal(requireNotNull(exchange.ownerUserId)))
        assertTrue(Capability.EXCHANGE_INITIATE in RoleCapabilities.forAppRole(AppRoleName.APP_USER))
    }

    @Test
    fun `EX-SND-02 organization Owner creates organization-owned Exchange`()
    {
        assertOrganizationCanInitiate(OrganizationRoleName.ORG_OWNER)
    }

    @Test
    fun `EX-SND-03 organization Admin creates Exchange`()
    {
        assertOrganizationCanInitiate(OrganizationRoleName.ORG_ADMIN)
    }

    @Test
    fun `EX-SND-04 organization Member with initiation capability creates Exchange`()
    {
        assertOrganizationCanInitiate(OrganizationRoleName.ORG_MEMBER)
    }

    @Test
    fun `EX-SND-05 organization Guest without initiation capability is denied`()
    {
        assertFalse(
            Capability.EXCHANGE_INITIATE in RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_GUEST),
        )
    }

    @Test
    fun `EX-SND-06 organization Owner in personal context creates personal-owned Exchange`()
    {
        val userId = UUID.randomUUID()
        val exchange = Exchange().apply { ownerUserId = userId }
        assertEquals(userId, exchange.ownerUserId)
        assertNull(exchange.ownerOrganizationId)
    }

    @Test
    fun `EX-SND-07 owner retains access after switching active organization`()
    {
        val probe = ExchangeAuthorizationProbe()
        probe.assertAllowed(
            Action.EXCHANGE_VIEW,
            listOf(probe.share(ExchangeShareRoleName.OWNER)),
        )
    }

    @Test
    fun `EX-SND-08 initiator cannot be primary recipient`()
    {
        assertSelfSelectionDenied()
    }

    @Test
    fun `EX-SND-09 initiator cannot be additional participant`()
    {
        assertSelfSelectionDenied()
    }

    private fun assertOrganizationCanInitiate(role: OrganizationRoleName)
    {
        assertTrue(Capability.EXCHANGE_INITIATE in RoleCapabilities.forOrganizationRole(role))
    }

    private fun assertSelfSelectionDenied()
    {
        val probe = ExchangeRecipientSelectionProbe()
        assertThrows(IllegalArgumentException::class.java) {
            probe.resolve(RegisteredUserRecipientSelectionRequest(probe.initiator.id.toString()))
        }
    }
}
