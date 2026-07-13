package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.ApplicationRoleName
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RoleCapabilitiesScopeTest
{
    /**
     * The platform export-approval route (`AUDIT_EXPORT_APPROVE`) gates dual control for
     * platform-scope exports the same way `ORG_POLICY_MANAGE` gates organization exports. Without
     * this grant, no platform role could ever call `POST /platform/audit-exports/{id}/approvals`,
     * so a platform export requiring dual control could never leave APPROVAL_PENDING.
     */
    @Test
    fun `platform admin holds the capability required to approve platform audit exports under dual control`()
    {
        assertTrue(RoleCapabilities.forAppRole(AppRoleName.APP_ADMIN).contains(Capability.AUDIT_EXPORT_APPROVE))
    }

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
