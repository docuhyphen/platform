package com.docuhyphen.app.api.resource.organization

import jakarta.ws.rs.DELETE
import jakarta.ws.rs.GET
import jakarta.ws.rs.PUT
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlatformOrganizationSubscriptionPolicyResourceContractTest
{
    @Test
    fun `subscription policy collection uses a resource based path`()
    {
        assertEquals(
            "/platform/organizations/subscription-policies",
            PlatformOrganizationSubscriptionPolicyCollectionResource::class.java
                .getAnnotation(Path::class.java).value,
        )
        assertTrue(
            PlatformOrganizationSubscriptionPolicyCollectionResource::class.java.declaredMethods
                .single { it.name == "list" }
                .isAnnotationPresent(GET::class.java),
        )
    }

    @Test
    fun `subscription policy item resource has no list action path`()
    {
        val resourceClass = PlatformOrganizationSubscriptionPolicyResource::class.java

        assertEquals(
            "/platform/organizations/{organizationId}/subscription-policy",
            resourceClass.getAnnotation(Path::class.java).value,
        )
        assertFalse(resourceClass.declaredMethods.any { it.name == "listPolicies" })
        assertFalse(
            resourceClass.declaredMethods
                .mapNotNull { it.getAnnotation(Path::class.java)?.value }
                .any { it == "/list" },
        )
        assertTrue(resourceClass.declaredMethods.single { it.name == "getEffectivePolicy" }
            .isAnnotationPresent(GET::class.java))
        assertTrue(resourceClass.declaredMethods.single { it.name == "upsertPolicy" }
            .isAnnotationPresent(PUT::class.java))
        assertTrue(resourceClass.declaredMethods.single { it.name == "deletePolicy" }
            .isAnnotationPresent(DELETE::class.java))
    }
}
