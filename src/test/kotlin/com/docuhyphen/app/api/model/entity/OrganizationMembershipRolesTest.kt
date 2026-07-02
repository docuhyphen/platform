package com.docuhyphen.app.api.model.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OrganizationMembershipRolesTest
{
    @Test
    fun `adding and removing a role preserves unrelated assignments`()
    {
        val membership = OrganizationMembership().apply {
            roles = mutableSetOf(OrganizationRoleName.ORG_MEMBER, OrganizationRoleName.ORG_BILLING_ADMIN)
        }

        membership.roles.add(OrganizationRoleName.ORG_ADMIN)
        membership.roles.remove(OrganizationRoleName.ORG_BILLING_ADMIN)

        assertEquals(
            setOf(OrganizationRoleName.ORG_MEMBER, OrganizationRoleName.ORG_ADMIN),
            membership.roles,
        )
    }
}
