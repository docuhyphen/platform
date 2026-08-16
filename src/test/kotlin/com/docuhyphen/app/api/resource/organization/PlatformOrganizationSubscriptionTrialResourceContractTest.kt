package com.docuhyphen.app.api.resource.organization

import jakarta.ws.rs.PATCH
import jakarta.ws.rs.POST
import jakarta.ws.rs.DELETE
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlatformOrganizationSubscriptionTrialResourceContractTest
{
    @Test
    fun `organization trial endpoints use resource based paths and verbs`()
    {
        val resource = PlatformOrganizationSubscriptionTrialResource::class.java
        assertEquals(
            "/platform/organizations/{organizationId}/subscription-trials",
            resource.getAnnotation(Path::class.java).value,
        )
        assertTrue(resource.declaredMethods.single { it.name == "start" }.isAnnotationPresent(POST::class.java))
        val extend = resource.declaredMethods.single { it.name == "extend" }
        assertTrue(extend.isAnnotationPresent(PATCH::class.java))
        assertEquals("/current", extend.getAnnotation(Path::class.java).value)
        val end = resource.declaredMethods.single { it.name == "end" }
        assertTrue(end.isAnnotationPresent(DELETE::class.java))
        assertEquals("/current", end.getAnnotation(Path::class.java).value)
        val convert = resource.declaredMethods.single { it.name == "convert" }
        assertTrue(convert.isAnnotationPresent(POST::class.java))
        assertEquals("/current/conversions", convert.getAnnotation(Path::class.java).value)
    }
}
