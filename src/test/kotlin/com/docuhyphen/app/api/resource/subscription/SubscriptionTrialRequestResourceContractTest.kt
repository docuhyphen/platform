package com.docuhyphen.app.api.resource.subscription

import com.docuhyphen.app.api.model.SubscriptionTrialRequestDtoMapper
import com.docuhyphen.app.api.model.dto.SubscriptionTrialRequestCreateRequest
import com.docuhyphen.app.api.service.auth.SelfServiceSubscriptionTrialRequestService
import com.docuhyphen.app.api.service.subscription.SubscriptionTrialRequestConflictException
import io.quarkus.security.ForbiddenException
import io.quarkus.security.UnauthorizedException
import jakarta.ws.rs.GET
import jakarta.ws.rs.POST
import jakarta.ws.rs.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SubscriptionTrialRequestResourceContractTest
{
    @Test
    fun `self service trial request endpoints use resource paths and verbs`()
    {
        val resource = SubscriptionTrialRequestResource::class.java
        assertEquals("/subscription-trial-requests", resource.getAnnotation(Path::class.java).value)
        assertTrue(resource.declaredMethods.single { it.name == "create" }.isAnnotationPresent(POST::class.java))
        val current = resource.declaredMethods.single { it.name == "current" }
        assertTrue(current.isAnnotationPresent(GET::class.java))
        assertEquals("/current", current.getAnnotation(Path::class.java).value)
    }

    @Test
    fun `missing authentication returns unauthorized`()
    {
        val service = mock<SelfServiceSubscriptionTrialRequestService>()
        whenever(service.current()).thenThrow(UnauthorizedException("Authentication is required"))
        val resource = SubscriptionTrialRequestResource(service, mock<SubscriptionTrialRequestDtoMapper>())

        assertEquals(401, resource.current().status)
    }

    @Test
    fun `insufficient permission returns forbidden`()
    {
        val service = mock<SelfServiceSubscriptionTrialRequestService>()
        whenever(service.current()).thenThrow(ForbiddenException("Permission is required"))
        val resource = SubscriptionTrialRequestResource(service, mock<SubscriptionTrialRequestDtoMapper>())

        assertEquals(403, resource.current().status)
    }

    @Test
    fun `a duplicate pending request returns conflict`()
    {
        val service = mock<SelfServiceSubscriptionTrialRequestService>()
        whenever(service.create(any())).thenThrow(
            SubscriptionTrialRequestConflictException("A trial request is already pending"),
        )
        val resource = SubscriptionTrialRequestResource(service, mock<SubscriptionTrialRequestDtoMapper>())

        assertEquals(409, resource.create(SubscriptionTrialRequestCreateRequest()).status)
    }
}
