package com.docuhyphen.app.api.resource.subscription

import com.docuhyphen.app.api.model.SubscriptionTrialRequestDtoMapper
import com.docuhyphen.app.api.service.subscription.PlatformSubscriptionTrialRequestService
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.GET
import jakarta.ws.rs.PATCH
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PlatformSubscriptionTrialRequestResourceContractTest
{
    @Test
    fun `platform trial request endpoints use resource paths and verbs`()
    {
        val resource = PlatformSubscriptionTrialRequestResource::class.java
        assertEquals("/platform/subscription-trial-requests", resource.getAnnotation(Path::class.java).value)
        assertTrue(resource.declaredMethods.single { it.name == "list" }.isAnnotationPresent(GET::class.java))
        val decide = resource.declaredMethods.single { it.name == "decide" }
        assertTrue(decide.isAnnotationPresent(PATCH::class.java))
        assertEquals("/{requestId}/status", decide.getAnnotation(Path::class.java).value)
    }

    @Test
    fun `platform request list preserves authentication and permission statuses`()
    {
        val service = mock<PlatformSubscriptionTrialRequestService>()
        val resource = PlatformSubscriptionTrialRequestResource(service, mock<SubscriptionTrialRequestDtoMapper>())
        whenever(service.list(null, 25, 0)).thenThrow(
            UnauthorizedException("Authentication is required"),
            ForbiddenException("App Administrator is required"),
        )

        assertEquals(401, resource.list(null, null, null).status)
        assertEquals(403, resource.list(null, null, null).status)
    }
}
