package com.docuhyphen.app.api.resource.organization

import com.docuhyphen.app.api.resource.organization.operations.PlatformOrganizationSubscriptionPolicyCollectionResourceOperations
import com.docuhyphen.app.api.resource.organization.operations.PlatformOrganizationSubscriptionPolicyResourceOperations
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
            PlatformOrganizationSubscriptionPolicyCollectionResourceOperations::class.java
                .getAnnotation(Path::class.java).value,
        )
        assertTrue(
            PlatformOrganizationSubscriptionPolicyCollectionResourceOperations::class.java.declaredMethods
                .single { it.name == "list" }
                .isAnnotationPresent(GET::class.java),
        )
    }

    @Test
    fun `subscription policy item resource has no list action path`()
    {
        val operations = PlatformOrganizationSubscriptionPolicyResourceOperations::class.java

        assertEquals(
            "/platform/organizations/{organizationId}/subscription-policy",
            operations.getAnnotation(Path::class.java).value,
        )
        assertFalse(operations.declaredMethods.any { it.name == "listPolicies" })
        assertFalse(
            operations.declaredMethods
                .mapNotNull { it.getAnnotation(Path::class.java)?.value }
                .any { it == "/list" },
        )
        assertTrue(operations.declaredMethods.single { it.name == "getEffectivePolicy" }
            .isAnnotationPresent(GET::class.java))
        assertTrue(operations.declaredMethods.single { it.name == "upsertPolicy" }
            .isAnnotationPresent(PUT::class.java))
        assertFalse(operations.declaredMethods.any { it.name == "deletePolicy" })
    }
}
