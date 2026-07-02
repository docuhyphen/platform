package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.ApplicationRoleName
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import com.docuhyphen.app.api.model.entity.PrincipalGroupRoleName
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Action-to-capability matrix and role-to-capability correctness.
 *
 * Rules verified:
 *  - Every Action maps to exactly one Capability (compile-time enforced by the enum constructor).
 *  - A Capability not listed in any role's set grants nothing by default.
 *  - EXCHANGE_RESCIND belongs to the OWNER Share role only.
 *  - EXCHANGE_INITIATE belongs to APP_USER and ORG_MEMBER but NOT to the APPLICATION machine role.
 *  - The APPLICATION role still maps to emptySet() (grants come from grantedCapabilitiesJson only).
 *  - ORG_MEMBER cannot write or administer org-owned resources.
 *  - GROUP_EDIT belongs to group OWNER and MANAGER but not MEMBER or OBSERVER.
 *  - APP_ADMIN has APP_REG_ADMIN; APP_AUDITOR does not.
 *  - Multi-role: ORG_BILLING_ADMIN + ORG_MEMBER compose correctly.
 *  - EXCHANGE_OWNER Share role does not cross into GROUP capabilities.
 *  - VARIABLE_DISCOVER does not imply VARIABLE_READ.
 *  - Use does not imply Edit for any resource family.
 *  - Adding a synthetic Capability that is not listed in any role grants it to no role.
 */
class ActionCapabilityModelTest
{
    // --- default-deny extension point ---

    @Test
    fun `new capability that is not mapped to any role grants nothing`()
    {
        // Every Capability must be explicitly listed in a role's set in RoleCapabilities.
        // Enumerate all capabilities assigned across all roles and confirm the remainder
        // grants to no principal when the capability is absent.
        val allAssigned = collectAllAssignedCapabilities()

        // WEBHOOK_DELIVER is an internal system action not granted to any human or machine
        // role in Phase 5 — it proves the default-deny extension point.
        assertFalse(allAssigned.contains(Capability.WEBHOOK_DELIVER)) {
            "WEBHOOK_DELIVER must not be granted to any role; it is a reserved system-only action"
        }
    }

    // --- EXCHANGE_RESCIND ---

    @Test
    fun `exchange OWNER Share role has EXCHANGE_RESCIND`()
    {
        val caps = RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.OWNER)
        assertTrue(caps.contains(Capability.EXCHANGE_RESCIND))
    }

    @Test
    fun `exchange EDITOR Share role does not have EXCHANGE_RESCIND`()
    {
        assertFalse(RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.EDITOR).contains(Capability.EXCHANGE_RESCIND))
    }

    @Test
    fun `exchange REVIEWER Share role does not have EXCHANGE_RESCIND`()
    {
        assertFalse(RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.REVIEWER).contains(Capability.EXCHANGE_RESCIND))
    }

    @Test
    fun `exchange PARTICIPANT Share role does not have EXCHANGE_RESCIND`()
    {
        assertFalse(RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.PARTICIPANT).contains(Capability.EXCHANGE_RESCIND))
    }

    // --- EXCHANGE_INITIATE ---

    @Test
    fun `APP_USER has EXCHANGE_INITIATE`()
    {
        assertTrue(RoleCapabilities.forAppRole(AppRoleName.APP_USER).contains(Capability.EXCHANGE_INITIATE))
    }

    @Test
    fun `ORG_MEMBER has EXCHANGE_INITIATE`()
    {
        assertTrue(RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_MEMBER).contains(Capability.EXCHANGE_INITIATE))
    }

    @Test
    fun `APPLICATION machine role does not have EXCHANGE_INITIATE by default`()
    {
        assertTrue(RoleCapabilities.forApplicationRole(ApplicationRoleName.APPLICATION).isEmpty()) {
            "APPLICATION role must map to emptySet(); EXCHANGE_INITIATE arrives only via grantedCapabilitiesJson"
        }
    }

    @Test
    fun `ORG_GUEST does not have EXCHANGE_INITIATE`()
    {
        assertFalse(RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_GUEST).contains(Capability.EXCHANGE_INITIATE))
    }

    // --- ORG_MEMBER write restriction ---

    @Test
    fun `ORG_MEMBER cannot write Document Library entries`()
    {
        val caps = RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_MEMBER)
        assertFalse(caps.contains(Capability.DOC_LIBRARY_WRITE))
        assertFalse(caps.contains(Capability.DOC_LIBRARY_ADMIN))
        assertFalse(caps.contains(Capability.DOC_LIBRARY_DELETE))
    }

    @Test
    fun `ORG_MEMBER cannot write Blueprints`()
    {
        val caps = RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_MEMBER)
        assertFalse(caps.contains(Capability.BLUEPRINT_WRITE))
        assertFalse(caps.contains(Capability.BLUEPRINT_ADMIN))
        assertFalse(caps.contains(Capability.BLUEPRINT_DELETE))
    }

    @Test
    fun `ORG_MEMBER cannot write Workflow Definitions`()
    {
        val caps = RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_MEMBER)
        assertFalse(caps.contains(Capability.WORKFLOW_WRITE))
        assertFalse(caps.contains(Capability.WORKFLOW_ADMIN))
        assertFalse(caps.contains(Capability.WORKFLOW_DELETE))
    }

    @Test
    fun `ORG_MEMBER can discover and use org resources`()
    {
        val caps = RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_MEMBER)
        assertTrue(caps.contains(Capability.DOC_LIBRARY_DISCOVER))
        assertTrue(caps.contains(Capability.DOC_LIBRARY_USE))
        assertTrue(caps.contains(Capability.BLUEPRINT_DISCOVER))
        assertTrue(caps.contains(Capability.BLUEPRINT_USE))
        assertTrue(caps.contains(Capability.WORKFLOW_DISCOVER))
        assertTrue(caps.contains(Capability.WORKFLOW_USE))
        assertTrue(caps.contains(Capability.SEQUENCE_CONSUME))
        assertTrue(caps.contains(Capability.VARIABLE_USE))
        assertTrue(caps.contains(Capability.COMMUNICATION_USE))
    }

    // --- GROUP_EDIT scope ---

    @Test
    fun `group OWNER has GROUP_EDIT`()
    {
        assertTrue(RoleCapabilities.forPrincipalGroupRole(PrincipalGroupRoleName.OWNER).contains(Capability.GROUP_EDIT))
    }

    @Test
    fun `group MANAGER has GROUP_EDIT`()
    {
        assertTrue(RoleCapabilities.forPrincipalGroupRole(PrincipalGroupRoleName.MANAGER).contains(Capability.GROUP_EDIT))
    }

    @Test
    fun `group MEMBER does not have GROUP_EDIT`()
    {
        assertFalse(RoleCapabilities.forPrincipalGroupRole(PrincipalGroupRoleName.MEMBER).contains(Capability.GROUP_EDIT))
    }

    @Test
    fun `group OBSERVER does not have GROUP_EDIT`()
    {
        assertFalse(RoleCapabilities.forPrincipalGroupRole(PrincipalGroupRoleName.OBSERVER).contains(Capability.GROUP_EDIT))
    }

    // --- APP_ADMIN and APP_AUDITOR registration access ---

    @Test
    fun `APP_ADMIN has APP_REG_ADMIN and APP_REG_READ`()
    {
        val caps = RoleCapabilities.forAppRole(AppRoleName.APP_ADMIN)
        assertTrue(caps.contains(Capability.APP_REG_ADMIN))
        assertTrue(caps.contains(Capability.APP_REG_READ))
    }

    @Test
    fun `APP_AUDITOR has APP_REG_READ but not APP_REG_ADMIN`()
    {
        val caps = RoleCapabilities.forAppRole(AppRoleName.APP_AUDITOR)
        assertTrue(caps.contains(Capability.APP_REG_READ))
        assertFalse(caps.contains(Capability.APP_REG_ADMIN))
    }

    @Test
    fun `APP_SUPPORT does not have APP_REG_READ or APP_REG_ADMIN`()
    {
        val caps = RoleCapabilities.forAppRole(AppRoleName.APP_SUPPORT)
        assertFalse(caps.contains(Capability.APP_REG_READ))
        assertFalse(caps.contains(Capability.APP_REG_ADMIN))
    }

    // --- multi-role composition (Ethan scenario) ---

    @Test
    fun `ORG_BILLING_ADMIN and ORG_MEMBER compose to include both billing and member capabilities`()
    {
        val billingCaps = RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_BILLING_ADMIN)
        val memberCaps = RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_MEMBER)
        val effective = billingCaps + memberCaps

        assertTrue(effective.contains(Capability.ORG_BILLING_MANAGE)) { "billing role must supply billing manage" }
        assertTrue(effective.contains(Capability.GROUP_READ)) { "member role must supply group read" }
        assertTrue(effective.contains(Capability.EXCHANGE_INITIATE)) { "member role must supply exchange initiate" }
        assertFalse(effective.contains(Capability.ORG_MEMBER_MANAGE)) { "neither role must supply member manage" }
    }

    @Test
    fun `removing ORG_BILLING_ADMIN leaves ORG_MEMBER capabilities intact`()
    {
        val memberCaps = RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_MEMBER)
        assertTrue(memberCaps.contains(Capability.EXCHANGE_INITIATE))
        assertTrue(memberCaps.contains(Capability.GROUP_READ))
        assertFalse(memberCaps.contains(Capability.ORG_BILLING_MANAGE))
    }

    // --- scope isolation ---

    @Test
    fun `exchange OWNER Share role does not carry group capabilities`()
    {
        val caps = RoleCapabilities.forExchangeShareRole(ExchangeShareRoleName.OWNER)
        assertFalse(caps.contains(Capability.GROUP_ADMIN))
        assertFalse(caps.contains(Capability.GROUP_EDIT))
        assertFalse(caps.contains(Capability.GROUP_DELETE))
    }

    @Test
    fun `group OWNER role does not carry exchange capabilities`()
    {
        val caps = RoleCapabilities.forPrincipalGroupRole(PrincipalGroupRoleName.OWNER)
        assertFalse(caps.contains(Capability.EXCHANGE_READ))
        assertFalse(caps.contains(Capability.EXCHANGE_ADMIN))
        assertFalse(caps.contains(Capability.EXCHANGE_RESCIND))
    }

    // --- discover vs view vs value ---

    @Test
    fun `VARIABLE_DISCOVER does not imply VARIABLE_READ`()
    {
        // ORG_MEMBER has VARIABLE_DISCOVER but not VARIABLE_READ (value access is restricted)
        val memberCaps = RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_MEMBER)
        assertTrue(memberCaps.contains(Capability.VARIABLE_DISCOVER))
        assertFalse(memberCaps.contains(Capability.VARIABLE_READ))
    }

    @Test
    fun `BLUEPRINT_USE does not imply BLUEPRINT_WRITE`()
    {
        val memberCaps = RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_MEMBER)
        assertTrue(memberCaps.contains(Capability.BLUEPRINT_USE))
        assertFalse(memberCaps.contains(Capability.BLUEPRINT_WRITE))
    }

    // --- ORG_OWNER vs ORG_ADMIN billing distinction ---

    @Test
    fun `ORG_OWNER has ORG_BILLING_MANAGE but ORG_ADMIN does not`()
    {
        assertTrue(RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_OWNER).contains(Capability.ORG_BILLING_MANAGE))
        assertFalse(RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_ADMIN).contains(Capability.ORG_BILLING_MANAGE))
    }

    // --- ORG_ADMIN gets webhook admin ---

    @Test
    fun `ORG_ADMIN has WEBHOOK_ADMIN`()
    {
        assertTrue(RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_ADMIN).contains(Capability.WEBHOOK_ADMIN))
    }

    @Test
    fun `ORG_MEMBER does not have WEBHOOK_ADMIN`()
    {
        assertFalse(RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_MEMBER).contains(Capability.WEBHOOK_ADMIN))
    }

    // -------------------------------------------------------------------------
    // helper
    // -------------------------------------------------------------------------

    private fun collectAllAssignedCapabilities(): Set<Capability>
    {
        val assigned = mutableSetOf<Capability>()
        AppRoleName.entries.forEach { assigned += RoleCapabilities.forAppRole(it) }
        ApplicationRoleName.entries.forEach { assigned += RoleCapabilities.forApplicationRole(it) }
        OrganizationRoleName.entries.forEach { assigned += RoleCapabilities.forOrganizationRole(it) }
        PrincipalGroupRoleName.entries.forEach { assigned += RoleCapabilities.forPrincipalGroupRole(it) }
        ExchangeShareRoleName.entries.forEach { assigned += RoleCapabilities.forExchangeShareRole(it) }
        return assigned
    }
}
