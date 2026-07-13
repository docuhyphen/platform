package com.docuhyphen.app.api.service.auth.authz

import com.docuhyphen.app.api.model.entity.AppRoleName
import com.docuhyphen.app.api.model.entity.ApplicationRoleName
import com.docuhyphen.app.api.model.entity.ExchangeShareRoleName
import com.docuhyphen.app.api.model.entity.OrganizationRoleName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AuditAuthorizationMatrixTest
{
    private val auditCapabilities = setOf(
        Capability.APP_AUDIT_READ,
        Capability.APP_AUDIT_EXPORT,
        Capability.ORG_AUDIT_READ,
        Capability.ORG_AUDIT_EXPORT,
        Capability.ORG_AUDIT_VIEW_SENSITIVE,
        Capability.AUDIT_EXPORT_APPROVE,
        Capability.AUDIT_RETENTION_MANAGE,
        Capability.AUDIT_LEGAL_HOLD_MANAGE,
        Capability.AUDIT_INTEGRITY_VERIFY,
        Capability.AUDIT_ENGAGEMENT_MANAGE,
    )

    @Test
    fun `human role audit authorization matrix is explicit and tenant scoped`()
    {
        val organizationGovernanceCapabilities = setOf(
            Capability.ORG_AUDIT_READ,
            Capability.ORG_AUDIT_EXPORT,
            Capability.ORG_AUDIT_VIEW_SENSITIVE,
            Capability.AUDIT_EXPORT_APPROVE,
            Capability.AUDIT_RETENTION_MANAGE,
            Capability.AUDIT_LEGAL_HOLD_MANAGE,
            Capability.AUDIT_INTEGRITY_VERIFY,
            Capability.AUDIT_ENGAGEMENT_MANAGE,
        )
        val expected = mapOf(
            "APP_ADMIN" to setOf(
                Capability.APP_AUDIT_READ,
                Capability.APP_AUDIT_EXPORT,
                Capability.AUDIT_EXPORT_APPROVE,
                Capability.AUDIT_RETENTION_MANAGE,
                Capability.AUDIT_LEGAL_HOLD_MANAGE,
                Capability.AUDIT_INTEGRITY_VERIFY,
                Capability.AUDIT_ENGAGEMENT_MANAGE,
            ),
            "APP_AUDITOR" to setOf(
                Capability.APP_AUDIT_READ,
                Capability.APP_AUDIT_EXPORT,
                Capability.ORG_AUDIT_READ,
            ),
            "ORG_OWNER" to organizationGovernanceCapabilities,
            "ORG_ADMIN" to organizationGovernanceCapabilities,
            "ORG_AUDITOR" to setOf(
                Capability.ORG_AUDIT_READ,
                Capability.ORG_AUDIT_EXPORT,
            ),
            "ORG_MEMBER" to emptySet(),
        )
        val actual = mapOf(
            "APP_ADMIN" to auditCapabilitiesFor(RoleCapabilities.forAppRole(AppRoleName.APP_ADMIN)),
            "APP_AUDITOR" to auditCapabilitiesFor(RoleCapabilities.forAppRole(AppRoleName.APP_AUDITOR)),
            "ORG_OWNER" to auditCapabilitiesFor(RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_OWNER)),
            "ORG_ADMIN" to auditCapabilitiesFor(RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_ADMIN)),
            "ORG_AUDITOR" to auditCapabilitiesFor(RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_AUDITOR)),
            "ORG_MEMBER" to auditCapabilitiesFor(RoleCapabilities.forOrganizationRole(OrganizationRoleName.ORG_MEMBER)),
        )

        assertEquals(expected, actual)
    }

    @Test
    fun `Exchange participants public links and applications receive no audit capability by default`()
    {
        ExchangeShareRoleName.entries.forEach { role ->
            assertEquals(
                emptySet<Capability>(),
                auditCapabilitiesFor(RoleCapabilities.forExchangeShareRole(role)),
                "Exchange Share role $role must not authorize canonical audit evidence",
            )
        }
        assertEquals(
            emptySet<Capability>(),
            auditCapabilitiesFor(RoleCapabilities.forApplicationRole(ApplicationRoleName.APPLICATION)),
            "Application principals must receive audit access only through an explicit capability grant",
        )
    }

    private fun auditCapabilitiesFor(capabilities: Set<Capability>): Set<Capability> =
        capabilities.intersect(auditCapabilities)
}
