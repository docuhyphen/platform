package com.docuhyphen.app.api.resource.user

import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlatformUserSubscriptionPolicyResourceContractTest
{
    @Test
    fun `user subscription administration uses resource based paths and verbs`()
    {
        val resource = PlatformUserSubscriptionPolicyResource::class.java
        assertEquals("/platform/users", resource.getAnnotation(Path::class.java).value)
        assertEquals(
            "/subscription-policies",
            resource.declaredMethods.single { it.name == "list" }.getAnnotation(Path::class.java).value,
        )
        assertTrue(resource.declaredMethods.single { it.name == "list" }.isAnnotationPresent(GET::class.java))
        assertTrue(resource.declaredMethods.single { it.name == "get" }.isAnnotationPresent(GET::class.java))
        assertTrue(resource.declaredMethods.single { it.name == "update" }.isAnnotationPresent(PATCH::class.java))
    }
}
