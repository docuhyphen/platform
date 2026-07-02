package com.docuhyphen.app.api.model.entity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class RoleFamiliesTest
{
    @Test
    fun `role families expose only their own values`()
    {
        assertEquals(
            setOf("APP_ADMIN", "APP_AUDITOR", "APP_SUPPORT", "APP_USER"),
            AppRoleName.entries.map { it.name }.toSet(),
        )
        assertEquals(
            setOf(
                "ORG_OWNER",
                "ORG_ADMIN",
                "ORG_BILLING_ADMIN",
                "ORG_USER_MANAGER",
                "ORG_AUDITOR",
                "ORG_MEMBER",
                "ORG_GUEST",
            ),
            OrganizationRoleName.entries.map { it.name }.toSet(),
        )
        assertEquals(setOf("OWNER", "MANAGER", "MEMBER", "OBSERVER"), PrincipalGroupRoleName.entries.map { it.name }.toSet())
        assertEquals(
            setOf("OWNER", "EDITOR", "REVIEWER", "SIGNER", "VIEWER", "COMMENTER", "PARTICIPANT"),
            ExchangeShareRoleName.entries.map { it.name }.toSet(),
        )
        assertEquals(setOf("APPLICATION"), ApplicationRoleName.entries.map { it.name }.toSet())
    }

    @Test
    fun `wrong scope role names cannot be parsed`()
    {
        assertThrows(IllegalArgumentException::class.java) { OrganizationRoleName.valueOf("APP_ADMIN") }
        assertThrows(IllegalArgumentException::class.java) { AppRoleName.valueOf("ORG_ADMIN") }
        assertThrows(IllegalArgumentException::class.java) { PrincipalGroupRoleName.valueOf("EDITOR") }
        assertThrows(IllegalArgumentException::class.java) { ExchangeShareRoleName.valueOf("MANAGER") }
        assertThrows(IllegalArgumentException::class.java) { ApplicationRoleName.valueOf("APP_USER") }
    }
}
