package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.ApplicationRoleName
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoleCapabilitiesScopeTest
{
    @Test
    fun `same role label does not cross group and exchange scopes`()
    {
        val groupOwner = RoleCapabilities.forPrincipalGroupRole(PrincipalGroupRoleName.OWNER)
        val exchangeOwner = RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.OWNER)

        assertTrue(groupOwner.contains(Capability.GROUP_ADMIN))
        assertFalse(groupOwner.contains(Capability.EXCHANGE_ADMIN))
        assertTrue(exchangeOwner.contains(Capability.EXCHANGE_ADMIN))
        assertFalse(exchangeOwner.contains(Capability.GROUP_ADMIN))
    }

    @Test
    fun `application role grants no human capabilities`()
    {
        assertTrue(RoleCapabilities.forApplicationRole(ApplicationRoleName.APPLICATION).isEmpty())
    }
}
